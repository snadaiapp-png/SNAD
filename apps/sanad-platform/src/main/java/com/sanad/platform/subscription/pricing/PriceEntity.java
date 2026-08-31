package com.sanad.platform.subscription.pricing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A price definition ({@code prices}) attached either to a plan version or to
 * a product (add-on / metered). Country- and currency-customizable, with a
 * billing interval, base/unit amounts, optional tiers and clamps.
 */
public final class PriceEntity {
    private UUID id;
    private UUID planVersionId;
    private UUID productId;
    private String priceModel;
    private String countryCode;         // ISO 3166-1 alpha-2 or GLOBAL
    private String currencyCode;        // ISO 4217
    private String billingInterval;     // MONTHLY | ANNUAL | ONE_TIME
    private long baseAmountMinor;
    private Long unitAmountMinor;
    private String tiersJson;
    private Long minAmountMinor;
    private Long maxAmountMinor;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Instant createdAt;
    private Instant updatedAt;

    public PriceEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(UUID planVersionId) { this.planVersionId = planVersionId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getPriceModel() { return priceModel; }
    public void setPriceModel(String priceModel) { this.priceModel = priceModel; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getBillingInterval() { return billingInterval; }
    public void setBillingInterval(String billingInterval) { this.billingInterval = billingInterval; }

    public long getBaseAmountMinor() { return baseAmountMinor; }
    public void setBaseAmountMinor(long baseAmountMinor) { this.baseAmountMinor = baseAmountMinor; }

    public Long getUnitAmountMinor() { return unitAmountMinor; }
    public void setUnitAmountMinor(Long unitAmountMinor) { this.unitAmountMinor = unitAmountMinor; }

    public String getTiersJson() { return tiersJson; }
    public void setTiersJson(String tiersJson) { this.tiersJson = tiersJson; }

    public Long getMinAmountMinor() { return minAmountMinor; }
    public void setMinAmountMinor(Long minAmountMinor) { this.minAmountMinor = minAmountMinor; }

    public Long getMaxAmountMinor() { return maxAmountMinor; }
    public void setMaxAmountMinor(Long maxAmountMinor) { this.maxAmountMinor = maxAmountMinor; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriceEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
