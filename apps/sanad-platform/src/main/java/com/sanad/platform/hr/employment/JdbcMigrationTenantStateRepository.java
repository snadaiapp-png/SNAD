package com.sanad.platform.hr.employment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link MigrationTenantStateRepository}.
 *
 * <p>Every operation establishes {@code app.tenant_id} on the SAME
 * database connection used for the query.</p>
 */
public final class JdbcMigrationTenantStateRepository implements MigrationTenantStateRepository {

    private final DataSource dataSource;

    public JdbcMigrationTenantStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MigrationTenantState getState(UUID tenantId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT state FROM hr_migration_tenant_state WHERE tenant_id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return MigrationTenantState.valueOf(rs.getString("state"));
                    }
                    return MigrationTenantState.LEGACY;
                }
            }
        });
    }

    @Override
    public void setState(UUID tenantId, MigrationTenantState state) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at) " +
                    "VALUES (?, ?, NOW()) " +
                    "ON CONFLICT (tenant_id) DO UPDATE SET state = EXCLUDED.state, updated_at = NOW()")) {
                ps.setObject(1, tenantId);
                ps.setString(2, state.name());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<MigrationTenantStateRecord> findRecord(UUID tenantId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT tenant_id, state, updated_at, updated_by " +
                    "FROM hr_migration_tenant_state WHERE tenant_id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new MigrationTenantStateRecord(
                                rs.getObject("tenant_id", UUID.class),
                                MigrationTenantState.valueOf(rs.getString("state")),
                                rs.getTimestamp("updated_at").toInstant(),
                                rs.getObject("updated_by", UUID.class)));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    private <T> T inTenantTransaction(UUID tenantId, SqlWork<T> work) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantContext(connection, tenantId);
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rb) { e.addSuppressed(rb); }
                if (e instanceof RuntimeException re) throw re;
                throw new IllegalStateException("Migration state operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire database connection", e);
        }
    }

    private void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
