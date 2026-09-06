package com.sanad.platform.hr.employment;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped HR migration state repository — reads/persists
 * {@link MigrationTenantState} for each tenant.
 *
 * <p>Acts as the gate for WS5 cutover authorization.</p>
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC implementation.</p>
 */
public interface MigrationTenantStateRepository {

    /** Get the current migration state for a tenant (default LEGACY if not set). */
    MigrationTenantState getState(UUID tenantId);

    /** Set the migration state for a tenant. Idempotent. */
    void setState(UUID tenantId, MigrationTenantState state);

    /** Optional explicit row for audit reasons. */
    Optional<MigrationTenantStateRecord> findRecord(UUID tenantId);
}
