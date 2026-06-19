package com.sanad.platform.access.role;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "role_capabilities", uniqueConstraints =
        @UniqueConstraint(name = "uk_role_capabilities",
                columnNames = {"tenant_id", "role_id", "capability_id"}))
@EntityListeners(AuditingEntityListener.class)
public class RoleCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @NotNull
    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @NotNull
    @Column(name = "capability_id", nullable = false, columnDefinition = "uuid")
    private UUID capabilityId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoleCapability() {
    }

    public RoleCapability(UUID tenantId, UUID roleId, UUID capabilityId) {
        this.tenantId = tenantId;
        this.roleId = roleId;
        this.capabilityId = capabilityId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRoleId() { return roleId; }
    public UUID getCapabilityId() { return capabilityId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RoleCapability mapping)) return false;
        if (id != null && mapping.id != null) return id.equals(mapping.id);
        return Objects.equals(tenantId, mapping.tenantId)
                && Objects.equals(roleId, mapping.roleId)
                && Objects.equals(capabilityId, mapping.capabilityId);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, roleId, capabilityId); }
}
