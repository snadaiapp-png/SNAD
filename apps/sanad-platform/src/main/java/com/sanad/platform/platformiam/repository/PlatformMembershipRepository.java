package com.sanad.platform.platformiam.repository;

import com.sanad.platform.platformiam.domain.PlatformMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped persistence boundary for Platform IAM memberships.
 */
public interface PlatformMembershipRepository {

    Optional<PlatformMembership> findByControlTenantIdAndUserId(UUID controlTenantId, UUID userId);

    List<PlatformMembership> findByControlTenantId(UUID controlTenantId);

    PlatformMembership save(PlatformMembership membership);

    /**
     * Locks ACTIVE membership rows whose users currently hold an ACTIVE assignment
     * to an ACTIVE role with the requested code. The lock is used by owner-safety
     * invariants; this repository does not make authorization decisions.
     */
    List<PlatformMembership> lockActiveMembershipsByRoleCode(UUID controlTenantId, String roleCode);
}
