package com.sanad.platform.scale.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Stage 08 Sprint 1 — ST8-S1-002 Tenant Quota Model.
 *
 * Per-tenant quota for a single dimension (API_RPM, AI_TOKENS_DAY, etc.).
 * Enforced at API gateway and AI inference layer.
 *
 * Security: tenant-scoped. No cross-tenant reads permitted.
 * Audit: every quota change audited via {@code QuotaAuditRecord}.
 */
@Entity
@Table(name = "tenant_quota",
       indexes = {
           @Index(name = "idx_tenant_quota_tenant_dim", columnList = "tenant_id,dimension", unique = true),
           @Index(name = "idx_tenant_quota_reset_at", columnList = "reset_at")
       })
public class TenantQuota {

    public enum Dimension {
        API_RPM,
        API_RPD,
        AI_TOKENS_DAY,
        AI_TOKENS_MONTH,
        STORAGE_GB,
        WEBHOOKS_DAY,
        JOBS_DAY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 32)
    private Dimension dimension;

    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    @Column(name = "used_value", nullable = false)
    private long usedValue;

    @Column(name = "reset_at", nullable = false)
    private Instant resetAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TenantQuota() {
    }

    public TenantQuota(String tenantId, Dimension dimension, long limitValue, Instant resetAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (limitValue < 0) {
            throw new IllegalArgumentException("limitValue must be non-negative");
        }
        this.limitValue = limitValue;
        this.usedValue = 0L;
        this.resetAt = Objects.requireNonNull(resetAt, "resetAt");
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isExceeded() {
        return usedValue >= limitValue;
    }

    public long remaining() {
        return Math.max(0L, limitValue - usedValue);
    }

    public void incrementUsed(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("delta must be non-negative");
        }
        this.usedValue += delta;
        this.updatedAt = Instant.now();
    }

    public void reset() {
        this.usedValue = 0L;
        this.resetAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void updateLimit(long newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("newLimit must be non-negative");
        }
        this.limitValue = newLimit;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Dimension getDimension() { return dimension; }
    public void setDimension(Dimension dimension) { this.dimension = dimension; }

    public long getLimitValue() { return limitValue; }
    public void setLimitValue(long limitValue) { this.limitValue = limitValue; }

    public long getUsedValue() { return usedValue; }
    public void setUsedValue(long usedValue) { this.usedValue = usedValue; }

    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantQuota that)) return false;
        return Objects.equals(tenantId, that.tenantId) && dimension == that.dimension;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, dimension);
    }
}
