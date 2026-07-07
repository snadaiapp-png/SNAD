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
    List<Role> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
