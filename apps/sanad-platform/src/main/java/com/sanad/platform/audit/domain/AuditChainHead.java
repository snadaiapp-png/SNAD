package com.sanad.platform.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stage 05A.1 §8 — Chain head pointer for a tenant's audit hash chain.
 *
 * <p>One row per tenant. Tracks the current {@code headSequence} and
 * {@code headHash}. The {@link com.sanad.platform.audit.service.AuditService}
 * locks this row with {@code SELECT ... FOR UPDATE} before appending a
 * new event, ensuring linear, concurrency-safe chain extension.</p>
 */
@Entity
@Table(name = "audit_chain_heads")
@EntityListeners(AuditingEntityListener.class)
public class AuditChainHead {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotNull
    @Column(name = "head_sequence", nullable = false)
    private Long headSequence = 0L;

    @Column(name = "head_hash", length = 64)
    private String headHash;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuditChainHead() {
    }

    public AuditChainHead(UUID tenantId) {
        this.tenantId = tenantId;
        this.headSequence = 0L;
    }

    public UUID getTenantId() { return tenantId; }
    public Long getHeadSequence() { return headSequence; }
    public String getHeadHash() { return headHash; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setHeadSequence(Long headSequence) { this.headSequence = headSequence; }
    public void setHeadHash(String headHash) { this.headHash = headHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditChainHead that)) return false;
        return Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }

    @Override
    public String toString() {
        return "AuditChainHead{tenantId=" + tenantId
                + ", headSequence=" + headSequence
                + ", headHash='" + headHash + "'}";
    }
}
