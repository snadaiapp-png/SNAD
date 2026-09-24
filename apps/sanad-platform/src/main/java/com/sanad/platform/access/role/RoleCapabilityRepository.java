package com.sanad.platform.access.role;

import com.sanad.platform.access.capability.AccessCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleCapabilityRepository extends JpaRepository<RoleCapability, UUID> {
    List<RoleCapability> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);
    Optional<RoleCapability> findByTenantIdAndRoleIdAndCapabilityId(
            UUID tenantId, UUID roleId, UUID capabilityId);
    boolean existsByTenantIdAndRoleIdAndCapabilityId(
            UUID tenantId, UUID roleId, UUID capabilityId);
    long deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /**
     * Returns distinct capability codes for the given role IDs within a tenant.
     * Used by /me endpoint to populate the capabilities array.
     */
    @Query("SELECT DISTINCT ac.code FROM RoleCapability rc " +
           "JOIN AccessCapability ac ON rc.capabilityId = ac.id " +
           "WHERE rc.tenantId = :tenantId AND rc.roleId IN :roleIds " +
           "AND ac.status = 'ACTIVE' " +
           "ORDER BY ac.code")
    List<String> findCapabilityCodesByTenantIdAndRoleIds(
            @Param("tenantId") UUID tenantId,
            @Param("roleIds") Set<UUID> roleIds);
}
