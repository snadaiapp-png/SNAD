package com.sanad.platform.hr.payroll.infrastructure;

import com.sanad.platform.hr.payroll.application.PayrollLifecycle;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleRepository;
import com.sanad.platform.hr.payroll.application.PayrollLifecycleRepository.RunState;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPayrollLifecycleRepository implements PayrollLifecycleRepository {

    @Override
    public Optional<RunState> loadForUpdate(Connection connection, UUID tenantId, UUID runId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, tenant_id, legal_entity_id, status, version
                  FROM hr_payroll_runs
                 WHERE tenant_id = ?
                   AND id = ?
                 FOR UPDATE
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RunState(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("tenant_id")),
                        UUID.fromString(rs.getString("legal_entity_id")),
                        PayrollLifecycle.valueOf(rs.getString("status")),
                        rs.getLong("version")));
            }
        }
    }

    @Override
    public RunState transition(
            Connection connection,
            RunState current,
            PayrollLifecycle target,
            UUID actorUserId) throws SQLException {

        String sql = """
                UPDATE hr_payroll_runs
                   SET status = ?,
                       version = version + 1,
                       reviewed_by = CASE WHEN ? = 'REVIEWED' THEN ? ELSE reviewed_by END,
                       reviewed_at = CASE WHEN ? = 'REVIEWED' THEN NOW() ELSE reviewed_at END,
                       approved_by = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_by END,
                       approved_at = CASE WHEN ? = 'APPROVED' THEN NOW() ELSE approved_at END,
                       exported_at = CASE WHEN ? = 'EXPORTED' THEN NOW() ELSE exported_at END,
                       updated_at = NOW()
                 WHERE id = ?
                   AND tenant_id = ?
                   AND status = ?
                   AND version = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, target.name());

            ps.setString(2, target.name());
            setNullableUuid(ps, 3, actorUserId);
            ps.setString(4, target.name());

            ps.setString(5, target.name());
            setNullableUuid(ps, 6, actorUserId);
            ps.setString(7, target.name());

            ps.setString(8, target.name());

            ps.setObject(9, current.id());
            ps.setObject(10, current.tenantId());
            ps.setString(11, current.status().name());
            ps.setLong(12, current.version());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_VERSION_CONFLICT: payroll run changed concurrently");
            }
        }

        return new RunState(
                current.id(),
                current.tenantId(),
                current.legalEntityId(),
                target,
                current.version() + 1);
    }

    @Override
    public RunState recalculate(
            Connection connection,
            RunState current) throws SQLException {

        String sql = """
                UPDATE hr_payroll_runs
                   SET version = version + 1,
                       updated_at = NOW()
                 WHERE id = ?
                   AND tenant_id = ?
                   AND status = 'CALCULATED'
                   AND version = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, current.id());
            ps.setObject(2, current.tenantId());
            ps.setLong(3, current.version());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_VERSION_CONFLICT: payroll run changed concurrently");
            }
        }

        return new RunState(
                current.id(),
                current.tenantId(),
                current.legalEntityId(),
                PayrollLifecycle.CALCULATED,
                current.version() + 1);
    }

    private void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

}
