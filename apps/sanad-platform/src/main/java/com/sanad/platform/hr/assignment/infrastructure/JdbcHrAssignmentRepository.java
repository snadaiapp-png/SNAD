package com.sanad.platform.hr.assignment.infrastructure;

import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;

import javax.sql.DataSource;
import java.math.BigDecimal;
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

public final class JdbcHrAssignmentRepository {

    private final DataSource dataSource;

    public JdbcHrAssignmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveAssignment(HrAssignment a) {
        inTenantTransaction(a.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employee_assignments " +
                    "(id, tenant_id, employment_id, organization_id, org_unit_id, position_id, " +
                    "reports_to_assignment_id, work_location_id, cost_center_id, " +
                    "assignment_type, occupancy_mode, allocation_percent, " +
                    "effective_from, effective_to, status, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                setAssignmentParams(ps, a);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Optional<HrAssignment> findAssignmentById(UUID tenantId, UUID assignmentId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM hr_employee_assignments WHERE id = ?")) {
                ps.setObject(1, assignmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapAssignment(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<HrAssignment> assignmentsForEmployment(UUID tenantId, UUID employmentId) {
        return inTenantTransaction(tenantId, connection -> {
            List<HrAssignment> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM hr_employee_assignments WHERE employment_id = ? ORDER BY effective_from")) {
                ps.setObject(1, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapAssignment(rs));
                }
            }
            return result;
        });
    }

    /**
     * Period-aware reporting cycle check. Traverses the reporting chain
     * from the proposed manager and checks if the assignment being revised
     * appears in the chain during the candidate effective period.
     */
    public boolean createsReportingCycle(UUID tenantId, UUID assignmentId,
                                           UUID reportsToAssignmentId,
                                           LocalDate effectiveFrom, LocalDate effectiveTo) {
        return inTenantTransaction(tenantId, connection -> {
            String candidateEnd = effectiveTo != null
                    ? "'" + effectiveTo.plusDays(1) + "'::date" : "'infinity'::date";
            String candidateRange = "daterange('" + effectiveFrom + "'::date, " + candidateEnd + ", '[)')";

            String sql = "WITH RECURSIVE chain AS (" +
                    "  SELECT id, reports_to_assignment_id FROM hr_employee_assignments " +
                    "  WHERE id = ? AND reports_to_assignment_id IS NOT NULL" +
                    "    AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                    "  UNION ALL" +
                    "  SELECT a.id, a.reports_to_assignment_id FROM hr_employee_assignments a" +
                    "  JOIN chain c ON a.id = c.reports_to_assignment_id" +
                    "  WHERE a.reports_to_assignment_id IS NOT NULL" +
                    "    AND daterange(a.effective_from, COALESCE(a.effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                    ") SELECT EXISTS(SELECT 1 FROM chain WHERE reports_to_assignment_id = ?) AS has_cycle";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, reportsToAssignmentId);
                ps.setObject(2, assignmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    /**
     * Atomically create an assignment: validate all invariants → INSERT, on ONE connection.
     */
    public HrAssignment createAssignmentAtomically(
            UUID tenantId, UUID employmentId, UUID organizationId,
            UUID orgUnitId, UUID positionId, UUID reportsToAssignmentId,
            UUID workLocationId, UUID costCenterId,
            AssignmentType assignmentType, OccupancyMode occupancyMode,
            BigDecimal allocationPercent,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        return inTenantTransaction(tenantId, connection -> {
            // 1. Validate allocation range
            if (allocationPercent == null || allocationPercent.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("allocation_percent must be > 0");
            }
            if (allocationPercent.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("allocation_percent must be <= 100");
            }

            // 2. Validate employment exists and get legal_entity_id
            UUID legalEntityId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT legal_entity_id FROM hr_employees WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Employment not found: " + employmentId);
                    legalEntityId = rs.getObject("legal_entity_id", UUID.class);
                    if (legalEntityId == null) throw new IllegalStateException("Employment has no legal_entity_id");
                }
            }

            // 3. Validate Legal Entity ↔ Organization eligibility
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM organization_legal_entities " +
                    "WHERE tenant_id = ? AND organization_id = ? AND legal_entity_id = ? " +
                    "AND status = 'ACTIVE' " +
                    "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, organizationId);
                ps.setObject(3, legalEntityId);
                ps.setString(4, effectiveFrom.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException(
                            "Organization " + organizationId + " is not eligible for Legal Entity " + legalEntityId);
                    }
                }
            }

            // 4. Validate Org Unit effectiveness (if set)
            if (orgUnitId != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM hr_org_unit_versions " +
                        "WHERE org_unit_id = ? AND tenant_id = ? " +
                        "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
                    ps.setObject(1, orgUnitId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, effectiveFrom.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Org Unit " + orgUnitId + " has no effective version for " + effectiveFrom);
                    }
                }
            }

            // 5. Validate Position effectiveness (if set)
            if (positionId != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM hr_position_versions " +
                        "WHERE position_id = ? AND tenant_id = ? " +
                        "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
                    ps.setObject(1, positionId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, effectiveFrom.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Position " + positionId + " has no effective version for " + effectiveFrom);
                    }
                }
            }

            // 6. Validate total effective allocation (period-aware)
            String candidateRange = "daterange('" + effectiveFrom + "'::date, " +
                    (effectiveTo != null ? "'" + effectiveTo.plusDays(1) + "'::date" : "'infinity'::date") + ", '[)')";
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COALESCE(SUM(allocation_percent), 0) FROM hr_employee_assignments " +
                    "WHERE tenant_id = ? AND employment_id = ? AND status = 'ACTIVE' " +
                    "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') && " + candidateRange)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    BigDecimal currentTotal = rs.getBigDecimal(1);
                    if (currentTotal == null) currentTotal = BigDecimal.ZERO;
                    if (currentTotal.add(allocationPercent).compareTo(new BigDecimal("100")) > 0) {
                        throw new IllegalStateException(
                            "Total effective allocation would exceed 100%: current=" + currentTotal + " + candidate=" + allocationPercent);
                    }
                }
            }

            // 7. Validate reporting (self-report + cycle)
            if (reportsToAssignmentId != null) {
                // Self-report check
                // (The assignment doesn't have an ID yet, so we check if
                // the proposed manager IS the assignment we're creating —
                // but that's not possible pre-INSERT. The self-report check
                // is handled in reviseAssignment where the ID exists.)
                
                // Cross-tenant check
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT tenant_id FROM hr_employee_assignments WHERE id = ?")) {
                    ps.setObject(1, reportsToAssignmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Manager assignment not found: " + reportsToAssignmentId);
                        UUID managerTenant = rs.getObject("tenant_id", UUID.class);
                        if (!managerTenant.equals(tenantId)) {
                            throw new IllegalStateException("Cross-tenant reporting link rejected");
                        }
                    }
                }
                
                // Cycle check
                UUID newId = UUID.randomUUID(); // pre-generate ID for cycle check
                boolean cycle = checkCycleOnConnection(connection, newId, reportsToAssignmentId, effectiveFrom, effectiveTo);
                if (cycle) {
                    throw new IllegalStateException("REPORTING_CYCLE: setting reports_to " + reportsToAssignmentId + " creates a cycle");
                }
            }

            // 8. INSERT
            UUID assignmentId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employee_assignments " +
                    "(id, tenant_id, employment_id, organization_id, org_unit_id, position_id, " +
                    "reports_to_assignment_id, work_location_id, cost_center_id, " +
                    "assignment_type, occupancy_mode, allocation_percent, " +
                    "effective_from, effective_to, status, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                ps.setObject(1, assignmentId);
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.setObject(4, organizationId);
                ps.setObject(5, orgUnitId);
                ps.setObject(6, positionId);
                ps.setObject(7, reportsToAssignmentId);
                ps.setObject(8, workLocationId);
                ps.setObject(9, costCenterId);
                ps.setString(10, assignmentType.name());
                ps.setString(11, occupancyMode.name());
                ps.setBigDecimal(12, allocationPercent);
                ps.setObject(13, java.sql.Date.valueOf(effectiveFrom));
                if (effectiveTo != null) ps.setObject(14, java.sql.Date.valueOf(effectiveTo));
                else ps.setNull(14, Types.DATE);
                ps.setString(15, "ACTIVE");
                ps.setLong(16, 0L);
                ps.executeUpdate();
            }

            return new HrAssignment(assignmentId, tenantId, employmentId, organizationId,
                    orgUnitId, positionId, reportsToAssignmentId, workLocationId,
                    assignmentType, occupancyMode, allocationPercent,
                    effectiveFrom, effectiveTo, "ACTIVE", 0L);
        });
    }

    /**
     * Atomically revise an assignment: validate → close old → insert new, on ONE connection.
     */
    public HrAssignment reviseAssignmentAtomically(
            UUID tenantId, UUID assignmentId,
            LocalDate effectiveFrom,
            UUID newReportsToAssignmentId, UUID newPositionId,
            OccupancyMode newOccupancyMode, BigDecimal newAllocationPercent) {
        return inTenantTransaction(tenantId, connection -> {
            // 1. Load existing assignment
            HrAssignment existing;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM hr_employee_assignments WHERE id = ?")) {
                ps.setObject(1, assignmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("Assignment not found: " + assignmentId);
                    existing = mapAssignment(rs);
                }
            }

            // 2. Self-report check
            if (newReportsToAssignmentId != null && newReportsToAssignmentId.equals(assignmentId)) {
                throw new IllegalStateException("Self-reporting is not allowed");
            }

            // 3. Cycle check (if reports_to is changing)
            if (newReportsToAssignmentId != null && !newReportsToAssignmentId.equals(existing.reportsToAssignmentId())) {
                boolean cycle = checkCycleOnConnection(connection, assignmentId, newReportsToAssignmentId, effectiveFrom, null);
                if (cycle) {
                    throw new IllegalStateException("REPORTING_CYCLE: setting reports_to " + newReportsToAssignmentId + " creates a cycle");
                }
            }

            // 4. Close existing open assignment
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employee_assignments SET effective_to = ?, updated_at = NOW() " +
                    "WHERE id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(effectiveFrom.minusDays(1)));
                ps.setObject(2, assignmentId);
                ps.executeUpdate();
            }

            // 5. Insert new version
            UUID newId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employee_assignments " +
                    "(id, tenant_id, employment_id, organization_id, org_unit_id, position_id, " +
                    "reports_to_assignment_id, work_location_id, cost_center_id, " +
                    "assignment_type, occupancy_mode, allocation_percent, " +
                    "effective_from, effective_to, status, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, NOW(), NOW())")) {
                ps.setObject(1, newId);
                ps.setObject(2, tenantId);
                ps.setObject(3, existing.employmentId());
                ps.setObject(4, existing.organizationId());
                ps.setObject(5, existing.orgUnitId());
                ps.setObject(6, newPositionId != null ? newPositionId : existing.positionId());
                ps.setObject(7, newReportsToAssignmentId != null ? newReportsToAssignmentId : existing.reportsToAssignmentId());
                ps.setObject(8, existing.workLocationId());
                ps.setObject(9, null); // cost_center_id not in frozen record
                ps.setString(10, existing.assignmentType().name());
                ps.setString(11, newOccupancyMode != null ? newOccupancyMode.name() : existing.occupancyMode().name());
                ps.setBigDecimal(12, newAllocationPercent != null ? newAllocationPercent : existing.allocationPercent());
                ps.setObject(13, java.sql.Date.valueOf(effectiveFrom));
                ps.setString(14, "ACTIVE");
                ps.setLong(15, existing.version() + 1);
                ps.executeUpdate();
            }

            return new HrAssignment(newId, tenantId, existing.employmentId(), existing.organizationId(),
                    existing.orgUnitId(),
                    newPositionId != null ? newPositionId : existing.positionId(),
                    newReportsToAssignmentId != null ? newReportsToAssignmentId : existing.reportsToAssignmentId(),
                    existing.workLocationId(),
                    existing.assignmentType(),
                    newOccupancyMode != null ? newOccupancyMode : existing.occupancyMode(),
                    newAllocationPercent != null ? newAllocationPercent : existing.allocationPercent(),
                    effectiveFrom, null, "ACTIVE", existing.version() + 1);
        });
    }

    // --- helpers ---

    private boolean checkCycleOnConnection(Connection connection, UUID assignmentId,
                                            UUID reportsToAssignmentId,
                                            LocalDate effectiveFrom, LocalDate effectiveTo) throws SQLException {
        String candidateEnd = effectiveTo != null ? "'" + effectiveTo.plusDays(1) + "'::date" : "'infinity'::date";
        String candidateRange = "daterange('" + effectiveFrom + "'::date, " + candidateEnd + ", '[)')";

        String sql = "WITH RECURSIVE chain AS (" +
                "  SELECT id, reports_to_assignment_id FROM hr_employee_assignments " +
                "  WHERE id = ? AND reports_to_assignment_id IS NOT NULL" +
                "    AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                "  UNION ALL" +
                "  SELECT a.id, a.reports_to_assignment_id FROM hr_employee_assignments a" +
                "  JOIN chain c ON a.id = c.reports_to_assignment_id" +
                "  WHERE a.reports_to_assignment_id IS NOT NULL" +
                "    AND daterange(a.effective_from, COALESCE(a.effective_to + 1, 'infinity'::date), '[)') && " + candidateRange + " " +
                ") SELECT EXISTS(SELECT 1 FROM chain WHERE reports_to_assignment_id = ?) AS has_cycle";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, reportsToAssignmentId);
            ps.setObject(2, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void setAssignmentParams(PreparedStatement ps, HrAssignment a) throws SQLException {
        ps.setObject(1, a.id());
        ps.setObject(2, a.tenantId());
        ps.setObject(3, a.employmentId());
        ps.setObject(4, a.organizationId());
        ps.setObject(5, a.orgUnitId());
        ps.setObject(6, a.positionId());
        ps.setObject(7, a.reportsToAssignmentId());
        ps.setObject(8, a.workLocationId());
        ps.setObject(9, null); // cost_center_id not in frozen record
        ps.setString(10, a.assignmentType().name());
        ps.setString(11, a.occupancyMode().name());
        ps.setBigDecimal(12, a.allocationPercent());
        ps.setObject(13, java.sql.Date.valueOf(a.effectiveFrom()));
        if (a.effectiveTo() != null) ps.setObject(14, java.sql.Date.valueOf(a.effectiveTo()));
        else ps.setNull(14, Types.DATE);
        ps.setString(15, a.status());
        ps.setLong(16, a.version());
    }

    private HrAssignment mapAssignment(ResultSet rs) throws SQLException {
        return new HrAssignment(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("employment_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class),
                rs.getObject("position_id", UUID.class),
                rs.getObject("reports_to_assignment_id", UUID.class),
                rs.getObject("work_location_id", UUID.class),
                AssignmentType.valueOf(rs.getString("assignment_type")),
                OccupancyMode.valueOf(rs.getString("occupancy_mode")),
                rs.getBigDecimal("allocation_percent"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getString("status"),
                rs.getLong("version"));
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
                throw new IllegalStateException("Assignment operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire assignment database connection", e);
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
