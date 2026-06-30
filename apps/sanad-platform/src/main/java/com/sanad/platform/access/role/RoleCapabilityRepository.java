package com.sanad.platform.access.role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Stage 03A — Paginated, tenant- and role-scoped query.
     */
    @Query(
        value = "SELECT rc FROM RoleCapability rc WHERE rc.tenantId = :tenantId AND rc.roleId = :roleId",
        countQuery = "SELECT COUNT(rc) FROM RoleCapability rc WHERE rc.tenantId = :tenantId AND rc.roleId = :roleId")
    Page<RoleCapability> findByTenantIdAndRoleId(
        @Param("tenantId") UUID tenantId,
        @Param("roleId") UUID roleId,
        Pageable pageable);
}
