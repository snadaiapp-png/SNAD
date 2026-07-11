package com.sanad.platform.admin.service;

import com.sanad.platform.admin.api.SaasAdminDtos.CancelSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSeatsRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSubscriptionPlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreatePlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.EntitlementRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.EntitlementResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.InvoiceResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.MarkInvoicePaidRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.PlanResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionEventResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdatePlanRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SaaS administration engine for plans, entitlements, subscriptions, upgrades, and invoices. */
@Service
public class SaasAdministrationService {

    private static final Set<String> PLAN_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");
    private static final Set<String> SUBSCRIPTION_STATUSES = Set.of(
            "TRIALING", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED");
    private static final Set<String> BILLING_CYCLES = Set.of("MONTHLY", "ANNUAL");
    private static final DateTimeFormatter INVOICE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;

    public SaasAdministrationService(JdbcTemplate jdbcTemplate, PlatformAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return jdbcTemplate.query(
                "SELECT id, code, name, description, status, currency_code, monthly_price_minor, "
                        + "annual_price_minor, trial_days, max_users, max_organizations, storage_mb, created_at, updated_at "
                        + "FROM saas_plans ORDER BY monthly_price_minor, code",
                this::mapPlanCore
        ).stream().map(this::toPlanResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(UUID planId) {
        List<PlanCore> rows = jdbcTemplate.query(
                "SELECT id, code, name, description, status, currency_code, monthly_price_minor, "
                        + "annual_price_minor, trial_days, max_users, max_organizations, storage_mb, created_at, updated_at "
                        + "FROM saas_plans WHERE id = ?",
                this::mapPlanCore,
                planId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
        }
        return toPlanResponse(rows.get(0));
    }

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request, Authentication authentication) {
        String code = normalizeCode(request.code());
        if (count("SELECT COUNT(*) FROM saas_plans WHERE code = ?", code) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan code already exists");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO saas_plans "
                        + "(id, code, name, description, status, currency_code, monthly_price_minor, annual_price_minor, "
                        + "trial_days, max_users, max_organizations, storage_mb, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, request.name().trim(), blankToNull(request.description()),
                normalizeCurrency(request.currencyCode()), request.monthlyPriceMinor(), request.annualPriceMinor(),
                request.trialDays(), request.maxUsers(), request.maxOrganizations(), request.storageMb(), Timestamp.from(now), Timestamp.from(now));
        replaceEntitlements(id, request.entitlements());
        PlanResponse created = getPlan(id);
        auditService.success(authentication, null, "PLAN.CREATE", "SAAS_PLAN", id.toString(),
                "Created from control plane", null, created);
        return created;
    }

    @Transactional
    public PlanResponse updatePlan(UUID planId, UpdatePlanRequest request, Authentication authentication) {
        PlanResponse before = getPlan(planId);
        long highestSeatUsage = scalarLong(
                "SELECT COALESCE(MAX(seat_quantity), 0) FROM tenant_subscriptions "
                        + "WHERE plan_id = ? AND status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED')",
                planId);
        long highestOrganizationUsage = scalarLong(
                "SELECT COALESCE(MAX(organization_count), 0) FROM ("
                        + "SELECT s.tenant_id, COUNT(o.id) AS organization_count FROM tenant_subscriptions s "
                        + "LEFT JOIN organizations o ON o.tenant_id = s.tenant_id AND o.status <> 'ARCHIVED' "
                        + "WHERE s.plan_id = ? AND s.status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED') "
                        + "GROUP BY s.tenant_id) usage",
                planId);
        if (request.maxUsers() < highestSeatUsage || request.maxOrganizations() < highestOrganizationUsage) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Plan limits cannot be reduced below current subscriber usage");
        }

        jdbcTemplate.update(
                "UPDATE saas_plans SET name = ?, description = ?, currency_code = ?, monthly_price_minor = ?, "
                        + "annual_price_minor = ?, trial_days = ?, max_users = ?, max_organizations = ?, storage_mb = ?, "
                        + "updated_at = ? WHERE id = ?",
                request.name().trim(), blankToNull(request.description()), normalizeCurrency(request.currencyCode()),
                request.monthlyPriceMinor(), request.annualPriceMinor(), request.trialDays(), request.maxUsers(),
                request.maxOrganizations(), request.storageMb(), Timestamp.from(Instant.now()), planId);
        replaceEntitlements(planId, request.entitlements());
        PlanResponse after = getPlan(planId);
        auditService.success(authentication, null, "PLAN.UPDATE", "SAAS_PLAN", planId.toString(),
                "Updated from control plane", before, after);
        return after;
    }

    @Transactional
    public PlanResponse changePlanStatus(
            UUID planId,
            String requestedStatus,
            String reason,
            Authentication authentication
    ) {
        PlanResponse before = getPlan(planId);
        String status = normalize(requestedStatus);
        if (!PLAN_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported plan status");
        }
        jdbcTemplate.update("UPDATE saas_plans SET status = ?, updated_at = ? WHERE id = ?",
                status, Timestamp.from(Instant.now()), planId);
        PlanResponse after = getPlan(planId);
        auditService.success(authentication, null, "PLAN.STATUS.CHANGE", "SAAS_PLAN", planId.toString(),
                reason, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(UUID tenantId) {
        String base = subscriptionSelect();
        if (tenantId == null) {
            return jdbcTemplate.query(base + " ORDER BY s.created_at DESC", this::mapSubscription);
        }
        return jdbcTemplate.query(base + " WHERE s.tenant_id = ? ORDER BY s.created_at DESC",
                this::mapSubscription, tenantId);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID subscriptionId) {
        List<SubscriptionResponse> rows = jdbcTemplate.query(
                subscriptionSelect() + " WHERE s.id = ?",
                this::mapSubscription,
                subscriptionId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<EntitlementResponse> subscriptionEntitlements(UUID subscriptionId) {
        SubscriptionResponse subscription = getSubscription(subscriptionId);
        return jdbcTemplate.query(
                "SELECT e.id, e.feature_code, e.enabled, e.limit_value FROM saas_plan_entitlements e "
                        + "WHERE e.plan_id = ? ORDER BY e.feature_code",
                this::mapEntitlement,
                subscription.planId());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEventResponse> subscriptionEvents(UUID subscriptionId) {
        getSubscription(subscriptionId);
        return jdbcTemplate.query(
                "SELECT id, subscription_id, action, old_plan_id, new_plan_id, effective_mode, adjustment_minor, "
                        + "reason, effective_at, created_at FROM subscription_change_events "
                        + "WHERE subscription_id = ? ORDER BY created_at DESC",
                this::mapSubscriptionEvent,
                subscriptionId);
    }

    @Transactional
    public SubscriptionResponse createSubscription(
            CreateSubscriptionRequest request,
            Authentication authentication
    ) {
        ensureTenant(request.tenantId());
        if (count("SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", request.tenantId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant already has a subscription");
        }
        PlanResponse plan = activePlan(request.planId());
        String billingCycle = normalizeCycle(request.billingCycle());
        validateUsageAgainstPlan(request.tenantId(), request.seatQuantity(), plan);

        int trialDays = request.trialDays() == null ? plan.trialDays() : request.trialDays();
        Instant now = Instant.now();
        Instant trialEndsAt = trialDays > 0 ? now.plus(Duration.ofDays(trialDays)) : null;
        Instant periodEnd = trialEndsAt != null ? trialEndsAt : nextPeriod(now, billingCycle);
        String status = trialEndsAt == null ? "ACTIVE" : "TRIALING";
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_subscriptions "
                        + "(id, tenant_id, plan_id, pending_plan_id, status, billing_cycle, pending_billing_cycle, "
                        + "seat_quantity, credit_balance_minor, started_at, trial_ends_at, current_period_start, "
                        + "current_period_end, cancel_at_period_end, cancelled_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, NULL, ?, 0, ?, ?, ?, ?, FALSE, NULL, ?, ?)",
                id, request.tenantId(), request.planId(), status, billingCycle, request.seatQuantity(),
                Timestamp.from(now), Timestamp.from(trialEndsAt), Timestamp.from(now), Timestamp.from(periodEnd), Timestamp.from(now), Timestamp.from(now));
        recordEvent(id, request.tenantId(), "SUBSCRIPTION.CREATED", null, request.planId(), "IMMEDIATE", 0,
                "Subscription created", now, authentication);
        SubscriptionResponse created = getSubscription(id);
        if (trialEndsAt == null) {
            issueRecurringInvoice(created, plan, "Initial subscription period");
        }
        auditService.success(authentication, request.tenantId(), "SUBSCRIPTION.CREATE", "TENANT_SUBSCRIPTION",
                id.toString(), "Created from control plane", null, created);
        return getSubscription(id);
    }

    @Transactional
    public SubscriptionResponse changePlan(
            UUID subscriptionId,
            ChangeSubscriptionPlanRequest request,
            Authentication authentication
    ) {
        SubscriptionResponse before = getSubscription(subscriptionId);
        ensureMutableSubscription(before);
        PlanResponse targetPlan = activePlan(request.planId());
        validateUsageAgainstPlan(before.tenantId(), before.seatQuantity(), targetPlan);
        String targetCycle = normalizeCycle(request.billingCycle());
        String mode = normalize(request.effectiveMode());
        if (!Set.of("IMMEDIATE", "NEXT_CYCLE").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported effective mode");
        }
        if (mode.equals("NEXT_CYCLE")) {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET pending_plan_id = ?, pending_billing_cycle = ?, updated_at = ? WHERE id = ?",
                    targetPlan.id(), targetCycle, Timestamp.from(Instant.now()), subscriptionId);
            recordEvent(subscriptionId, before.tenantId(), "PLAN.CHANGE.SCHEDULED", before.planId(), targetPlan.id(),
                    mode, 0, request.reason(), before.currentPeriodEnd(), authentication);
        } else {
            PlanResponse oldPlan = getPlan(before.planId());
            long adjustment = proratedAdjustment(before, oldPlan, targetPlan, targetCycle);
            if (adjustment < 0) {
                jdbcTemplate.update(
                        "UPDATE tenant_subscriptions SET credit_balance_minor = credit_balance_minor + ?, "
                                + "plan_id = ?, billing_cycle = ?, pending_plan_id = NULL, pending_billing_cycle = NULL, updated_at = ? "
                                + "WHERE id = ?",
                        Math.abs(adjustment), targetPlan.id(), targetCycle, Timestamp.from(Instant.now()), subscriptionId);
            } else {
                jdbcTemplate.update(
                        "UPDATE tenant_subscriptions SET plan_id = ?, billing_cycle = ?, pending_plan_id = NULL, "
                                + "pending_billing_cycle = NULL, updated_at = ? WHERE id = ?",
                        targetPlan.id(), targetCycle, Timestamp.from(Instant.now()), subscriptionId);
            }
            if (adjustment > 0) {
                issueInvoice(getSubscription(subscriptionId), adjustment,
                        "Prorated immediate plan upgrade", before.currentPeriodStart(), before.currentPeriodEnd());
            }
            recordEvent(subscriptionId, before.tenantId(), "PLAN.CHANGE.APPLIED", before.planId(), targetPlan.id(),
                    mode, adjustment, request.reason(), Instant.now(), authentication);
        }
        SubscriptionResponse after = getSubscription(subscriptionId);
        auditService.success(authentication, before.tenantId(), "SUBSCRIPTION.PLAN.CHANGE", "TENANT_SUBSCRIPTION",
                subscriptionId.toString(), request.reason(), before, after);
        return after;
    }

    @Transactional
    public SubscriptionResponse changeSeats(
            UUID subscriptionId,
            ChangeSeatsRequest request,
            Authentication authentication
    ) {
        SubscriptionResponse before = getSubscription(subscriptionId);
        ensureMutableSubscription(before);
        PlanResponse plan = getPlan(before.planId());
        validateUsageAgainstPlan(before.tenantId(), request.seatQuantity(), plan);
        int oldSeats = before.seatQuantity();
        int newSeats = request.seatQuantity();
        long adjustment = 0;
        if (oldSeats != newSeats) {
            long unitPrice = price(plan, before.billingCycle());
            adjustment = prorate((long) (newSeats - oldSeats) * unitPrice,
                    before.currentPeriodStart(), before.currentPeriodEnd());
            if (adjustment < 0) {
                jdbcTemplate.update(
                        "UPDATE tenant_subscriptions SET seat_quantity = ?, credit_balance_minor = credit_balance_minor + ?, updated_at = ? WHERE id = ?",
                        newSeats, Math.abs(adjustment), Timestamp.from(Instant.now()), subscriptionId);
            } else {
                jdbcTemplate.update(
                        "UPDATE tenant_subscriptions SET seat_quantity = ?, updated_at = ? WHERE id = ?",
                        newSeats, Timestamp.from(Instant.now()), subscriptionId);
            }
            if (adjustment > 0) {
                issueInvoice(getSubscription(subscriptionId), adjustment,
                        "Prorated seat increase", before.currentPeriodStart(), before.currentPeriodEnd());
            }
            recordEvent(subscriptionId, before.tenantId(), "SEATS.CHANGED", before.planId(), before.planId(),
                    "IMMEDIATE", adjustment, request.reason(), Instant.now(), authentication);
        }
        SubscriptionResponse after = getSubscription(subscriptionId);
        auditService.success(authentication, before.tenantId(), "SUBSCRIPTION.SEATS.CHANGE", "TENANT_SUBSCRIPTION",
                subscriptionId.toString(), request.reason(), before, after);
        return after;
    }

    @Transactional
    public SubscriptionResponse cancelSubscription(
            UUID subscriptionId,
            CancelSubscriptionRequest request,
            Authentication authentication
    ) {
        SubscriptionResponse before = getSubscription(subscriptionId);
        ensureMutableSubscription(before);
        Instant now = Instant.now();
        if (request.immediate()) {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET status = 'CANCELLED', cancel_at_period_end = FALSE, "
                            + "cancelled_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), Timestamp.from(now), subscriptionId);
            recordEvent(subscriptionId, before.tenantId(), "SUBSCRIPTION.CANCELLED", before.planId(), null,
                    "IMMEDIATE", 0, request.reason(), now, authentication);
        } else {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET cancel_at_period_end = TRUE, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), subscriptionId);
            recordEvent(subscriptionId, before.tenantId(), "SUBSCRIPTION.CANCELLATION.SCHEDULED", before.planId(), null,
                    "NEXT_CYCLE", 0, request.reason(), before.currentPeriodEnd(), authentication);
        }
        SubscriptionResponse after = getSubscription(subscriptionId);
        auditService.success(authentication, before.tenantId(), "SUBSCRIPTION.CANCEL", "TENANT_SUBSCRIPTION",
                subscriptionId.toString(), request.reason(), before, after);
        return after;
    }

    @Transactional
    public SubscriptionResponse resumeSubscription(UUID subscriptionId, Authentication authentication) {
        SubscriptionResponse before = getSubscription(subscriptionId);
        Instant now = Instant.now();
        if (!"CANCELLED".equals(before.status())) {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET cancel_at_period_end = FALSE, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), subscriptionId);
        } else {
            PlanResponse plan = activePlan(before.planId());
            validateUsageAgainstPlan(before.tenantId(), before.seatQuantity(), plan);
            Instant periodEnd = nextPeriod(now, before.billingCycle());
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET status = 'ACTIVE', cancel_at_period_end = FALSE, cancelled_at = NULL, "
                            + "current_period_start = ?, current_period_end = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), Timestamp.from(periodEnd), Timestamp.from(now), subscriptionId);
            issueRecurringInvoice(getSubscription(subscriptionId), plan, "Subscription resumed");
        }
        recordEvent(subscriptionId, before.tenantId(), "SUBSCRIPTION.RESUMED", before.planId(), before.planId(),
                "IMMEDIATE", 0, "Resumed from control plane", now, authentication);
        SubscriptionResponse after = getSubscription(subscriptionId);
        auditService.success(authentication, before.tenantId(), "SUBSCRIPTION.RESUME", "TENANT_SUBSCRIPTION",
                subscriptionId.toString(), "Resumed from control plane", before, after);
        return after;
    }

    @Transactional
    public SubscriptionResponse renewSubscription(UUID subscriptionId, Authentication authentication) {
        SubscriptionResponse before = getSubscription(subscriptionId);
        if ("CANCELLED".equals(before.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled subscription must be resumed first");
        }
        Instant now = Instant.now();
        if (before.cancelAtPeriodEnd()) {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET status = 'CANCELLED', cancel_at_period_end = FALSE, cancelled_at = ?, updated_at = ? WHERE id = ?",
                    Timestamp.from(now), Timestamp.from(now), subscriptionId);
            recordEvent(subscriptionId, before.tenantId(), "SUBSCRIPTION.CANCELLED", before.planId(), null,
                    "NEXT_CYCLE", 0, "Scheduled cancellation applied", now, authentication);
            return getSubscription(subscriptionId);
        }

        UUID planId = before.pendingPlanId() == null ? before.planId() : before.pendingPlanId();
        String billingCycle = before.pendingBillingCycle() == null ? before.billingCycle() : before.pendingBillingCycle();
        PlanResponse plan = activePlan(planId);
        validateUsageAgainstPlan(before.tenantId(), before.seatQuantity(), plan);
        Instant periodEnd = nextPeriod(now, billingCycle);
        jdbcTemplate.update(
                "UPDATE tenant_subscriptions SET plan_id = ?, billing_cycle = ?, pending_plan_id = NULL, "
                        + "pending_billing_cycle = NULL, status = 'ACTIVE', trial_ends_at = NULL, "
                        + "current_period_start = ?, current_period_end = ?, updated_at = ? WHERE id = ?",
                planId, billingCycle, Timestamp.from(now), Timestamp.from(periodEnd), Timestamp.from(now), subscriptionId);
        SubscriptionResponse renewed = getSubscription(subscriptionId);
        issueRecurringInvoice(renewed, plan, "Subscription renewal");
        recordEvent(subscriptionId, before.tenantId(), "SUBSCRIPTION.RENEWED", before.planId(), planId,
                "NEXT_CYCLE", 0, "Renewal processed", now, authentication);
        SubscriptionResponse after = getSubscription(subscriptionId);
        auditService.success(authentication, before.tenantId(), "SUBSCRIPTION.RENEW", "TENANT_SUBSCRIPTION",
                subscriptionId.toString(), "Renewal processed", before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(UUID tenantId) {
        String sql = invoiceSelect();
        if (tenantId == null) {
            return jdbcTemplate.query(sql + " ORDER BY i.created_at DESC", this::mapInvoice);
        }
        return jdbcTemplate.query(sql + " WHERE i.tenant_id = ? ORDER BY i.created_at DESC",
                this::mapInvoice, tenantId);
    }

    @Transactional
    public InvoiceResponse markInvoicePaid(
            UUID invoiceId,
            MarkInvoicePaidRequest request,
            Authentication authentication
    ) {
        InvoiceResponse before = getInvoice(invoiceId);
        if ("VOID".equals(before.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Void invoice cannot be paid");
        }
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE billing_invoices SET status = 'PAID', amount_paid_minor = total_minor, paid_at = ?, "
                        + "payment_reference = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(now), request.paymentReference().trim(), Timestamp.from(now), invoiceId);
        InvoiceResponse after = getInvoice(invoiceId);
        auditService.success(authentication, before.tenantId(), "INVOICE.MARK.PAID", "BILLING_INVOICE",
                invoiceId.toString(), request.reason(), before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID invoiceId) {
        List<InvoiceResponse> rows = jdbcTemplate.query(invoiceSelect() + " WHERE i.id = ?", this::mapInvoice, invoiceId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return rows.get(0);
    }

    private PlanResponse activePlan(UUID planId) {
        PlanResponse plan = getPlan(planId);
        if (!"ACTIVE".equals(plan.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected plan is not active");
        }
        return plan;
    }

    private void ensureMutableSubscription(SubscriptionResponse subscription) {
        if (!SUBSCRIPTION_STATUSES.contains(subscription.status()) || "CANCELLED".equals(subscription.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription is not mutable in its current state");
        }
    }

    private void validateUsageAgainstPlan(UUID tenantId, int seats, PlanResponse plan) {
        if (seats > plan.maxUsers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat quantity exceeds the selected plan limit");
        }
        long organizations = count(
                "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND status <> 'ARCHIVED'",
                tenantId);
        if (organizations > plan.maxOrganizations()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current organization count exceeds the selected plan limit");
        }
        long occupiedSeats = count(
                "SELECT COUNT(DISTINCT LOWER(email)) FROM organization_memberships "
                        + "WHERE tenant_id = ? AND status IN ('INVITED', 'ACTIVE')",
                tenantId);
        if (occupiedSeats > seats) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat quantity cannot be lower than current invited and active members");
        }
    }

    private void replaceEntitlements(UUID planId, List<EntitlementRequest> entitlements) {
        jdbcTemplate.update("DELETE FROM saas_plan_entitlements WHERE plan_id = ?", planId);
        if (entitlements == null) return;
        Instant now = Instant.now();
        Set<String> seen = new java.util.HashSet<>();
        for (EntitlementRequest entitlement : entitlements) {
            String featureCode = normalizeCode(entitlement.featureCode());
            if (!seen.add(featureCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate entitlement: " + featureCode);
            }
            jdbcTemplate.update(
                    "INSERT INTO saas_plan_entitlements "
                            + "(id, plan_id, feature_code, enabled, limit_value, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), planId, featureCode, entitlement.enabled(), entitlement.limitValue(), Timestamp.from(now), Timestamp.from(now));
        }
    }

    private PlanCore mapPlanCore(ResultSet rs, int rowNum) throws SQLException {
        return new PlanCore(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), rs.getString("currency_code"),
                rs.getLong("monthly_price_minor"), rs.getLong("annual_price_minor"),
                rs.getInt("trial_days"), rs.getInt("max_users"), rs.getInt("max_organizations"),
                rs.getLong("storage_mb"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private PlanResponse toPlanResponse(PlanCore plan) {
        List<EntitlementResponse> entitlements = jdbcTemplate.query(
                "SELECT id, feature_code, enabled, limit_value FROM saas_plan_entitlements "
                        + "WHERE plan_id = ? ORDER BY feature_code",
                this::mapEntitlement,
                plan.id());
        return new PlanResponse(plan.id(), plan.code(), plan.name(), plan.description(), plan.status(),
                plan.currencyCode(), plan.monthlyPriceMinor(), plan.annualPriceMinor(), plan.trialDays(),
                plan.maxUsers(), plan.maxOrganizations(), plan.storageMb(), entitlements,
                plan.createdAt(), plan.updatedAt());
    }

    private EntitlementResponse mapEntitlement(ResultSet rs, int rowNum) throws SQLException {
        Object limit = rs.getObject("limit_value");
        return new EntitlementResponse(
                rs.getObject("id", UUID.class), rs.getString("feature_code"), rs.getBoolean("enabled"),
                limit == null ? null : ((Number) limit).longValue());
    }

    private SubscriptionResponse mapSubscription(ResultSet rs, int rowNum) throws SQLException {
        return new SubscriptionResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"),
                rs.getObject("plan_id", UUID.class), rs.getString("plan_code"), rs.getString("plan_name"),
                rs.getObject("pending_plan_id", UUID.class), rs.getString("pending_plan_code"),
                rs.getString("status"), rs.getString("billing_cycle"), rs.getString("pending_billing_cycle"),
                rs.getInt("seat_quantity"), rs.getLong("credit_balance_minor"), rs.getString("currency_code"),
                instant(rs, "started_at"), instant(rs, "trial_ends_at"), instant(rs, "current_period_start"),
                instant(rs, "current_period_end"), rs.getBoolean("cancel_at_period_end"),
                instant(rs, "cancelled_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private SubscriptionEventResponse mapSubscriptionEvent(ResultSet rs, int rowNum) throws SQLException {
        return new SubscriptionEventResponse(
                rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
                rs.getString("action"), rs.getObject("old_plan_id", UUID.class), rs.getObject("new_plan_id", UUID.class),
                rs.getString("effective_mode"), rs.getLong("adjustment_minor"), rs.getString("reason"),
                instant(rs, "effective_at"), instant(rs, "created_at"));
    }

    private InvoiceResponse mapInvoice(ResultSet rs, int rowNum) throws SQLException {
        return new InvoiceResponse(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"),
                rs.getObject("subscription_id", UUID.class), rs.getString("invoice_number"), rs.getString("status"),
                rs.getString("currency_code"), rs.getLong("subtotal_minor"), rs.getLong("credit_applied_minor"),
                rs.getLong("tax_minor"), rs.getLong("total_minor"), rs.getLong("amount_paid_minor"),
                rs.getString("description"), instant(rs, "period_start"), instant(rs, "period_end"),
                instant(rs, "due_at"), instant(rs, "paid_at"), rs.getString("payment_reference"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private void issueRecurringInvoice(SubscriptionResponse subscription, PlanResponse plan, String description) {
        long subtotal = Math.multiplyExact(price(plan, subscription.billingCycle()), subscription.seatQuantity());
        issueInvoice(subscription, subtotal, description, subscription.currentPeriodStart(), subscription.currentPeriodEnd());
    }

    private void issueInvoice(
            SubscriptionResponse subscription,
            long subtotal,
            String description,
            Instant periodStart,
            Instant periodEnd
    ) {
        if (subtotal <= 0) return;
        long credit = Math.min(subscription.creditBalanceMinor(), subtotal);
        long total = subtotal - credit;
        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, subtotal_minor, "
                        + "credit_applied_minor, tax_minor, total_minor, amount_paid_minor, description, period_start, "
                        + "period_end, due_at, paid_at, payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, 0, ?, 0, ?, ?, ?, ?, NULL, NULL, ?, ?)",
                invoiceId, subscription.tenantId(), subscription.id(), invoiceNumber(), subscription.currencyCode(),
                subtotal, credit, total, description, Timestamp.from(periodStart), Timestamp.from(periodEnd), Timestamp.from(now.plus(Duration.ofDays(14))), Timestamp.from(now), Timestamp.from(now));
        if (credit > 0) {
            jdbcTemplate.update(
                    "UPDATE tenant_subscriptions SET credit_balance_minor = credit_balance_minor - ?, updated_at = ? WHERE id = ?",
                    credit, Timestamp.from(now), subscription.id());
        }
    }

    private long proratedAdjustment(
            SubscriptionResponse subscription,
            PlanResponse oldPlan,
            PlanResponse newPlan,
            String newCycle
    ) {
        long oldAmount = Math.multiplyExact(price(oldPlan, subscription.billingCycle()), subscription.seatQuantity());
        long newAmount = Math.multiplyExact(price(newPlan, newCycle), subscription.seatQuantity());
        return prorate(newAmount - oldAmount, subscription.currentPeriodStart(), subscription.currentPeriodEnd());
    }

    private long prorate(long fullPeriodDelta, Instant periodStart, Instant periodEnd) {
        Instant now = Instant.now();
        long totalSeconds = Math.max(1, Duration.between(periodStart, periodEnd).getSeconds());
        long remainingSeconds = Math.max(0, Duration.between(now, periodEnd).getSeconds());
        return BigDecimal.valueOf(fullPeriodDelta)
                .multiply(BigDecimal.valueOf(remainingSeconds))
                .divide(BigDecimal.valueOf(totalSeconds), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long price(PlanResponse plan, String billingCycle) {
        return "ANNUAL".equals(normalizeCycle(billingCycle)) ? plan.annualPriceMinor() : plan.monthlyPriceMinor();
    }

    private Instant nextPeriod(Instant start, String billingCycle) {
        ZonedDateTime value = ZonedDateTime.ofInstant(start, ZoneOffset.UTC);
        return ("ANNUAL".equals(normalizeCycle(billingCycle)) ? value.plusYears(1) : value.plusMonths(1)).toInstant();
    }

    private void recordEvent(
            UUID subscriptionId,
            UUID tenantId,
            String action,
            UUID oldPlanId,
            UUID newPlanId,
            String effectiveMode,
            long adjustment,
            String reason,
            Instant effectiveAt,
            Authentication authentication
    ) {
        PrincipalIds principal = principal(authentication);
        jdbcTemplate.update(
                "INSERT INTO subscription_change_events "
                        + "(id, subscription_id, tenant_id, action, old_plan_id, new_plan_id, effective_mode, "
                        + "adjustment_minor, reason, actor_tenant_id, actor_user_id, effective_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), subscriptionId, tenantId, action, oldPlanId, newPlanId, effectiveMode,
                adjustment, blankToNull(reason), principal.tenantId(), principal.userId(), Timestamp.from(effectiveAt), Timestamp.from(Instant.now()));
    }

    private void ensureTenant(UUID tenantId) {
        if (count("SELECT COUNT(*) FROM tenants WHERE id = ?", tenantId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
    }

    private String subscriptionSelect() {
        return "SELECT s.id, s.tenant_id, t.name AS tenant_name, s.plan_id, p.code AS plan_code, p.name AS plan_name, "
                + "s.pending_plan_id, pp.code AS pending_plan_code, s.status, s.billing_cycle, s.pending_billing_cycle, "
                + "s.seat_quantity, s.credit_balance_minor, p.currency_code, s.started_at, s.trial_ends_at, "
                + "s.current_period_start, s.current_period_end, s.cancel_at_period_end, s.cancelled_at, "
                + "s.created_at, s.updated_at FROM tenant_subscriptions s "
                + "JOIN tenants t ON t.id = s.tenant_id JOIN saas_plans p ON p.id = s.plan_id "
                + "LEFT JOIN saas_plans pp ON pp.id = s.pending_plan_id";
    }

    private String invoiceSelect() {
        return "SELECT i.id, i.tenant_id, t.name AS tenant_name, i.subscription_id, i.invoice_number, i.status, "
                + "i.currency_code, i.subtotal_minor, i.credit_applied_minor, i.tax_minor, i.total_minor, "
                + "i.amount_paid_minor, i.description, i.period_start, i.period_end, i.due_at, i.paid_at, "
                + "i.payment_reference, i.created_at, i.updated_at FROM billing_invoices i "
                + "JOIN tenants t ON t.id = i.tenant_id";
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long scalarLong(String sql, Object... args) {
        return count(sql, args);
    }

    private String invoiceNumber() {
        return "INV-" + INVOICE_DAY.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCode(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code is required");
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = normalize(value);
        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency code");
        }
        return normalized;
    }

    private static String normalizeCycle(String value) {
        String normalized = normalize(value);
        if (!BILLING_CYCLES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported billing cycle");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new SQLException("Unsupported timestamp type for " + column);
    }

    @SuppressWarnings("unchecked")
    private static PrincipalIds principal(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return new PrincipalIds(null, null);
        }
        return new PrincipalIds(uuid(details.get("tenant_id")), uuid(details.get("user_id")));
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record PrincipalIds(UUID tenantId, UUID userId) {
    }

    private record PlanCore(
            UUID id,
            String code,
            String name,
            String description,
            String status,
            String currencyCode,
            long monthlyPriceMinor,
            long annualPriceMinor,
            int trialDays,
            int maxUsers,
            int maxOrganizations,
            long storageMb,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}