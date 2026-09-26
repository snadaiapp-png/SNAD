package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.PlanResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.SubscriptionResponse;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.security.service.RegistrationProvisioner;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Single commercial provisioning authority for Executive/control-plane tenant creation.
 *
 * <p>The whole command is transactional: registration, tenant profile, subscription
 * creation, plan composition, entitlements/audit side effects and final commercial
 * state either commit together or roll back together. No subscription failure is
 * swallowed and no catalog entry is created implicitly.</p>
 */
@Service
public class ExecutiveTenantProvisioningService {

    private final JdbcTemplate jdbc;
    private final RegistrationProvisioner registrationProvisioner;
    private final SaasAdministrationService saasService;
    private final TenantCommercialStateService commercialStateService;
    private final PlatformAuditService auditService;

    public ExecutiveTenantProvisioningService(
            JdbcTemplate jdbc,
            RegistrationProvisioner registrationProvisioner,
            SaasAdministrationService saasService,
            TenantCommercialStateService commercialStateService,
            PlatformAuditService auditService
    ) {
        this.jdbc = jdbc;
        this.registrationProvisioner = registrationProvisioner;
        this.saasService = saasService;
        this.commercialStateService = commercialStateService;
        this.auditService = auditService;
    }

    public record ProvisionedExecutiveTenant(
            TenantResponse tenant,
            TenantCommercialStateService.TenantCommercialState commercialState
    ) {
    }

    @Transactional
    public ProvisionedExecutiveTenant provision(
            CreateTenantRequest request,
            Authentication authentication
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant request is required");
        }

        String subdomain = normalizedSubdomain(request.subdomain());
        ensureSubdomainAvailable(subdomain);
        int seats = request.seatQuantity() == null ? 1 : request.seatQuantity();
        if (seats < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seatQuantity must be at least 1");
        }
        Integer trialDays = request.trialDays();
        if (trialDays != null && (trialDays < 0 || trialDays > 365)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trialDays must be between 0 and 365");
        }
        String billingCycle = normalizeCycle(request.billingCycle());
        PlanResponse plan = resolveActivePlan(request.planId(), request.planCode());

        // Plan resolution and all request validation happen before registration so
        // deterministic 4xx failures never create a transient tenant in the first place.
        RegistrationProvisioner.ProvisionedRegistration registration = registrationProvisioner.provision(
                request.adminEmail(),
                request.adminDisplayName(),
                request.name(),
                subdomain,
                null,
                upperOrNull(request.countryCode()));
        UUID tenantId = registration.tenantId();

        Instant now = Instant.now();
        jdbc.update(
                "UPDATE tenants SET name = ?, legal_name = ?, subdomain = ?, status = 'ACTIVE', "
                        + "billing_email = ?, country_code = ?, locale = ?, timezone = ?, currency_code = ?, "
                        + "trial_ends_at = NULL, suspension_reason = NULL, updated_at = ? WHERE id = ?",
                request.name().trim(),
                blankToNull(request.legalName()),
                subdomain,
                lowerOrNull(request.billingEmail()),
                upperOrNull(request.countryCode()),
                defaultValue(request.locale(), "ar-SA"),
                defaultValue(request.timezone(), "Asia/Riyadh"),
                defaultValue(upperOrNull(request.currencyCode()), "SAR"),
                Timestamp.from(now),
                tenantId);

        SubscriptionResponse subscription = saasService.createSubscription(
                new CreateSubscriptionRequest(
                        tenantId,
                        plan.id(),
                        billingCycle,
                        seats,
                        trialDays),
                authentication);

        // tenant.trial_ends_at is profile metadata only; subscription lifecycle remains
        // authoritative. Mirror the actual resolved trial timestamp for display without
        // changing the operational tenant status away from ACTIVE.
        jdbc.update(
                "UPDATE tenants SET trial_ends_at = ?, updated_at = ? WHERE id = ?",
                subscription.trialEndsAt() == null ? null : Timestamp.from(subscription.trialEndsAt()),
                Timestamp.from(Instant.now()),
                tenantId);

        TenantResponse tenant = getTenant(tenantId);
        TenantCommercialStateService.TenantCommercialState commercialState =
                commercialStateService.resolve(tenantId, tenant.status());

        if (commercialState.effectiveSubscriptionId() == null) {
            throw new IllegalStateException(
                    "Commercial provisioning completed without an effective subscription");
        }

        auditService.success(
                authentication,
                tenantId,
                "TENANT.PROVISION",
                "TENANT",
                tenantId.toString(),
                "Atomic Executive commercial provisioning",
                null,
                java.util.Map.of(
                        "tenant", tenant,
                        "administratorUserId", registration.userId(),
                        "subscriptionId", subscription.id(),
                        "planId", plan.id(),
                        "billingCycle", subscription.billingCycle(),
                        "seatQuantity", subscription.seatQuantity()));

        return new ProvisionedExecutiveTenant(tenant, commercialState);
    }

    private PlanResponse resolveActivePlan(UUID planId, String requestedPlanCode) {
        PlanResponse plan;
        if (planId != null) {
            plan = saasService.getPlan(planId);
        } else {
            String code = blankToNull(requestedPlanCode);
            if (code == null) {
                code = "STARTER";
            }
            final String expectedCode = code;
            List<PlanResponse> matching = saasService.listPlans().stream()
                    .filter(candidate -> expectedCode.equalsIgnoreCase(candidate.code()))
                    .toList();
            if (matching.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No plan found for code: " + expectedCode);
            }
            if (matching.size() != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ambiguous plan code: " + expectedCode);
            }
            plan = matching.get(0);
        }

        if (!"ACTIVE".equalsIgnoreCase(plan.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plan is not commercially active: " + plan.code());
        }
        return plan;
    }

    private void ensureSubdomainAvailable(String subdomain) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE LOWER(subdomain) = LOWER(?)",
                Long.class,
                subdomain);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant subdomain already exists");
        }
    }

    private TenantResponse getTenant(UUID tenantId) {
        List<TenantResponse> rows = jdbc.query(
                "SELECT id, name, legal_name, subdomain, status, billing_email, country_code, locale, "
                        + "timezone, currency_code, trial_ends_at, suspension_reason, created_at, updated_at "
                        + "FROM tenants WHERE id = ?",
                this::mapTenant,
                tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provisioned tenant not found");
        }
        return rows.get(0);
    }

    private TenantResponse mapTenant(ResultSet rs, int row) throws SQLException {
        return new TenantResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("legal_name"),
                rs.getString("subdomain"),
                rs.getString("status"),
                rs.getString("billing_email"),
                rs.getString("country_code"),
                rs.getString("locale"),
                rs.getString("timezone"),
                rs.getString("currency_code"),
                instant(rs, "trial_ends_at"),
                rs.getString("suspension_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String normalizedSubdomain(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subdomain is required");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeCycle(String value) {
        String cycle = blankToNull(value);
        if (cycle == null) return "MONTHLY";
        cycle = cycle.toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("MONTHLY", "ANNUAL").contains(cycle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported billing cycle");
        }
        return cycle;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lowerOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String defaultValue(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }
}
