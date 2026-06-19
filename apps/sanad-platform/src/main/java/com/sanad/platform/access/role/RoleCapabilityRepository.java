package com.sanad.platform.access.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleCapabilityRepository extends JpaRepository<RoleCapability, UUID> {
    List<RoleCapability> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);
    Optional<RoleCapability> findByTenantIdAndRoleIdAndCapabilityId(
            UUID tenantId, UUID roleId, UUID capabilityId);
    boolean existsByTenantIdAndRoleIdAndCapabilityId(
            UUID tenantId, UUID roleId, UUID capabilityId);
    long deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}
