package com.sanad.platform.access.grant;

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
public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, UUID> {
    List<UserRoleGrant> findByTenantIdAndUserIdOrderByCreatedAtAsc(UUID tenantId, UUID userId);
    List<UserRoleGrant> findByTenantIdAndUserIdAndStatus(
            UUID tenantId, UUID userId, UserGrantStatus status);
    Optional<UserRoleGrant> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<UserRoleGrant> findByTenantIdAndUserIdAndRoleIdAndOrganizationId(
            UUID tenantId, UUID userId, UUID roleId, UUID organizationId);
    Optional<UserRoleGrant> findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
            UUID tenantId, UUID userId, UUID roleId);

    /**
     * Stage 03A — Paginated, tenant- and user-scoped query.
     */
    @Query(
        value = "SELECT g FROM UserRoleGrant g WHERE g.tenantId = :tenantId AND g.userId = :userId",
        countQuery = "SELECT COUNT(g) FROM UserRoleGrant g WHERE g.tenantId = :tenantId AND g.userId = :userId")
    Page<UserRoleGrant> findByTenantIdAndUserId(
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId,
        Pageable pageable);
}
