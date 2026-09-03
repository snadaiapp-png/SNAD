package com.sanad.platform.subscription.change;

import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceCalculator;
import com.sanad.platform.subscription.pricing.PriceEntity;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.pricing.PriceTier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Item-aware subscription change engine — Preview → Validate → Confirm/Execute
 * → Audit (mission §11). Extends the existing
 * {@code SubscriptionImpactService} concepts from plan-vs-plan to the
 * multi-item world.
 *
 * <p>R0C-2R country authority: the pricing country is ALWAYS resolved
 * server-side from the authoritative {@code tenants.country_code} —
 * CLIENT_COUNTRY_AUTHORITY = NONE. Any client-supplied country parameter is
 * deliberately ignored (retained on the wire for backward compatibility
 * only). When the tenant carries no country the resolver's GLOBAL fallback
 * applies.</p>
 */
@Service
public class SubscriptionChangeService {

    private final JdbcTemplate jdbc;
    private final SubscriptionItemRepository itemRepository;
    private final PriceResolver priceResolver;

    public SubscriptionChangeService(JdbcTemplate jdbc,
                                     SubscriptionItemRepository itemRepository,
                                     PriceResolver priceResolver) {
        this.jdbc = jdbc;
        this.itemRepository = itemRepository;
        this.priceResolver = priceResolver;
    }

    public record ItemLine(UUID itemId, String itemType, String name, int quantity,
                           Long unitAmountMinor, String currencyCode) {
    }

    public record ChangePreview(
            UUID subscriptionId,
            UUID targetPlanVersionId,
            String fromStatus,
            List<ItemLine> currentItems,
            Long currentMonthlyMinor,
            Long targetMonthlyMinor,
            Long deltaMonthlyMinor,
            String currencyCode,
            List<String> warnings) {
    }

    public record ChangeResult(UUID subscriptionId, String status, String reason) {
    }

