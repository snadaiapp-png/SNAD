package com.sanad.platform.access.grant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_role_scope",
                columnNames = {"tenant_id", "user_id", "role_id", "organization_id"}))
@EntityListeners(AuditingEntityListener.class)
public class UserRoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotNull
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @NotNull
    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "organization_id", columnDefinition = "uuid")
    private UUID organizationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserGrantStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRoleGrant() {
    }

    public UserRoleGrant(UUID tenantId, UUID userId, UUID roleId, UUID organizationId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.roleId = roleId;
        this.organizationId = organizationId;
        this.status = UserGrantStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getRoleId() { return roleId; }
    public UUID getOrganizationId() { return organizationId; }
    public UserGrantStatus getStatus() { return status; }
    public void setStatus(UserGrantStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isTenantWide() { return organizationId == null; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserRoleGrant grant)) return false;
        if (id != null && grant.id != null) return id.equals(grant.id);
        return Objects.equals(tenantId, grant.tenantId)
                && Objects.equals(userId, grant.userId)
                && Objects.equals(roleId, grant.roleId)
                && Objects.equals(organizationId, grant.organizationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId, roleId, organizationId);
    }
}
