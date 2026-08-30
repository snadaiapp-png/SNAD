package com.sanad.platform.hr.structure.infrastructure;

import com.sanad.platform.hr.structure.domain.HrJob;
import com.sanad.platform.hr.structure.domain.HrJobVersion;
import com.sanad.platform.hr.structure.domain.HrOrgUnit;
import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.domain.HrPositionVersion;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of HR structure repository. Every operation sets
 * tenant context on the same connection used for the query (FORCE RLS).
 */
public final class JdbcHrStructureRepository {

    private final DataSource dataSource;

    public JdbcHrStructureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // --- Org Unit ---

    public void saveOrgUnit(HrOrgUnit orgUnit) {
        inTenantTransaction(orgUnit.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())")) {
                ps.setObject(1, orgUnit.id());
                ps.setObject(2, orgUnit.tenantId());
                ps.setObject(3, orgUnit.organizationId());
                ps.setString(4, orgUnit.stableCode());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Optional<HrOrgUnit> findOrgUnitById(UUID tenantId, UUID orgUnitId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, organization_id, stable_code FROM hr_org_units WHERE id = ?")) {
                ps.setObject(1, orgUnitId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapOrgUnit(rs)) : Optional.empty();
                }
            }
        });
    }

    public void saveOrgUnitVersion(HrOrgUnitVersion version) {
        inTenantTransaction(version.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_org_unit_versions " +
                    "(id, tenant_id, org_unit_id, name, code, unit_type, parent_org_unit_id, " +
                    "effective_from, effective_to, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, version.id());
                ps.setObject(2, version.tenantId());
                ps.setObject(3, version.orgUnitId());
                ps.setString(4, version.name());
                ps.setString(5, version.code());
                ps.setString(6, version.unitType());
                ps.setObject(7, version.parentOrgUnitId());
                ps.setObject(8, java.sql.Date.valueOf(version.effectiveFrom()));
                if (version.effectiveTo() != null) {
                    ps.setObject(9, java.sql.Date.valueOf(version.effectiveTo()));
                } else {
                    ps.setNull(9, Types.DATE);
                }
                ps.setString(10, version.status());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<HrOrgUnitVersion> orgUnitVersions(UUID tenantId, UUID orgUnitId) {
        return inTenantTransaction(tenantId, connection -> {
            List<HrOrgUnitVersion> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, org_unit_id, name, code, unit_type, parent_org_unit_id, " +
                    "effective_from, effective_to, status " +
                    "FROM hr_org_unit_versions WHERE org_unit_id = ? ORDER BY effective_from")) {
                ps.setObject(1, orgUnitId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapOrgUnitVersion(rs));
                }
            }
            return result;
        });
    }

    // --- Job ---

    public void saveJob(HrJob job) {
        inTenantTransaction(job.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())")) {
                ps.setObject(1, job.id());
                ps.setObject(2, job.tenantId());
                ps.setObject(3, job.organizationId());
                ps.setString(4, job.stableCode());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void saveJobVersion(HrJobVersion version) {
        inTenantTransaction(version.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_job_versions " +
                    "(id, tenant_id, job_id, title, description, grade, effective_from, effective_to, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, version.id());
                ps.setObject(2, version.tenantId());
                ps.setObject(3, version.jobId());
                ps.setString(4, version.title());
                ps.setString(5, version.description());
                ps.setString(6, version.grade());
                ps.setObject(7, java.sql.Date.valueOf(version.effectiveFrom()));
                if (version.effectiveTo() != null) {
                    ps.setObject(8, java.sql.Date.valueOf(version.effectiveTo()));
                } else {
                    ps.setNull(8, Types.DATE);
                }
                ps.setString(9, version.status());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<HrJobVersion> jobVersions(UUID tenantId, UUID jobId) {
        return inTenantTransaction(tenantId, connection -> {
            List<HrJobVersion> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, job_id, title, description, grade, " +
                    "effective_from, effective_to, status " +
                    "FROM hr_job_versions WHERE job_id = ? ORDER BY effective_from")) {
                ps.setObject(1, jobId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapJobVersion(rs));
                }
            }
            return result;
        });
    }

    // --- Position ---

    public void savePositionVersion(HrPositionVersion version) {
        inTenantTransaction(version.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_position_versions " +
                    "(id, tenant_id, position_id, organization_id, job_id, org_unit_id, " +
                    "title, effective_from, effective_to, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, version.id());
                ps.setObject(2, version.tenantId());
                ps.setObject(3, version.positionId());
                ps.setObject(4, version.organizationId());
                ps.setObject(5, version.jobId());
                ps.setObject(6, version.orgUnitId());
                ps.setString(7, version.title());
                ps.setObject(8, java.sql.Date.valueOf(version.effectiveFrom()));
                if (version.effectiveTo() != null) {
                    ps.setObject(9, java.sql.Date.valueOf(version.effectiveTo()));
                } else {
                    ps.setNull(9, Types.DATE);
                }
                ps.setString(10, version.status());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<HrPositionVersion> positionVersions(UUID tenantId, UUID positionId) {
        return inTenantTransaction(tenantId, connection -> {
            List<HrPositionVersion> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, position_id, organization_id, job_id, org_unit_id, " +
                    "title, effective_from, effective_to, status " +
                    "FROM hr_position_versions WHERE position_id = ? ORDER BY effective_from")) {
                ps.setObject(1, positionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapPositionVersion(rs));
                }
            }
            return result;
        });
    }

    /**
     * Close any existing open version (effective_to IS NULL) for an org unit
     * by setting effective_to to the day before the new effective_from.
     */
    public void closeOpenOrgUnitVersion(UUID tenantId, UUID orgUnitId, LocalDate newEffectiveFrom) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_org_unit_versions SET effective_to = ? " +
                    "WHERE tenant_id = ? AND org_unit_id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(newEffectiveFrom.minusDays(1)));
                ps.setObject(2, tenantId);
                ps.setObject(3, orgUnitId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Check if setting parentOrgUnitId as parent of orgUnitId creates a cycle
     * during the given effective period. Uses a recursive CTE constrained to
     * the candidate effective interval (period-aware).
     *
     * <p>Logic: traverse the parent chain starting from parentOrgUnitId.
     * If the chain reaches back to orgUnitId, a cycle exists.</p>
     */
    public boolean createsCycle(UUID tenantId, UUID orgUnitId, UUID parentOrgUnitId,
                                  LocalDate effectiveFrom, LocalDate effectiveTo) {
        return inTenantTransaction(tenantId, connection -> {
            String candidateEnd = effectiveTo != null
                    ? "'" + effectiveTo.plusDays(1) + "'::date"
                    : "'infinity'::date";
            String candidateRange = "daterange('" + effectiveFrom + "'::date, " + candidateEnd + ", '[)')";

            // Traverse from parentOrgUnitId upward through the parent chain.
            // If any ancestor in the chain (including parentOrgUnitId itself)
            // equals orgUnitId, we have a cycle.
            String sql = "WITH RECURSIVE chain AS (" +
                    "  SELECT org_unit_id, parent_org_unit_id " +
                    "  FROM hr_org_unit_versions " +
                    "  WHERE org_unit_id = ? " +
                    "    AND parent_org_unit_id IS NOT NULL" +
                    "    AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                    "  UNION ALL" +
                    "  SELECT v.org_unit_id, v.parent_org_unit_id " +
                    "  FROM hr_org_unit_versions v " +
                    "  JOIN chain c ON v.org_unit_id = c.parent_org_unit_id " +
                    "  WHERE v.parent_org_unit_id IS NOT NULL" +
                    "    AND daterange(v.effective_from, COALESCE(v.effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                    ") SELECT EXISTS(SELECT 1 FROM chain WHERE parent_org_unit_id = ?) AS has_cycle";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                // Start traversal from parentOrgUnitId (the proposed parent).
                ps.setObject(1, parentOrgUnitId);
                // Check if any node in the chain has orgUnitId as its parent
                // (meaning the chain leads back to orgUnitId).
                ps.setObject(2, orgUnitId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    // --- helpers ---

    private HrOrgUnit mapOrgUnit(ResultSet rs) throws SQLException {
        return new HrOrgUnit(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("stable_code"));
    }

    private HrOrgUnitVersion mapOrgUnitVersion(ResultSet rs) throws SQLException {
        return new HrOrgUnitVersion(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("unit_type"),
                rs.getObject("parent_org_unit_id", UUID.class),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getString("status"));
    }

    private HrJobVersion mapJobVersion(ResultSet rs) throws SQLException {
        return new HrJobVersion(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("grade"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getString("status"));
    }

    private HrPositionVersion mapPositionVersion(ResultSet rs) throws SQLException {
        return new HrPositionVersion(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("position_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class),
                rs.getString("title"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getString("status"));
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
                throw new IllegalStateException("HR structure operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire HR structure database connection", e);
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
