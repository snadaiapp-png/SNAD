package com.sanad.platform.security.tenant.support;

import java.util.UUID;

/**
 * Stage 04A.3.1 §4 — Interface for creating test fixtures using a
 * SEPARATE Fixture DataSource that bypasses RLS.
 */
public interface TenantFixtureSeeder {

    /**
     * Seeds two tenants, two users (one per tenant), with password hashes.
     * Also seeds roles, capabilities, and role grants for User A
     * (USER.READ, USER.WRITE in Tenant A only).
     * Used by TenantCrudIsolationIntegrationTest and others requiring
     * full authentication + authorization.
     */
    TenantTestFixture seedCrudFixture();

    /**
     * Seeds two tenants with 3 and 2 organizations respectively.
     * Used by TenantAwarePaginationIntegrationTest.
     */
    TenantTestFixture seedPaginationFixture();

    /**
     * Seeds auth chain fixtures: active user, suspended user, archived tenant.
     * Used by TenantAuthenticationUnderRlsIntegrationTest.
     */
    TenantTestFixture seedAuthFixture();

    /**
     * Seeds capability fixtures: user with USER.READ in Tenant A only.
     * Used by TenantCapabilityBindingIntegrationTest.
     */
    TenantTestFixture seedCapabilityFixture();

    /**
     * Seeds session fixtures: user with known session version.
     * Used by TenantSessionBindingIntegrationTest.
     */
    TenantTestFixture seedSessionFixture();

    /**
     * Increments the session version for a user (simulates logout/password change).
     */
    void incrementSessionVersion(UUID tenantId, UUID userId);

    /**
     * Revokes a role grant (simulates role removal).
     */
    void revokeRoleGrant(UUID tenantId, UUID grantId);

    /**
     * Cleans up all fixtures created by the seeder.
     * Uses the Fixture DataSource (bypasses RLS).
     */
    void cleanup(TenantTestFixture fixture);
}