    @Transactional(readOnly = true)
    public ChangePreview preview(UUID subscriptionId, UUID targetPlanVersionId,
                                 String clientCountryCode, Instant at) {
        SubscriptionContext ctx = requireSubscription(subscriptionId);
        String billingInterval = ctx.billingCycle() != null ? ctx.billingCycle() : "MONTHLY";
        // P0-A: tenant's authoritative country wins; the client-supplied value
        // is intentionally NOT used for pricing (authority NONE).
        String pricingCountry = ctx.tenantCountryCode() != null
                ? ctx.tenantCountryCode() : PriceResolver.GLOBAL;
        List<SubscriptionItemEntity> items = itemRepository
                .findBySubscriptionId(subscriptionId).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus()))
                .toList();

        List<ItemLine> lines = items.stream()
                .map(i -> new ItemLine(i.getId(), i.getItemType(), i.getNameSnapshot(),
                        i.getQuantity(), i.getUnitAmountMinor(), i.getCurrencyCode()))
                .toList();

        Optional<SubscriptionItemEntity> planItem = items.stream()
                .filter(i -> "PLAN".equals(i.getItemType()))
                .findFirst();
        long currentMonthly = planItem.map(i -> nvl(i.getUnitAmountMinor())).orElse(0L);

        List<String> warnings = new ArrayList<>();
        Long targetMonthly = null;
        Long delta = null;
        if (planItem.isPresent()) {
            UUID versionId = planItem.get().getPlanVersionId() != null
                    ? planItem.get().getPlanVersionId() : targetPlanVersionId;
            Optional<PriceEntity> price = priceResolver.resolveForPlanVersion(
                    targetPlanVersionId, pricingCountry, billingInterval, at);
            if (price.isPresent()) {
                targetMonthly = compute(price.get(), planItem.get().getQuantity());
                delta = targetMonthly - currentMonthly;
            } else {
                warnings.add("No effective price found for target version (country=" + pricingCountry
                        + ", interval=" + billingInterval + "); change cannot be priced");
            }
        } else {
            warnings.add("Subscription has no ACTIVE PLAN item to change");
        }

        return new ChangePreview(subscriptionId, targetPlanVersionId, ctx.status(),
                lines, currentMonthly, targetMonthly, delta,
                planItem.map(SubscriptionItemEntity::getCurrencyCode).orElse(null),
                warnings);
    }

    /**
     * Executes a previewed plan-item change: cancels the current PLAN item and
     * inserts a new one pinned to the target plan version. Warnings are
     * blocking — a change that cannot be priced is never executed silently.
     */
    @Transactional
    public ChangeResult execute(UUID subscriptionId, UUID targetPlanVersionId,
                                String clientCountryCode, String reason,
                                UUID actorTenantId, UUID actorUserId) {
        ChangePreview preview = preview(subscriptionId, targetPlanVersionId,
                clientCountryCode, Instant.now());
        if (!preview.warnings().isEmpty()) {
            throw new IllegalStateException(
                    "Change preview has warnings; refusing to execute: " + preview.warnings());
        }

        SubscriptionItemEntity currentPlanItem = itemRepository
                .findActiveBySubscriptionIdAndType(subscriptionId, "PLAN")
                .orElseThrow(() -> new IllegalStateException("No ACTIVE PLAN item"));

        Map<String, Object> versionRow;
        try {
            versionRow = jdbc.queryForMap(
                    "SELECT pv.plan_id, pv.currency_code FROM plan_versions pv WHERE pv.id = ?",
                    targetPlanVersionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown plan version: " + targetPlanVersionId);
        }

        applyCanonicalPlanCompositionChange(subscriptionId,
                (UUID) versionRow.get("plan_id"), targetPlanVersionId,
                preview.targetMonthlyMinor(), (String) versionRow.get("currency_code"),
                currentPlanItem.getQuantity(), reason, actorTenantId, actorUserId);

        return new ChangeResult(subscriptionId, "EXECUTED", reason);
    }

    /**
     * R0C-4 (recovery STAGE-3) — deterministic target version resolution.
     * Returns the latest ACTIVE plan_version of the plan (ordered by
     * version_number). Fails closed when the plan has no ACTIVE version —
     * never silently falls back to an arbitrary or stale version.
     */
    @Transactional(readOnly = true)
    public UUID resolveActivePlanVersion(UUID planId) {
        List<UUID> versions = jdbc.queryForList(
                "SELECT id FROM plan_versions WHERE plan_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY version_number DESC",
                UUID.class, planId);
        if (versions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Plan has no ACTIVE version; refusing plan composition change");
        }
        return versions.get(0);
    }

    /**
     * R0C-4 (recovery STAGE-3) — canonical birth of the initial ACTIVE PLAN
     * item for a newly created subscription. Composition-only: billing,
     * audit, and entitlement events remain the caller's responsibility.
     * Joins the caller's transaction (REQUIRED propagation).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void insertInitialPlanItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID planVersionId,
                                      long unitAmountMinor, String currencyCode, int quantity) {
        SubscriptionItemEntity item = new SubscriptionItemEntity();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenantId);
        item.setSubscriptionId(subscriptionId);
        item.setItemType("PLAN");
        item.setPlanId(planId);
        item.setPlanVersionId(planVersionId);
        item.setQuantity(Math.max(1, quantity));
        item.setUnitAmountMinor(unitAmountMinor);
        item.setCurrencyCode(currencyCode);
        item.setStatus("ACTIVE");
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        itemRepository.insert(item);
    }

    /**
     * R0C-4 (recovery STAGE-3) — THE single canonical authority for effective
     * PLAN composition mutation. Cancels the current ACTIVE PLAN item (if
     * any), inserts the new ACTIVE PLAN item pinned to the target version,
     * converges both compatibility anchors, and writes the command ledger —
     * all inside the caller's transaction (REQUIRED propagation joins it).
     *
     * <p>Composition-only contract: this method must NOT price, prorate,
     * issue invoices, change payment status, or publish entitlement events —
     * those remain with the caller (SCP execute path or the legacy engine).
     * Both {@link #execute} and the converged legacy paths route through
     * here, so there is exactly one writer of effective plan composition.</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void applyCanonicalPlanCompositionChange(UUID subscriptionId, UUID targetPlanId,
                                                    UUID targetPlanVersionId, long unitAmountMinor,
                                                    String currencyCode, int quantity,
                                                    String reason, UUID actorTenantId, UUID actorUserId) {
        SubscriptionContext ctx = requireSubscription(subscriptionId);

        itemRepository.findActiveBySubscriptionIdAndType(subscriptionId, "PLAN")
                .ifPresent(current -> itemRepository.updateStatus(current.getId(), "CANCELLED"));

        insertInitialPlanItem(subscriptionId, ctx.tenantId(), targetPlanId, targetPlanVersionId,
                unitAmountMinor, currencyCode, quantity);

        // R0C-3 (recovery STAGE-2): the compatibility mirrors must never
        // diverge from the effective ACTIVE PLAN item.
        jdbc.update("""
                        UPDATE tenant_subscriptions
                        SET plan_id = ?, plan_version_id = ?, updated_at = NOW()
                        WHERE id = ?
                        """,
                targetPlanId, targetPlanVersionId, subscriptionId);

        // P0-C: subscription_commands.from_status/to_status are VARCHAR(24) and
        // carry lifecycle statuses. A plan change does not transition the
        // subscription status, so from == to == the subscription's actual
        // status (max vocabulary length 17). The target-version detail is
        // preserved in the VARCHAR(500) reason column — never in to_status.
        String status = ctx.status();
        String ledgerReason = "TARGET_VERSION=" + targetPlanVersionId
                + (reason != null && !reason.isBlank() ? "; " + reason : "");
        jdbc.update("""
                        INSERT INTO subscription_commands (
                            id, subscription_id, tenant_id, command, from_status, to_status,
                            reason, actor_tenant_id, actor_user_id, correlation_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), subscriptionId, ctx.tenantId(), "PLAN_CHANGE",
                status, status, ledgerReason,
                actorTenantId, actorUserId, null);
    }

    private long compute(PriceEntity price, int quantity) {
        List<PriceTier> tiers = PriceRepository.parseTiers(price.getTiersJson());
        return PriceCalculator.computeWithBounds(
                price.getPriceModel(), price.getBaseAmountMinor(), price.getUnitAmountMinor(),
                tiers.isEmpty() ? null : tiers, quantity, 0,
                price.getMinAmountMinor(), price.getMaxAmountMinor());
    }

    /**
     * Server-side subscription context. P0-B: fetched with a RowMapper over a
     * single joined query (never a multi-column scalar queryForObject).
     * P0-A: the pricing country comes from the authoritative
     * {@code tenants.country_code} via the join — tenant_subscriptions has no
     * country column.
     */
    record SubscriptionContext(UUID tenantId, UUID planId, String status,
                                String billingCycle, String tenantCountryCode) {
    }

    private SubscriptionContext requireSubscription(UUID subscriptionId) {
        try {
            return jdbc.queryForObject("""
                            SELECT s.tenant_id, s.plan_id, s.status, s.billing_cycle, t.country_code
                            FROM tenant_subscriptions s
                            JOIN tenants t ON t.id = s.tenant_id
                            WHERE s.id = ?
                            """,
                    (rs, rowNum) -> new SubscriptionContext(
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("plan_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("billing_cycle"),
                            rs.getString("country_code")),
                    subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
