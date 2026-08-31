package com.sanad.platform.subscription.change;

import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceCalculator;
import com.sanad.platform.subscription.pricing.PriceEntity;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.pricing.PriceTier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                                 String countryCode, Instant at) {
        requireSubscription(subscriptionId);
        String billingInterval = billingInterval(subscriptionId);
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
                    targetPlanVersionId, countryCode, billingInterval, at);
            if (price.isPresent()) {
                targetMonthly = compute(price.get(), planItem.get().getQuantity());
                delta = targetMonthly - currentMonthly;
            } else {
                warnings.add("No effective price found for target version (country=" + countryCode
                        + ", interval=" + billingInterval + "); change cannot be priced");
            }
        } else {
            warnings.add("Subscription has no ACTIVE PLAN item to change");
        }

        return new ChangePreview(subscriptionId, targetPlanVersionId, "CURRENT",
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
                                String countryCode, String reason,
                                UUID actorTenantId, UUID actorUserId) {
        ChangePreview preview = preview(subscriptionId, targetPlanVersionId, countryCode, Instant.now());
        if (!preview.warnings().isEmpty()) {
            throw new IllegalStateException(
                    "Change preview has warnings; refusing to execute: " + preview.warnings());
        }

        SubscriptionItemEntity currentPlanItem = itemRepository
                .findActiveBySubscriptionIdAndType(subscriptionId, "PLAN")
                .orElseThrow(() -> new IllegalStateException("No ACTIVE PLAN item"));
        itemRepository.updateStatus(currentPlanItem.getId(), "CANCELLED");

        Map<String, Object> versionRow;
        try {
            versionRow = jdbc.queryForMap(
                    "SELECT pv.plan_id, pv.currency_code FROM plan_versions pv WHERE pv.id = ?",
                    targetPlanVersionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown plan version: " + targetPlanVersionId);
        }
        UUID planId = (UUID) versionRow.get("plan_id");
        String currency = (String) versionRow.get("currency_code");

        SubscriptionItemEntity newItem = new SubscriptionItemEntity();
        newItem.setId(UUID.randomUUID());
        newItem.setTenantId(currentPlanItem.getTenantId());
        newItem.setSubscriptionId(subscriptionId);
        newItem.setItemType("PLAN");
        newItem.setPlanId(planId);
        newItem.setPlanVersionId(targetPlanVersionId);
        newItem.setQuantity(currentPlanItem.getQuantity());
        newItem.setUnitAmountMinor(preview.targetMonthlyMinor());
        newItem.setCurrencyCode(currency);
        newItem.setStatus("ACTIVE");
        newItem.setCreatedAt(Instant.now());
        newItem.setUpdatedAt(Instant.now());
        itemRepository.insert(newItem);

        jdbc.update("""
                        INSERT INTO subscription_commands (
                            id, subscription_id, tenant_id, command, from_status, to_status,
                            reason, actor_tenant_id, actor_user_id, correlation_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), subscriptionId, currentPlanItem.getTenantId(), "PLAN_CHANGE",
                "CURRENT", "TARGET_VERSION=" + targetPlanVersionId, reason,
                actorTenantId, actorUserId, null);

        return new ChangeResult(subscriptionId, "EXECUTED", reason);
    }

    private long compute(PriceEntity price, int quantity) {
        List<PriceTier> tiers = PriceRepository.parseTiers(price.getTiersJson());
        return PriceCalculator.computeWithBounds(
                price.getPriceModel(), price.getBaseAmountMinor(), price.getUnitAmountMinor(),
                tiers.isEmpty() ? null : tiers, quantity, 0,
                price.getMinAmountMinor(), price.getMaxAmountMinor());
    }

    private void requireSubscription(UUID subscriptionId) {
        try {
            jdbc.queryForObject(
                    "SELECT tenant_id, plan_id FROM tenant_subscriptions WHERE id = ?",
                    UUID.class, subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }
    }

    private String billingInterval(UUID subscriptionId) {
        try {
            String cycle = jdbc.queryForObject(
                    "SELECT billing_cycle, country_code FROM tenant_subscriptions WHERE id = ?",
                    String.class, subscriptionId);
            return cycle != null ? cycle : "MONTHLY";
        } catch (EmptyResultDataAccessException e) {
            return "MONTHLY";
        }
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
