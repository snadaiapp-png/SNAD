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
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByTenantIdOrderByCodeAsc(UUID tenantId);
    List<Role> findByTenantIdAndStatusOrderByCodeAsc(UUID tenantId, RoleStatus status);
    Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Stage 03A — Paginated, tenant-scoped role query.
     */
    @Query(
        value = "SELECT r FROM Role r WHERE r.tenantId = :tenantId",
        countQuery = "SELECT COUNT(r) FROM Role r WHERE r.tenantId = :tenantId")
    Page<Role> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);
}
