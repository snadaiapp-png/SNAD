package com.sanad.platform.subscription.plan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A versioned contract of a SaaS plan ({@code plan_versions}).
 *
 * <p>Editing a plan must never silently change what existing subscribers
 * contracted: subscribers are pinned to the version recorded on their
 * subscription/subscription item until an explicit change, renewal, or
 * migration. Versions follow the DRAFT → ACTIVE → RETIRED lifecycle; at most
 * one version per plan is ACTIVE (partial unique index).
 */
public final class PlanVersionEntity {
    private UUID id;
    private UUID planId;
    private int versionNumber;
    private String status;              // DRAFT | ACTIVE | RETIRED
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private String currencyCode;
    private long monthlyPriceMinor;
    private long annualPriceMinor;
    private int trialDays;
    private int maxUsers;
    private int maxOrganizations;
    private long storageMb;
    private Instant createdAt;
    private Instant updatedAt;

    public PlanVersionEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Instant getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public long getMonthlyPriceMinor() { return monthlyPriceMinor; }
    public void setMonthlyPriceMinor(long monthlyPriceMinor) { this.monthlyPriceMinor = monthlyPriceMinor; }

    public long getAnnualPriceMinor() { return annualPriceMinor; }
    public void setAnnualPriceMinor(long annualPriceMinor) { this.annualPriceMinor = annualPriceMinor; }

    public int getTrialDays() { return trialDays; }
    public void setTrialDays(int trialDays) { this.trialDays = trialDays; }

    public int getMaxUsers() { return maxUsers; }
    public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

    public int getMaxOrganizations() { return maxOrganizations; }
    public void setMaxOrganizations(int maxOrganizations) { this.maxOrganizations = maxOrganizations; }

    public long getStorageMb() { return storageMb; }
    public void setStorageMb(long storageMb) { this.storageMb = storageMb; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanVersionEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PlanVersionEntity{" +
                "id=" + id +
                ", planId=" + planId +
                ", versionNumber=" + versionNumber +
                ", status='" + status + '\'' +
                '}';
    }
}
