package com.sanad.platform.security.tenant.support;

/**
 * Stage 04A.3.1 §4 — Interface for creating test fixtures using a
 * SEPARATE Fixture DataSource that bypasses RLS.
 */
public interface TenantFixtureSeeder {

    /**
     * Seeds two tenants, two users (one per tenant), with password hashes.
     * Used by TenantCrudIsolationIntegrationTest.
     */
    TenantTestFixture seedCrudFixture();

    /**
     * Seeds two tenants with 3 and 2 organizations respectively.
     * Used by TenantAwarePaginationIntegrationTest.
     */
    TenantTestFixture seedPaginationFixture();

    /**
     * Cleans up all fixtures created by the seeder.
     * Uses the Fixture DataSource (bypasses RLS).
     */
    void cleanup(TenantTestFixture fixture);
}
