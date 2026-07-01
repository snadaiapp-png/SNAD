package com.sanad.platform.security.tenant.support;

import java.util.UUID;

/**
 * Stage 04A.3.4 §6 — Holds IDs of test fixtures created by the seeder.
 * Expanded to support full security chain testing.
 */
public record TenantTestFixture(
        UUID tenantAId,
        UUID tenantBId,
        UUID userAId,
        UUID userBId,
        String userAPasswordHash,
        String userBPasswordHash,
        UUID organizationAId,
        UUID organizationBId,
        UUID membershipAId,
        UUID membershipBId,
        UUID roleId,
        UUID capabilityId,
        UUID roleGrantId,

        // Security chain fixtures (§6)
        UUID suspendedUserId,
        UUID suspendedUserTenantId,
        UUID revokedMembershipUserId,
        UUID revokedMembershipTenantId,
        UUID revokedMembershipGrantId,
        UUID archivedTenantId,
        UUID archivedTenantUserId,
        UUID userWithoutCapabilityId,
        UUID userWithoutCapabilityTenantId,
        UUID roleBId,
        UUID roleBGrantId
) {
    /** Compact constructor for backward compatibility with older seeders. */
    public TenantTestFixture(UUID tenantAId, UUID tenantBId, UUID userAId, UUID userBId,
                              String userAPasswordHash, String userBPasswordHash,
                              UUID organizationAId, UUID organizationBId,
                              UUID membershipAId, UUID membershipBId,
                              UUID roleId, UUID capabilityId, UUID roleGrantId) {
        this(tenantAId, tenantBId, userAId, userBId,
             userAPasswordHash, userBPasswordHash,
             organizationAId, organizationBId,
             membershipAId, membershipBId,
             roleId, capabilityId, roleGrantId,
             null, null, null, null, null, null, null, null, null, null, null);
    }
}
