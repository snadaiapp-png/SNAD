package com.sanad.platform.subscription.item;

import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Subscription item management — the 1..N billable lines of a subscription.
 *
 * <p>Validates type-specific references, pins PLAN items to the plan's ACTIVE
 * version, and provides the dual-compatible {@link #effectivePlanVersionId}
 * read used while the legacy single-plan columns still exist on
 * {@code tenant_subscriptions}.
 */
@Service
public class SubscriptionItemService {

    public static final String TYPE_PLAN = "PLAN";
    public static final String TYPE_ADD_ON = "ADD_ON";
    public static final String TYPE_METERED = "METERED";
    public static final String TYPE_OTHER = "OTHER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final Set<String> ITEM_TYPES =
            Set.of(TYPE_PLAN, TYPE_ADD_ON, TYPE_METERED, TYPE_OTHER);

    private final JdbcTemplate jdbc;
    private final SubscriptionItemRepository repository;
    private final PlanVersionRepository planVersionRepository;

    public SubscriptionItemService(JdbcTemplate jdbc,
                                   SubscriptionItemRepository repository,
                                   PlanVersionRepository planVersionRepository) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.planVersionRepository = planVersionRepository;
    }

    @Transactional
    public SubscriptionItemEntity addItem(UUID subscriptionId, String itemType,
                                          UUID applicationId, UUID productId,
                                          UUID planId, UUID planVersionId,
                                          int quantity, Long unitAmountMinor,
                                          String currencyCode) {
        return addItem(subscriptionId, itemType, applicationId, productId, planId,
                planVersionId, quantity, unitAmountMinor, currencyCode, null);
    }

    /**
     * @param expectedTenantId when non-null, rejects the operation unless the
     *                         subscription belongs to this tenant (caller scope).
     */
    @Transactional
    public SubscriptionItemEntity addItem(UUID subscriptionId, String itemType,
                                          UUID applicationId, UUID productId,
                                          UUID planId, UUID planVersionId,
                                          int quantity, Long unitAmountMinor,
                                          String currencyCode, UUID expectedTenantId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is required");
        }
        UUID ownerTenantId;
        try {
            ownerTenantId = jdbc.queryForObject(
                    "SELECT tenant_id FROM tenant_subscriptions WHERE id = ?",
                    UUID.class, subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            ownerTenantId = null;
        }
        if (ownerTenantId == null) {
            throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);
        }
        if (expectedTenantId != null && !expectedTenantId.equals(ownerTenantId)) {
            throw new IllegalStateException("Subscription belongs to a different tenant");
        }
        if (itemType == null || !ITEM_TYPES.contains(itemType)) {
            throw new IllegalArgumentException("Invalid item type: " + itemType);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (TYPE_PLAN.equals(itemType) && planId == null) {
            throw new IllegalArgumentException("PLAN items require planId");
        }
        if (planId != null) {
            Long planCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM saas_plans WHERE id = ?", Long.class, planId);
            if (planCount == null || planCount == 0) {
                throw new IllegalArgumentException("Unknown plan: " + planId);
            }
        }
        if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currencyCode must be a 3-letter ISO code");
        }

        SubscriptionItemEntity item = new SubscriptionItemEntity();
        item.setId(UUID.randomUUID());
        item.setTenantId(ownerTenantId);
        item.setSubscriptionId(subscriptionId);
        item.setItemType(itemType);
        item.setApplicationId(applicationId);
        item.setProductId(productId);
        item.setPlanId(planId);
        item.setPlanVersionId(resolvePlanVersion(planId, planVersionId));
        item.setNameSnapshot(itemName(planId, applicationId, productId));
        item.setQuantity(quantity);
        item.setUnitAmountMinor(unitAmountMinor);
        item.setCurrencyCode(currencyCode);
        item.setStatus(STATUS_ACTIVE);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        repository.insert(item);
        return item;
    }

    private UUID resolvePlanVersion(UUID planId, UUID requestedVersionId) {
        if (planId == null) {
            return null;
        }
        if (requestedVersionId != null) {
            PlanVersionEntity version = planVersionRepository.findById(requestedVersionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown plan version: " + requestedVersionId));
            if (!planId.equals(version.getPlanId())) {
                throw new IllegalArgumentException("Plan version does not belong to the given plan");
            }
            return requestedVersionId;
        }
        return planVersionRepository.findActiveByPlanId(planId)
                .map(PlanVersionEntity::getId)
                .orElse(null);
    }

    private String itemName(UUID planId, UUID applicationId, UUID productId) {
        if (planId != null) {
            try {
                return jdbc.queryForObject(
                        "SELECT name FROM saas_plans WHERE id = ?", String.class, planId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        }
        if (applicationId != null) {
            try {
                return jdbc.queryForObject(
                        "SELECT name FROM applications WHERE id = ?", String.class, applicationId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        }
        if (productId != null) {
            try {
                return jdbc.queryForObject(
                        "SELECT name FROM products WHERE id = ?", String.class, productId);
            } catch (EmptyResultDataAccessException e) {
                return null;
            }
        }
        return null;
    }

    @Transactional
    public void cancelItem(UUID itemId) {
        SubscriptionItemEntity item = repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription item: " + itemId));
        if (STATUS_CANCELLED.equals(item.getStatus())) {
            return;
        }
        item.setStatus(STATUS_CANCELLED);
        repository.updateStatus(itemId, STATUS_CANCELLED);
    }

    @Transactional
    public void updateQuantity(UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        SubscriptionItemEntity item = repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription item: " + itemId));
        item.setQuantity(quantity);
        repository.updateQuantityAndAmount(itemId, quantity, item.getUnitAmountMinor());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionItemEntity> listItems(UUID subscriptionId, boolean activeOnly) {
        List<SubscriptionItemEntity> items = repository.findBySubscriptionId(subscriptionId);
        if (!activeOnly) {
            return items;
        }
        return items.stream().filter(i -> STATUS_ACTIVE.equals(i.getStatus())).toList();
    }

    /**
     * Dual-compatible read of the plan version a subscription is contracted on:
     * prefers the ACTIVE PLAN item's pinned version, then the legacy
     * {@code tenant_subscriptions.plan_version_id} backfill column.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> effectivePlanVersionId(UUID subscriptionId) {
        Optional<SubscriptionItemEntity> planItem =
                repository.findActiveBySubscriptionIdAndType(subscriptionId, TYPE_PLAN);
        if (planItem.isPresent() && planItem.get().getPlanVersionId() != null) {
            return Optional.of(planItem.get().getPlanVersionId());
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT plan_version_id FROM tenant_subscriptions WHERE id = ?",
                    UUID.class, subscriptionId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
