package com.sanad.platform.security.tenant.support;

import java.util.UUID;

/**
 * Stage 04A.3.1 §4 — Holds IDs of test fixtures created by the seeder.
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
        UUID roleGrantId
) {}
