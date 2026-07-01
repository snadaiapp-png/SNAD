package com.sanad.platform.security.tenant.support;

import javax.sql.DataSource;

/**
 * Stage 04A.3.1 §3 — Test-only DataSource for fixture creation.
 *
 * <p>Uses a SEPARATE database account (TENANT_FIXTURE_DATABASE_USERNAME)
 * that is NOT subject to RLS. This account is the migration_owner or a
 * dedicated fixture account — it can INSERT/UPDATE/DELETE without RLS
 * restrictions, allowing test data setup before the actual RLS-verified
 * test runs.</p>
 *
 * <p>This DataSource is:</p>
 * <ul>
 *   <li>NOT @Primary</li>
 *   <li>NOT used by JPA Runtime</li>
 *   <li>NOT available in src/main</li>
 *   <li>NOT used inside test HTTP requests</li>
 * </ul>
 */
public interface TenantFixtureDataSource {

    /**
     * Returns the fixture DataSource (bypasses RLS).
     * Only for test data setup/cleanup — never for runtime queries.
     */
    DataSource getFixtureDataSource();
}
