package com.sanad.platform.access.override;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical data model for {@code user_permission_overrides} (Wave 1 Task 1
 * schema, FORCE RLS). DENY rows are capability-wide by DB CHECK; ALLOW rows
 * may carry a data scope. Persistence is JDBC (PostgreSQL Direct) through
 * {@link UserPermissionOverrideRepository}.
 */
@Entity
@Table(name = "user_permission_overrides")
@EntityListeners(AuditingEntityListener.class)
public class UserPermissionOverride {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "capability_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID capabilityId;

    @Column(nullable = false, length = 10)
    private String effect;

    @Column(name = "scope_type", length = 20)
    private String scopeType;

    @Column(name = "scope_reference", columnDefinition = "uuid")
    private UUID scopeReference;

    @Column(nullable = false)
    private String reason;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private int version;

    protected UserPermissionOverride() {
    }

    public UserPermissionOverride(
            UUID id, UUID tenantId, UUID userId, UUID capabilityId, String effect,
            String scopeType, UUID scopeReference, String reason,
            Instant validFrom, Instant validUntil, UUID createdBy, int version) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.capabilityId = capabilityId;
        this.effect = effect;
        this.scopeType = scopeType;
        this.scopeReference = scopeReference;
        this.reason = reason;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.createdBy = createdBy;
        this.version = version;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getCapabilityId() { return capabilityId; }
    public String getEffect() { return effect; }
    public String getScopeType() { return scopeType; }
    public UUID getScopeReference() { return scopeReference; }
    public String getReason() { return reason; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
