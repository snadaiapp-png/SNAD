package com.sanad.platform.subscription.item;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A billable line on a subscription ({@code subscription_items}).
 *
 * <p>A subscription is 0..N items; each item is a PLAN application plan, an
 * ADD_ON, a METERED product, or OTHER billable product. A subscription must
 * not assume a single product — e.g. one subscription may carry
 * ERP/Enterprise + HRM/Professional + AI/UsageBased + Extra Users.
 */
public final class SubscriptionItemEntity {
    private UUID id;
    private UUID tenantId;
    private UUID subscriptionId;
    private String itemType;            // PLAN | ADD_ON | METERED | OTHER
    private UUID applicationId;
    private UUID productId;
    private UUID planId;
    private UUID planVersionId;
    private String nameSnapshot;
    private int quantity;
    private Long unitAmountMinor;
    private String currencyCode;
    private String status;              // ACTIVE | PENDING | CANCELLED
    private Instant createdAt;
    private Instant updatedAt;

    public SubscriptionItemEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(UUID planVersionId) { this.planVersionId = planVersionId; }

    public String getNameSnapshot() { return nameSnapshot; }
    public void setNameSnapshot(String nameSnapshot) { this.nameSnapshot = nameSnapshot; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getUnitAmountMinor() { return unitAmountMinor; }
    public void setUnitAmountMinor(Long unitAmountMinor) { this.unitAmountMinor = unitAmountMinor; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubscriptionItemEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SubscriptionItemEntity{" +
                "id=" + id +
                ", subscriptionId=" + subscriptionId +
                ", itemType='" + itemType + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
