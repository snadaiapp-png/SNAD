package com.sanad.platform.hr.employment;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link MigrationTenantStateRepository}.
 *
 * <p>Task 2 RED skeleton: methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC persistence against hr_migration_tenant_state.</p>
 */
public final class JdbcMigrationTenantStateRepository implements MigrationTenantStateRepository {

    private final DataSource dataSource;

    public JdbcMigrationTenantStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MigrationTenantState getState(UUID tenantId) {
        throw new UnsupportedOperationException(
                "JdbcMigrationTenantStateRepository.getState — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public void setState(UUID tenantId, MigrationTenantState state) {
        throw new UnsupportedOperationException(
                "JdbcMigrationTenantStateRepository.setState — Task 2 RED skeleton, implement in GREEN");
    }

    @Override
    public Optional<MigrationTenantStateRecord> findRecord(UUID tenantId) {
        throw new UnsupportedOperationException(
                "JdbcMigrationTenantStateRepository.findRecord — Task 2 RED skeleton, implement in GREEN");
    }
}
