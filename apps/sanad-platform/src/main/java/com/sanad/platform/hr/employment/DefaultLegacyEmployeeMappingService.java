package com.sanad.platform.hr.employment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Default implementation of {@link LegacyEmployeeMappingService}.
 *
 * <p>Classification rule (per HRM-G0 spec):
 * <ul>
 *   <li>Mapping row exists with classification AUTO_MIGRATE → AUTO_MIGRATE</li>
 *   <li>Mapping row exists with classification MIGRATION_REVIEW_REQUIRED → MIGRATION_REVIEW_REQUIRED</li>
 *   <li>Mapping row exists with classification MIGRATION_BLOCKED → MIGRATION_BLOCKED</li>
 *   <li>No mapping row exists → MIGRATION_BLOCKED (no authoritative match)</li>
 * </ul>
 * </p>
 *
 * <p>NEVER guesses by fuzzy name/email/employee_number similarity.
 * Authoritative means an unambiguous deterministic external_id or
 * prelinked UUID stored in hr_legacy_employee_mappings.</p>
 *
 * <p>This implementation reads the classification from
 * {@code hr_legacy_employee_mappings} — the table that Task 6 backfill
 * populates with deterministic, evidence-based classification rows.
 * Task 2 only establishes the read path; the write path (backfill)
 * is Task 6.</p>
 */
public final class DefaultLegacyEmployeeMappingService implements LegacyEmployeeMappingService {

    private final DataSource dataSource;

    public DefaultLegacyEmployeeMappingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public LegacyMappingClassification classify(UUID tenantId, UUID legacyEmployeeId) {
        if (tenantId == null || legacyEmployeeId == null) {
            return LegacyMappingClassification.MIGRATION_BLOCKED;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantContext(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT classification FROM hr_legacy_employee_mappings " +
                        "WHERE tenant_id = ? AND legacy_employee_id = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, legacyEmployeeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return LegacyMappingClassification.valueOf(rs.getString("classification"));
                        }
                        connection.commit();
                        return LegacyMappingClassification.MIGRATION_BLOCKED;
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                return LegacyMappingClassification.MIGRATION_BLOCKED;
            }
        } catch (SQLException e) {
            return LegacyMappingClassification.MIGRATION_BLOCKED;
        }
    }

    private void setTenantContext(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery();
        }
    }
}
