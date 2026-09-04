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
import java.util.Objects;
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
     * Atomically revise an Org Unit: validate cycle → close old version →
     * insert new version, all on ONE connection/transaction. If validation
     * fails, NOTHING is mutated (rollback).
     *
     * <p>This prevents the prevalidation-mutation defect where a rejected
     * cycle check left the previous version closed.</p>
     *
     * @return the new HrOrgUnitVersion
     * @throws IllegalStateException if a cycle is detected
     */
    public HrOrgUnitVersion reviseOrgUnitAtomically(
            UUID tenantId, UUID orgUnitId,
            LocalDate effectiveFrom,
            UUID parentOrgUnitId,
            String name, String code, String unitType) {
        return inTenantTransaction(tenantId, connection -> {
            String candidateRange = "daterange('" + effectiveFrom + "'::date, 'infinity'::date, '[)')";

            // 1. VALIDATE — period-aware cycle check BEFORE any mutation.
            if (parentOrgUnitId != null && !parentOrgUnitId.equals(orgUnitId)) {
                String cycleSql = "WITH RECURSIVE chain AS (" +
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

                try (PreparedStatement ps = connection.prepareStatement(cycleSql)) {
                    ps.setObject(1, parentOrgUnitId);
                    ps.setObject(2, orgUnitId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getBoolean(1)) {
                            throw new IllegalStateException(
                                "HRM_INVALID_STATE_TRANSITION: ORG_CYCLE: setting parent " + parentOrgUnitId +
                                " for org unit " + orgUnitId +
                                " creates a cycle during effective period from " + effectiveFrom);
                        }
                    }
                }
            }

            // 2. CLOSE — close any existing open version.
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_org_unit_versions SET effective_to = ? " +
                    "WHERE tenant_id = ? AND org_unit_id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(effectiveFrom.minusDays(1)));
                ps.setObject(2, tenantId);
                ps.setObject(3, orgUnitId);
                ps.executeUpdate();
            }

            // 3. INSERT — create the new open version.
            UUID versionId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_org_unit_versions " +
                    "(id, tenant_id, org_unit_id, name, code, unit_type, parent_org_unit_id, " +
                    "effective_from, effective_to, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)")) {
                ps.setObject(1, versionId);
                ps.setObject(2, tenantId);
                ps.setObject(3, orgUnitId);
                ps.setString(4, name);
                ps.setString(5, code);
                ps.setString(6, unitType);
                ps.setObject(7, parentOrgUnitId);
                ps.setObject(8, java.sql.Date.valueOf(effectiveFrom));
                ps.setString(9, "ACTIVE");
                ps.executeUpdate();
            }

            return new HrOrgUnitVersion(
                    versionId, tenantId, orgUnitId, name, code, unitType,
                    parentOrgUnitId, effectiveFrom, null, "ACTIVE");
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

    // ==================== WS5 Task 4 (Structure v2) ====================

    /**
     * Creates an Org Unit root row plus its initial open version atomically.
     * A proposed parent must have an effective version at {@code effectiveFrom};
     * a cycle check runs before any mutation.
     */
    public HrOrgUnitVersion createOrgUnitAtomically(UUID tenantId, UUID organizationId,
                                                    String stableCode, String name, String code,
                                                    String unitType, UUID parentOrgUnitId,
                                                    LocalDate effectiveFrom) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            if (parentOrgUnitId != null) {
                requireOrgUnitVersionEffective(connection, tenantId, parentOrgUnitId, effectiveFrom);
                if (createsCycleOnConnection(connection, orgUnitIdForParentProbe(tenantId, parentOrgUnitId),
                        parentOrgUnitId, effectiveFrom)) {
                    throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: ORG_CYCLE: parent " + parentOrgUnitId
                            + " would create a cycle from " + effectiveFrom);
                }
            }
            UUID unitId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())")) {
                ps.setObject(1, unitId);
                ps.setObject(2, tenantId);
                ps.setObject(3, organizationId);
                ps.setString(4, stableCode);
                ps.executeUpdate();
            }
            UUID versionId = UUID.randomUUID();
            insertOrgUnitVersion(connection, tenantId, unitId, name, code, unitType,
                    parentOrgUnitId, effectiveFrom);
            return new HrOrgUnitVersion(versionId, tenantId, unitId, name, code, unitType,
                    parentOrgUnitId, effectiveFrom, null, "ACTIVE");
        });
    }

    /** Open (unclosed) version per org unit, joined with the root row. */
    public List<HrOrgUnitVersion> openOrgUnitVersions(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            List<HrOrgUnitVersion> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT v.id, v.tenant_id, v.org_unit_id, v.name, v.code, v.unit_type, " +
                    "v.parent_org_unit_id, v.effective_from, v.effective_to, v.status " +
                    "FROM hr_org_unit_versions v WHERE v.tenant_id = ? AND v.effective_to IS NULL " +
                    "ORDER BY v.org_unit_id, v.effective_from")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapOrgUnitVersion(rs));
                }
            }
            return result;
        });
    }

    /** Jobs with their open version, joined for the directory surface. */
    public List<HrJobVersion> openJobVersions(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            List<HrJobVersion> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT v.id, v.tenant_id, v.job_id, v.title, v.description, v.grade, " +
                    "v.effective_from, v.effective_to, v.status " +
                    "FROM hr_job_versions v WHERE v.tenant_id = ? AND v.effective_to IS NULL " +
                    "ORDER BY v.job_id, v.effective_from")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapJobVersion(rs));
                }
            }
            return result;
        });
    }

    /** Positions (root staffability) joined with their open version. */
    public List<PositionWithStaffability> openPositionVersions(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            List<PositionWithStaffability> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT p.id AS position_id, p.status AS staffability, v.id AS version_id, " +
                    "v.tenant_id, v.position_id, v.organization_id, v.job_id, v.org_unit_id, " +
                    "v.title, v.effective_from, v.effective_to, v.status " +
                    "FROM hr_positions p LEFT JOIN hr_position_versions v " +
                    "ON v.position_id = p.id AND v.effective_to IS NULL " +
                    "WHERE p.tenant_id = ? ORDER BY p.id, v.effective_from")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapPositionWithStaffability(rs));
                }
            }
            return result;
        });
    }

    /**
     * Creates a Job root row plus its initial open version atomically.
     */
    public HrJobVersion createJobAtomically(UUID tenantId, UUID organizationId, String stableCode,
                                            String title, String description, String grade,
                                            LocalDate effectiveFrom) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            UUID jobId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_jobs (id, tenant_id, organization_id, stable_code, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())")) {
                ps.setObject(1, jobId);
                ps.setObject(2, tenantId);
                ps.setObject(3, organizationId);
                ps.setString(4, stableCode);
                ps.executeUpdate();
            }
            UUID versionId = UUID.randomUUID();
            insertJobVersion(connection, tenantId, jobId, title, description, grade, effectiveFrom);
            return new HrJobVersion(versionId, tenantId, jobId, title, description, grade,
                    effectiveFrom, null, "ACTIVE");
        });
    }

    /**
     * Effective-dated job revision: close the open version, insert the
     * successor atomically. No state changes on validation failure.
     */
    public HrJobVersion reviseJobAtomically(UUID tenantId, UUID jobId, String title,
                                            String description, String grade, LocalDate effectiveFrom) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            requireJobExists(connection, tenantId, jobId);
            closeOpenJobVersion(connection, tenantId, jobId, effectiveFrom);
            UUID versionId = UUID.randomUUID();
            insertJobVersion(connection, tenantId, jobId, title, description, grade, effectiveFrom);
            return new HrJobVersion(versionId, tenantId, jobId, title, description, grade,
                    effectiveFrom, null, "ACTIVE");
        });
    }

    /**
     * Creates a Position root row plus its initial open version atomically.
     * The referenced job (if any) must have an effective version.
     */
    public HrPositionVersion createPositionAtomically(UUID tenantId, String title, String code,
                                                      UUID jobId, UUID orgUnitId,
                                                      LocalDate effectiveFrom) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            if (jobId != null) {
                requireJobVersionEffective(connection, tenantId, jobId, effectiveFrom);
            }
            UUID positionId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_positions (id, tenant_id, title, code, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())")) {
                ps.setObject(1, positionId);
                ps.setObject(2, tenantId);
                ps.setString(3, title);
                ps.setString(4, code);
                ps.executeUpdate();
            }
            UUID versionId = UUID.randomUUID();
            insertPositionVersion(connection, tenantId, positionId, null, jobId, orgUnitId,
                    title, effectiveFrom);
            return new HrPositionVersion(versionId, tenantId, positionId, null, jobId, orgUnitId,
                    title, effectiveFrom, null, "ACTIVE");
        });
    }

    /**
     * Effective-dated position revision: close the open version, insert the
     * successor atomically. Staffability (root status) is untouched.
     */
    public HrPositionVersion revisePositionAtomically(UUID tenantId, UUID positionId, String title,
                                                      UUID jobId, UUID orgUnitId, LocalDate effectiveFrom) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            requirePositionExists(connection, tenantId, positionId);
            if (jobId != null) {
                requireJobVersionEffective(connection, tenantId, jobId, effectiveFrom);
            }
            HrPositionVersion current = openPositionVersion(connection, tenantId, positionId);
            String candidateTitle = title != null ? title : current.title();
            UUID candidateJobId = jobId != null ? jobId : current.jobId();
            UUID candidateOrgUnitId = orgUnitId != null ? orgUnitId : current.orgUnitId();
            closeOpenPositionVersion(connection, tenantId, positionId, effectiveFrom);
            UUID versionId = UUID.randomUUID();
            insertPositionVersion(connection, tenantId, positionId, current.organizationId(),
                    candidateJobId, candidateOrgUnitId, candidateTitle, effectiveFrom);
            return new HrPositionVersion(versionId, tenantId, positionId, current.organizationId(),
                    candidateJobId, candidateOrgUnitId, candidateTitle, effectiveFrom, null, "ACTIVE");
        });
    }

    /**
     * Freeze / close act on POSITION STAFFABILITY (the root row status), never
     * on occupancy and never on the effective-dated version history.
     * freeze → INACTIVE (temporarily not staffable, reversible via revise flow);
     * close → ARCHIVED (terminal). Illegal transitions are rejected.
     */
    public String setPositionStaffability(UUID tenantId, UUID positionId, String newStatus) {
        Objects.requireNonNull(tenantId, "tenantId");
        return inTenantTransaction(tenantId, connection -> {
            String current = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT status FROM hr_positions WHERE tenant_id = ? AND id = ? FOR UPDATE")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, positionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("HRM_POSITION_NOT_FOUND: " + positionId);
                    }
                    current = rs.getString(1);
                }
            }
            boolean allowed = ("INACTIVE".equals(newStatus) && "ACTIVE".equals(current))
                    || ("ARCHIVED".equals(newStatus) && ("ACTIVE".equals(current) || "INACTIVE".equals(current)));
            if (!allowed) {
                throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: position staffability "
                        + current + " cannot transition to " + newStatus);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_positions SET status = ?, updated_at = NOW() " +
                    "WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, newStatus);
                ps.setObject(2, tenantId);
                ps.setObject(3, positionId);
                ps.executeUpdate();
            }
            return newStatus;
        });
    }

    // ---------- structure v2 internal helpers ----------

    private UUID orgUnitIdForParentProbe(UUID tenantId, UUID parentOrgUnitId) {
        // The cycle traversal starts from the proposed parent; the new unit has
        // no history yet, so only the parent chain can form a cycle.
        return parentOrgUnitId;
    }

    private boolean createsCycleOnConnection(Connection connection, UUID startFrom, UUID target,
                                             LocalDate effectiveFrom) throws SQLException {
        String candidateRange = "daterange('" + effectiveFrom + "'::date, 'infinity'::date, '[)')";
        String sql = "WITH RECURSIVE chain AS (" +
                "  SELECT org_unit_id, parent_org_unit_id FROM hr_org_unit_versions " +
                "  WHERE org_unit_id = ? AND parent_org_unit_id IS NOT NULL " +
                "    AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                "  UNION ALL " +
                "  SELECT v.org_unit_id, v.parent_org_unit_id FROM hr_org_unit_versions v " +
                "  JOIN chain c ON v.org_unit_id = c.parent_org_unit_id " +
                "  WHERE v.parent_org_unit_id IS NOT NULL " +
                "    AND daterange(v.effective_from, COALESCE(v.effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                ") SELECT EXISTS(SELECT 1 FROM chain WHERE org_unit_id = ?) AS has_cycle";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, startFrom);
            ps.setObject(2, target);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void requireOrgUnitVersionEffective(Connection connection, UUID tenantId,
                                                UUID orgUnitId, LocalDate effectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_org_unit_versions WHERE org_unit_id = ? AND tenant_id = ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
            ps.setObject(1, orgUnitId);
            ps.setObject(2, tenantId);
            ps.setString(3, effectiveFrom.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_ORG_UNIT_NOT_FOUND: org unit " + orgUnitId
                            + " has no effective version for " + effectiveFrom);
                }
            }
        }
    }

    private void requireJobExists(Connection connection, UUID tenantId, UUID jobId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_jobs WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_JOB_NOT_FOUND: " + jobId);
                }
            }
        }
    }

    private void requireJobVersionEffective(Connection connection, UUID tenantId,
                                            UUID jobId, LocalDate effectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_job_versions WHERE job_id = ? AND tenant_id = ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
            ps.setObject(1, jobId);
            ps.setObject(2, tenantId);
            ps.setString(3, effectiveFrom.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_JOB_NOT_FOUND: job " + jobId
                            + " has no effective version for " + effectiveFrom);
                }
            }
        }
    }

    private void requirePositionExists(Connection connection, UUID tenantId, UUID positionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_positions WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, positionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_POSITION_NOT_FOUND: " + positionId);
                }
            }
        }
    }

    private void insertOrgUnitVersion(Connection connection, UUID tenantId, UUID orgUnitId,
                                      String name, String code, String unitType,
                                      UUID parentOrgUnitId, LocalDate effectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_org_unit_versions " +
                "(id, tenant_id, org_unit_id, name, code, unit_type, parent_org_unit_id, " +
                "effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, orgUnitId);
            ps.setString(4, name);
            ps.setString(5, code);
            ps.setString(6, unitType);
            ps.setObject(7, parentOrgUnitId);
            ps.setObject(8, java.sql.Date.valueOf(effectiveFrom));
            ps.executeUpdate();
        }
    }

    private void insertJobVersion(Connection connection, UUID tenantId, UUID jobId,
                                  String title, String description, String grade,
                                  LocalDate effectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_job_versions " +
                "(id, tenant_id, job_id, title, description, grade, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, jobId);
            ps.setString(4, title);
            ps.setString(5, description);
            ps.setString(6, grade);
            ps.setObject(7, java.sql.Date.valueOf(effectiveFrom));
            ps.executeUpdate();
        }
    }

    private void insertPositionVersion(Connection connection, UUID tenantId, UUID positionId,
                                       UUID organizationId, UUID jobId, UUID orgUnitId,
                                       String title, LocalDate effectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_position_versions " +
                "(id, tenant_id, position_id, organization_id, job_id, org_unit_id, " +
                "title, effective_from, effective_to, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, positionId);
            ps.setObject(4, organizationId);
            ps.setObject(5, jobId);
            ps.setObject(6, orgUnitId);
            ps.setString(7, title);
            ps.setObject(8, java.sql.Date.valueOf(effectiveFrom));
            ps.executeUpdate();
        }
    }

    private void closeOpenJobVersion(Connection connection, UUID tenantId, UUID jobId,
                                     LocalDate newEffectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_job_versions SET effective_to = ? " +
                "WHERE tenant_id = ? AND job_id = ? AND effective_to IS NULL")) {
            ps.setObject(1, java.sql.Date.valueOf(newEffectiveFrom.minusDays(1)));
            ps.setObject(2, tenantId);
            ps.setObject(3, jobId);
            ps.executeUpdate();
        }
    }

    private void closeOpenPositionVersion(Connection connection, UUID tenantId, UUID positionId,
                                          LocalDate newEffectiveFrom) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_position_versions SET effective_to = ? " +
                "WHERE tenant_id = ? AND position_id = ? AND effective_to IS NULL")) {
            ps.setObject(1, java.sql.Date.valueOf(newEffectiveFrom.minusDays(1)));
            ps.setObject(2, tenantId);
            ps.setObject(3, positionId);
            ps.executeUpdate();
        }
    }

    private HrPositionVersion openPositionVersion(Connection connection, UUID tenantId,
                                                  UUID positionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, position_id, organization_id, job_id, org_unit_id, " +
                "title, effective_from, effective_to, status FROM hr_position_versions " +
                "WHERE tenant_id = ? AND position_id = ? AND effective_to IS NULL")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, positionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_POSITION_NOT_FOUND: no open version for " + positionId);
                }
                return mapPositionVersion(rs);
            }
        }
    }

    /** Root staffability joined with the open effective version. */
    public record PositionWithStaffability(
            UUID positionId,
            String staffability,
            HrPositionVersion version) {
    }

    private PositionWithStaffability mapPositionWithStaffability(ResultSet rs) throws SQLException {
        UUID positionId = rs.getObject("position_id", UUID.class);
        String staffability = rs.getString("staffability");
        HrPositionVersion version = null;
        if (rs.getObject("version_id", UUID.class) != null) {
            version = new HrPositionVersion(
                    rs.getObject("version_id", UUID.class),
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
        return new PositionWithStaffability(positionId, staffability, version);
    }
}
