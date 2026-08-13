package com.sanad.platform.module.registry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a per-plan module entitlement override.
 *
 * <p>Links {@code saas_plans} to {@code modules} with specific capability/limit/quota
 * values. When a plan is assigned to a tenant via a subscription, the
 * {@code EntitlementResolver} reads these rows to compute the effective
 * entitlements for that tenant.
 *
 * <p>This entity does NOT replace the existing {@code saas_plan_entitlements}
 * table (V19) — it adds module-scoped entitlements alongside it.
 */
public final class PlanModuleEntitlementEntity {
    private UUID id;
    private UUID planId;
    private UUID moduleId;
    private boolean moduleEnabled;
    private String capabilityCode;     // nullable: if NULL, this row only controls moduleEnabled
    private String capabilityValue;    // string value (parsed by resolver based on type)
    private Long limitValue;            // for NUMERIC_LIMIT
    private Long quotaValue;            // for QUOTA
    private String quotaPeriod;        // DAILY | MONTHLY | YEARLY | TOTAL
    private Instant effectiveAt;
    private Instant createdAt;
    private Instant updatedAt;

    public PlanModuleEntitlementEntity() {
    }

    public PlanModuleEntitlementEntity(UUID id, UUID planId, UUID moduleId, boolean moduleEnabled,
                                       String capabilityCode, String capabilityValue,
                                       Long limitValue, Long quotaValue, String quotaPeriod,
                                       Instant effectiveAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.planId = planId;
        this.moduleId = moduleId;
        this.moduleEnabled = moduleEnabled;
        this.capabilityCode = capabilityCode;
        this.capabilityValue = capabilityValue;
        this.limitValue = limitValue;
        this.quotaValue = quotaValue;
        this.quotaPeriod = quotaPeriod;
        this.effectiveAt = effectiveAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }

    public boolean isModuleEnabled() { return moduleEnabled; }
    public void setModuleEnabled(boolean moduleEnabled) { this.moduleEnabled = moduleEnabled; }

    public String getCapabilityCode() { return capabilityCode; }
    public void setCapabilityCode(String capabilityCode) { this.capabilityCode = capabilityCode; }

    public String getCapabilityValue() { return capabilityValue; }
    public void setCapabilityValue(String capabilityValue) { this.capabilityValue = capabilityValue; }

    public Long getLimitValue() { return limitValue; }
    public void setLimitValue(Long limitValue) { this.limitValue = limitValue; }

    public Long getQuotaValue() { return quotaValue; }
    public void setQuotaValue(Long quotaValue) { this.quotaValue = quotaValue; }

    public String getQuotaPeriod() { return quotaPeriod; }
    public void setQuotaPeriod(String quotaPeriod) { this.quotaPeriod = quotaPeriod; }

    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanModuleEntitlementEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
