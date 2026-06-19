package com.sanad.platform.access.grant;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
