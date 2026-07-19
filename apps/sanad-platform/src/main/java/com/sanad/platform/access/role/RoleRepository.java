package com.sanad.platform.access.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByTenantIdOrderByCodeAsc(UUID tenantId);
    List<Role> findByTenantIdAndStatusOrderByCodeAsc(UUID tenantId, RoleStatus status);
    Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Batch lookup of multiple roles within a tenant by their IDs.
     *
     * <p>This exists to eliminate the N+1 query pattern in
     * {@code AuthController.buildMeResponse}, where role codes were previously
     * resolved one grant at a time via {@link #findByTenantIdAndId}. A single
     * batched call replaces N round trips.
     */
    List<Role> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
