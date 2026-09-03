package com.sanad.platform.subscription.item;

import com.sanad.platform.subscription.plan.PlanVersionEntity;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

        // R0C-5 §9-B: adding a PLAN is legitimate ONLY when the plan is not
        // already ACTIVE on the subscription (distinct secondary plans are the
        // supported multi-plan model; the same plan twice is invalid — the
        // unique index is the final guard, this is the fail-closed front door).
        if (TYPE_PLAN.equals(itemType)) {
            if (repository.findActiveBySubscriptionIdAndPlanId(subscriptionId, planId).isPresent()) {
                throw new IllegalStateException(
                        "Subscription already has an ACTIVE PLAN item for plan " + planId
                                + "; duplicate active same-plan items are invalid");
            }
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
        // R0C-5 §9-D: the compatibility-ANCHORED plan item (ACTIVE PLAN whose
        // plan_id equals tenant_subscriptions.plan_id) can only be replaced
        // through the canonical plan-change authority — the generic item API
        // must fail closed. Cancelling a SECONDARY distinct plan stays allowed.
        if (TYPE_PLAN.equals(item.getItemType()) && STATUS_ACTIVE.equals(item.getStatus())) {
            UUID anchorPlanId = anchorPlanId(item.getSubscriptionId());
            if (anchorPlanId != null && anchorPlanId.equals(item.getPlanId())) {
                throw new IllegalStateException(
                        "Item " + itemId + " is the compatibility-anchored PLAN of subscription "
                                + item.getSubscriptionId()
                                + "; anchored plan replacement must use the canonical plan-change authority");
            }
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
        // R0C-5 §10: PLAN quantity IS the subscription seat count (billing
        // prices price x seat_quantity; composition changes carry seatQuantity
        // into the item). Mutating it through the generic item API would desync
        // the billing mirror — the sanctioned path is the seat-change engine.
        if (TYPE_PLAN.equals(item.getItemType())) {
            throw new IllegalStateException(
                    "PLAN item quantity mirrors the subscription seat count; use the seat-change"
                            + " path (billing-validated) instead of generic item quantity mutation");
        }
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
     * R0C-5 §7 — DETERMINISTIC dual-compatible read of the plan version the
     * subscription is contracted on:
     * <ol>
     *   <li>read the compatibility anchor {@code tenant_subscriptions.plan_id};</li>
     *   <li>find the ACTIVE PLAN item matching that plan;</li>
     *   <li>return its pinned {@code plan_version_id} when present;</li>
     *   <li>fall back to {@code tenant_subscriptions.plan_version_id} only for
     *       dual compatibility (legacy rows).</li>
     * </ol>
     * An arbitrary ACTIVE PLAN must never be chosen — distinct secondary
     * plans are valid and carry their own versions.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> effectivePlanVersionId(UUID subscriptionId) {
        UUID anchorPlanId;
        UUID anchorVersionId;
        try {
            Map<String, Object> anchorRow = jdbc.queryForMap(
                    "SELECT plan_id, plan_version_id FROM tenant_subscriptions WHERE id = ?",
                    subscriptionId);
            anchorPlanId = (UUID) anchorRow.get("plan_id");
            anchorVersionId = (UUID) anchorRow.get("plan_version_id");
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        if (anchorPlanId != null) {
            Optional<SubscriptionItemEntity> anchored = repository
                    .findActiveBySubscriptionIdAndPlanId(subscriptionId, anchorPlanId);
            if (anchored.isPresent() && anchored.get().getPlanVersionId() != null) {
                return Optional.of(anchored.get().getPlanVersionId());
            }
        }
        return Optional.ofNullable(anchorVersionId);
    }

    private UUID anchorPlanId(UUID subscriptionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT plan_id FROM tenant_subscriptions WHERE id = ?",
                    UUID.class, subscriptionId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
