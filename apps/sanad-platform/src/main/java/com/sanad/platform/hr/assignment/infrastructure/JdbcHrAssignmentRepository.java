package com.sanad.platform.hr.assignment.infrastructure;

import com.sanad.platform.hr.assignment.domain.HrAssignment;
import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcHrAssignmentRepository {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;

    /**
     * Standard construction: evidence-atomic — critical assignment mutations
     * append hr_audit_ledger + hr_audit_delivery + hr_domain_event_outbox in
     * the SAME transaction (WS4 Task 4 contract).
     */
    public JdbcHrAssignmentRepository(DataSource dataSource) {
        this(dataSource, new JdbcHrEvidenceWriter(dataSource));
    }

    /** Explicit construction for tests (failure injection / observation). */
    public JdbcHrAssignmentRepository(DataSource dataSource, HrTransactionalEvidenceWriter evidenceWriter) {
        this.dataSource = dataSource;
        this.evidenceWriter = evidenceWriter;
    }

    private void appendEvidence(
            Connection connection, UUID tenantId, String eventType, HrAssignment assignment,
            ObjectNode beforeState, ObjectNode afterState, UUID distinguisher) throws SQLException {
        if (evidenceWriter == null) {
            return;
        }
        String action = eventType.substring(0, eventType.length() - 3);
        UUID eventId = UUID.nameUUIDFromBytes(
                (tenantId + "|" + eventType + "|" + assignment.id() + "|" + distinguisher)
                        .getBytes(StandardCharsets.UTF_8));
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("assignmentId", assignment.id().toString());
        payload.put("employmentId", assignment.employmentId().toString());
        if (assignment.organizationId() != null) {
            payload.put("organizationId", assignment.organizationId().toString());
        }
        payload.put("assignmentType", assignment.assignmentType().name());
        payload.put("occupancyMode", assignment.occupancyMode().name());
        payload.put("allocationPercent", assignment.allocationPercent() == null ? null : assignment.allocationPercent().toPlainString());
        payload.put("effectiveFrom", assignment.effectiveFrom() == null ? null : assignment.effectiveFrom().toString());
        evidenceWriter.writeEvidence(connection,
                new HrAuditRecord(tenantId, null, action, "ASSIGNMENT", assignment.id(),
                        assignment.organizationId(), null, "OPERATIONAL", null,
                        beforeState, afterState, "SUCCESS", null, distinguisher, Instant.now()),
                new DomainEventEnvelope(eventId, eventType, 1, "ASSIGNMENT", assignment.id(),
                        tenantId, assignment.organizationId(), null, Instant.now(), null, null,
                        eventType + ":" + assignment.id() + ":" + distinguisher,
                        "OPERATIONAL", payload));
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
        return inTenantTransaction(tenantId, connection ->
                checkCycleOnConnection(connection, assignmentId, reportsToAssignmentId,
                        effectiveFrom, effectiveTo));
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
            validateAllocationRange(allocationPercent);

            UUID legalEntityId = loadEmploymentLegalEntity(connection, tenantId, employmentId);
            validateOrganizationEligibility(connection, tenantId, organizationId, legalEntityId, effectiveFrom);
            validateOrgUnitEffectiveness(connection, tenantId, orgUnitId, effectiveFrom);
            validatePositionEffectiveness(connection, tenantId, positionId, effectiveFrom);

            // An incomplete intermediate FTE state is valid. Only overlapping
            // effective allocation above 100% is rejected.
            validateEffectiveAllocation(connection, tenantId, employmentId,
                    allocationPercent, effectiveFrom, effectiveTo, null);

            validateReporting(connection, tenantId, null, reportsToAssignmentId,
                    effectiveFrom, effectiveTo);

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

            HrAssignment created = new HrAssignment(assignmentId, tenantId, employmentId, organizationId,
                    orgUnitId, positionId, reportsToAssignmentId, workLocationId,
                    assignmentType, occupancyMode, allocationPercent,
                    effectiveFrom, effectiveTo, "ACTIVE", 0L);

            // Transactional evidence append (audit + delivery + outbox) — same transaction.
            ObjectNode afterState = OBJECT_MAPPER.createObjectNode();
            afterState.put("status", "ACTIVE");
            afterState.put("assignmentType", assignmentType.name());
            afterState.put("occupancyMode", occupancyMode.name());
            afterState.put("allocationPercent", allocationPercent == null ? null : allocationPercent.toPlainString());
            afterState.put("effectiveFrom", effectiveFrom.toString());
            appendEvidence(connection, tenantId, "HRM.ASSIGNMENT.CREATED.v1", created,
                    OBJECT_MAPPER.createObjectNode(), afterState, assignmentId);

            return created;
        });
    }

    /**
     * Atomically revise an assignment. Every prospective invariant is
     * validated before the first persisted mutation; then the old period is
     * closed and the replacement is inserted on the same connection/transaction.
     */
    public HrAssignment reviseAssignmentAtomically(
            UUID tenantId, UUID assignmentId,
            LocalDate effectiveFrom,
            UUID newReportsToAssignmentId, UUID newPositionId,
            OccupancyMode newOccupancyMode, BigDecimal newAllocationPercent) {
        return inTenantTransaction(tenantId, connection -> {
            HrAssignment existing = loadAssignmentForUpdate(connection, assignmentId);

            UUID candidatePositionId = newPositionId != null ? newPositionId : existing.positionId();
            UUID candidateReportsToId = newReportsToAssignmentId != null
                    ? newReportsToAssignmentId : existing.reportsToAssignmentId();
            OccupancyMode candidateOccupancyMode = newOccupancyMode != null
                    ? newOccupancyMode : existing.occupancyMode();
            BigDecimal candidateAllocation = newAllocationPercent != null
                    ? newAllocationPercent : existing.allocationPercent();

            // Validate the complete candidate before UPDATE/INSERT.
            validateAllocationRange(candidateAllocation);

            UUID legalEntityId = loadEmploymentLegalEntity(
                    connection, tenantId, existing.employmentId());
            validateOrganizationEligibility(connection, tenantId, existing.organizationId(),
                    legalEntityId, effectiveFrom);
            validateOrgUnitEffectiveness(connection, tenantId, existing.orgUnitId(), effectiveFrom);
            validatePositionEffectiveness(connection, tenantId, candidatePositionId, effectiveFrom);

            // Exclude the row being superseded so its old open-ended period is
            // not double-counted against the prospective replacement.
            validateEffectiveAllocation(connection, tenantId, existing.employmentId(),
                    candidateAllocation, effectiveFrom, null, assignmentId);
            validatePrimaryOverlap(connection, tenantId, existing.employmentId(),
                    existing.assignmentType(), effectiveFrom, null, assignmentId);
            validatePositionOccupancy(connection, tenantId, candidatePositionId,
                    candidateOccupancyMode, effectiveFrom, null, assignmentId);
            validateReporting(connection, tenantId, assignmentId, candidateReportsToId,
                    effectiveFrom, null);

            // No persisted mutation occurs before this point.
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employee_assignments SET effective_to = ?, updated_at = NOW() " +
                    "WHERE id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(effectiveFrom.minusDays(1)));
                ps.setObject(2, assignmentId);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Assignment is not open for revision: " + assignmentId);
                }
            }

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
                ps.setObject(6, candidatePositionId);
                ps.setObject(7, candidateReportsToId);
                ps.setObject(8, existing.workLocationId());
                ps.setObject(9, null); // cost_center_id not in frozen record
                ps.setString(10, existing.assignmentType().name());
                ps.setString(11, candidateOccupancyMode.name());
                ps.setBigDecimal(12, candidateAllocation);
                ps.setObject(13, java.sql.Date.valueOf(effectiveFrom));
                ps.setString(14, "ACTIVE");
                ps.setLong(15, existing.version() + 1);
                ps.executeUpdate();
            }

            HrAssignment revised = new HrAssignment(newId, tenantId, existing.employmentId(), existing.organizationId(),
                    existing.orgUnitId(), candidatePositionId, candidateReportsToId,
                    existing.workLocationId(), existing.assignmentType(), candidateOccupancyMode,
                    candidateAllocation, effectiveFrom, null, "ACTIVE", existing.version() + 1);

            // Transactional evidence append (audit + delivery + outbox) — same transaction.
            ObjectNode beforeState = OBJECT_MAPPER.createObjectNode();
            beforeState.put("status", existing.status());
            beforeState.put("allocationPercent", existing.allocationPercent() == null ? null : existing.allocationPercent().toPlainString());
            beforeState.put("effectiveFrom", existing.effectiveFrom() == null ? null : existing.effectiveFrom().toString());
            ObjectNode afterState = OBJECT_MAPPER.createObjectNode();
            afterState.put("status", "ACTIVE");
            afterState.put("allocationPercent", candidateAllocation == null ? null : candidateAllocation.toPlainString());
            afterState.put("effectiveFrom", effectiveFrom.toString());
            appendEvidence(connection, tenantId, "HRM.ASSIGNMENT.REVISED.v1", revised,
                    beforeState, afterState, assignmentId);

            return revised;
        });
    }

    // ==================== WS5 Task 4 (Assignment v2) ====================

    /** Tenant-scoped assignment listing — every period row, oldest first. */
    public java.util.List<HrAssignment> listAssignments(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM hr_employee_assignments WHERE tenant_id = ? ORDER BY effective_from, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.List<HrAssignment> result = new ArrayList<>();
                    while (rs.next()) result.add(mapAssignment(rs));
                    return result;
                }
            }
        });
    }

    /**
     * Terminal close of the open assignment period, guarded by the expected
     * version. History is preserved: the row remains with an ENDED status and
     * a closed effective_to. Idempotency of the HTTP layer is handled above
     * this persistence boundary.
     */
    public HrAssignment endAssignmentAtomically(UUID tenantId, UUID assignmentId,
                                                LocalDate effectiveTo, long expectedVersion) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        return inTenantTransaction(tenantId, connection -> {
            HrAssignment existing = loadAssignmentForUpdate(connection, assignmentId);
            if (existing.version() != expectedVersion) {
                throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: assignment version "
                        + existing.version() + " does not match expected " + expectedVersion);
            }
            if (existing.effectiveTo() != null || !"ACTIVE".equals(existing.status())) {
                throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: assignment "
                        + assignmentId + " is not an open ACTIVE period");
            }
            if (effectiveTo.isBefore(existing.effectiveFrom())) {
                throw new IllegalArgumentException("HRM_VALIDATION_FAILED: effectiveTo "
                        + effectiveTo + " precedes effectiveFrom " + existing.effectiveFrom());
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employee_assignments SET effective_to = ?, status = 'ENDED', " +
                    "version = version + 1, updated_at = NOW() " +
                    "WHERE id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(effectiveTo));
                ps.setObject(2, assignmentId);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Assignment is not open for end: " + assignmentId);
                }
            }

            HrAssignment ended = new HrAssignment(existing.id(), existing.tenantId(), existing.employmentId(),
                    existing.organizationId(), existing.orgUnitId(), existing.positionId(),
                    existing.reportsToAssignmentId(), existing.workLocationId(), existing.assignmentType(),
                    existing.occupancyMode(), existing.allocationPercent(), existing.effectiveFrom(),
                    effectiveTo, "ENDED", existing.version() + 1);

            ObjectNode beforeState = OBJECT_MAPPER.createObjectNode();
            beforeState.put("status", existing.status());
            ObjectNode afterState = OBJECT_MAPPER.createObjectNode();
            afterState.put("status", "ENDED");
            afterState.put("effectiveTo", effectiveTo.toString());
            appendEvidence(connection, tenantId, "HRM.ASSIGNMENT.ENDED.v1", ended,
                    beforeState, afterState, assignmentId);

            return ended;
        });
    }

    /**
     * Supersedes the open period with a new period carrying the new manager
     * link, guarded by the expected version. The superseded row keeps its
     * identity and gains a closed effective_to — history is never overwritten.
     */
    public HrAssignment changeManagerAtomically(UUID tenantId, UUID assignmentId,
                                                UUID newReportsToAssignmentId,
                                                LocalDate effectiveFrom, long expectedVersion) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        return inTenantTransaction(tenantId, connection -> {
            HrAssignment existing = loadAssignmentForUpdate(connection, assignmentId);
            if (existing.version() != expectedVersion) {
                throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: assignment version "
                        + existing.version() + " does not match expected " + expectedVersion);
            }
            if (existing.effectiveTo() != null || !"ACTIVE".equals(existing.status())) {
                throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: assignment "
                        + assignmentId + " is not an open ACTIVE period");
            }
            validateReporting(connection, tenantId, assignmentId, newReportsToAssignmentId, effectiveFrom, null);
            return supersedeAtomically(connection, tenantId, existing,
                    existing.orgUnitId(), existing.positionId(), newReportsToAssignmentId,
                    existing.occupancyMode(), existing.allocationPercent(), effectiveFrom);
        });
    }

    /**
     * Supersedes the open period with a new placement period (org unit and
     * optionally position/manager), guarded by the expected version. The full
     * WS2 validation chain runs before any mutation; the superseded row keeps
     * its identity and gains a closed effective_to — transfer never overwrites
     * historical placement.
     */
    public HrAssignment transferAssignmentAtomically(UUID tenantId, UUID assignmentId,
                                                     UUID newOrgUnitId, UUID newPositionId,
                                                     UUID newReportsToAssignmentId,
                                                     LocalDate effectiveFrom, long expectedVersion) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        return inTenantTransaction(tenantId, connection -> {
            HrAssignment existing = loadAssignmentForUpdate(connection, assignmentId);
            if (existing.version() != expectedVersion) {
                throw new IllegalStateException("HRM_CONCURRENCY_CONFLICT: assignment version "
                        + existing.version() + " does not match expected " + expectedVersion);
            }
            if (existing.effectiveTo() != null || !"ACTIVE".equals(existing.status())) {
                throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: assignment "
                        + assignmentId + " is not an open ACTIVE period");
            }

            UUID candidatePositionId = newPositionId != null ? newPositionId : existing.positionId();
            UUID candidateReportsToId = newReportsToAssignmentId != null
                    ? newReportsToAssignmentId : existing.reportsToAssignmentId();

            UUID legalEntityId = loadEmploymentLegalEntity(connection, tenantId, existing.employmentId());
            validateOrganizationEligibility(connection, tenantId, existing.organizationId(),
                    legalEntityId, effectiveFrom);
            validateOrgUnitEffectiveness(connection, tenantId, newOrgUnitId, effectiveFrom);
            validatePositionEffectiveness(connection, tenantId, candidatePositionId, effectiveFrom);
            validateEffectiveAllocation(connection, tenantId, existing.employmentId(),
                    existing.allocationPercent(), effectiveFrom, null, assignmentId);
            validatePrimaryOverlap(connection, tenantId, existing.employmentId(),
                    existing.assignmentType(), effectiveFrom, null, assignmentId);
            validatePositionOccupancy(connection, tenantId, candidatePositionId,
                    existing.occupancyMode(), effectiveFrom, null, assignmentId);
            validateReporting(connection, tenantId, assignmentId, candidateReportsToId, effectiveFrom, null);

            return supersedeAtomically(connection, tenantId, existing,
                    newOrgUnitId, candidatePositionId, candidateReportsToId,
                    existing.occupancyMode(), existing.allocationPercent(), effectiveFrom);
        });
    }

    /** Shared supersede core: close the open period, insert the successor period atomically. */
    private HrAssignment supersedeAtomically(Connection connection, UUID tenantId, HrAssignment existing,
                                             UUID candidateOrgUnitId, UUID candidatePositionId,
                                             UUID candidateReportsToId, OccupancyMode candidateOccupancyMode,
                                             BigDecimal candidateAllocation, LocalDate effectiveFrom)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_employee_assignments SET effective_to = ?, updated_at = NOW() " +
                "WHERE id = ? AND effective_to IS NULL")) {
            ps.setObject(1, java.sql.Date.valueOf(effectiveFrom.minusDays(1)));
            ps.setObject(2, existing.id());
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "Assignment is not open for revision: " + existing.id());
            }
        }

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
            ps.setObject(5, candidateOrgUnitId);
            ps.setObject(6, candidatePositionId);
            ps.setObject(7, candidateReportsToId);
            ps.setObject(8, existing.workLocationId());
            ps.setObject(9, null);
            ps.setString(10, existing.assignmentType().name());
            ps.setString(11, candidateOccupancyMode.name());
            ps.setBigDecimal(12, candidateAllocation);
            ps.setObject(13, java.sql.Date.valueOf(effectiveFrom));
            ps.setString(14, "ACTIVE");
            ps.setLong(15, existing.version() + 1);
            ps.executeUpdate();
        }

        HrAssignment revised = new HrAssignment(newId, tenantId, existing.employmentId(), existing.organizationId(),
                candidateOrgUnitId, candidatePositionId, candidateReportsToId,
                existing.workLocationId(), existing.assignmentType(), candidateOccupancyMode,
                candidateAllocation, effectiveFrom, null, "ACTIVE", existing.version() + 1);

        ObjectNode beforeState = OBJECT_MAPPER.createObjectNode();
        beforeState.put("status", existing.status());
        beforeState.put("allocationPercent", existing.allocationPercent() == null ? null : existing.allocationPercent().toPlainString());
        beforeState.put("effectiveFrom", existing.effectiveFrom() == null ? null : existing.effectiveFrom().toString());
        ObjectNode afterState = OBJECT_MAPPER.createObjectNode();
        afterState.put("status", "ACTIVE");
        afterState.put("allocationPercent", candidateAllocation == null ? null : candidateAllocation.toPlainString());
        afterState.put("effectiveFrom", effectiveFrom.toString());
        appendEvidence(connection, tenantId, "HRM.ASSIGNMENT.REVISED.v1", revised,
                beforeState, afterState, existing.id());

        return revised;
    }

    private HrAssignment loadAssignmentForUpdate(Connection connection, UUID assignmentId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM hr_employee_assignments WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, assignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Assignment not found: " + assignmentId);
                return mapAssignment(rs);
            }
        }
    }

    private void validateAllocationRange(BigDecimal allocationPercent) {
        if (allocationPercent == null || allocationPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("allocation_percent must be > 0");
        }
        if (allocationPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("allocation_percent must be <= 100");
        }
    }

    private UUID loadEmploymentLegalEntity(Connection connection, UUID tenantId, UUID employmentId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT legal_entity_id FROM hr_employees WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Employment not found: " + employmentId);
                UUID legalEntityId = rs.getObject("legal_entity_id", UUID.class);
                if (legalEntityId == null) throw new IllegalStateException("Employment has no legal_entity_id");
                return legalEntityId;
            }
        }
    }

    private void validateOrganizationEligibility(Connection connection, UUID tenantId,
                                                 UUID organizationId, UUID legalEntityId,
                                                 LocalDate effectiveFrom) throws SQLException {
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
                            "organization " + organizationId + " is not eligible for Legal Entity " + legalEntityId);
                }
            }
        }
    }

    private void validateOrgUnitEffectiveness(Connection connection, UUID tenantId,
                                              UUID orgUnitId, LocalDate effectiveFrom) throws SQLException {
        if (orgUnitId == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_org_unit_versions " +
                "WHERE org_unit_id = ? AND tenant_id = ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
            ps.setObject(1, orgUnitId);
            ps.setObject(2, tenantId);
            ps.setString(3, effectiveFrom.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "org unit " + orgUnitId + " has no effective version for " + effectiveFrom);
                }
            }
        }
    }

    private void validatePositionEffectiveness(Connection connection, UUID tenantId,
                                               UUID positionId, LocalDate effectiveFrom) throws SQLException {
        if (positionId == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_position_versions " +
                "WHERE position_id = ? AND tenant_id = ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') @> ?::date LIMIT 1")) {
            ps.setObject(1, positionId);
            ps.setObject(2, tenantId);
            ps.setString(3, effectiveFrom.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "position " + positionId + " has no effective version for " + effectiveFrom);
                }
            }
        }
    }

    private void validateEffectiveAllocation(Connection connection, UUID tenantId, UUID employmentId,
                                             BigDecimal candidateAllocation,
                                             LocalDate effectiveFrom, LocalDate effectiveTo,
                                             UUID excludeAssignmentId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(allocation_percent), 0) FROM hr_employee_assignments " +
                "WHERE tenant_id = ? AND employment_id = ? AND status = 'ACTIVE' " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') " +
                "&& daterange(?::date, COALESCE(?::date + 1, 'infinity'::date), '[)')" +
                (excludeAssignmentId != null ? " AND id <> ?" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            ps.setObject(3, java.sql.Date.valueOf(effectiveFrom));
            if (effectiveTo != null) ps.setObject(4, java.sql.Date.valueOf(effectiveTo));
            else ps.setNull(4, Types.DATE);
            if (excludeAssignmentId != null) ps.setObject(5, excludeAssignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal currentTotal = rs.getBigDecimal(1);
                if (currentTotal == null) currentTotal = BigDecimal.ZERO;
                BigDecimal prospectiveTotal = currentTotal.add(candidateAllocation);
                if (prospectiveTotal.compareTo(ONE_HUNDRED) > 0) {
                    throw new IllegalStateException(
                            "Total effective allocation would exceed 100%: current=" + currentTotal +
                                    " + candidate=" + candidateAllocation);
                }
            }
        }
    }

    private void validatePrimaryOverlap(Connection connection, UUID tenantId, UUID employmentId,
                                        AssignmentType assignmentType,
                                        LocalDate effectiveFrom, LocalDate effectiveTo,
                                        UUID excludeAssignmentId) throws SQLException {
        if (assignmentType != AssignmentType.PRIMARY) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_employee_assignments " +
                "WHERE tenant_id = ? AND employment_id = ? AND assignment_type = 'PRIMARY' AND status = 'ACTIVE' " +
                "AND id <> ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') " +
                "&& daterange(?::date, COALESCE(?::date + 1, 'infinity'::date), '[)') LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employmentId);
            ps.setObject(3, excludeAssignmentId);
            ps.setObject(4, java.sql.Date.valueOf(effectiveFrom));
            if (effectiveTo != null) ps.setObject(5, java.sql.Date.valueOf(effectiveTo));
            else ps.setNull(5, Types.DATE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException(
                            "PRIMARY assignment period overlaps an existing PRIMARY assignment");
                }
            }
        }
    }

    private void validatePositionOccupancy(Connection connection, UUID tenantId, UUID positionId,
                                           OccupancyMode occupancyMode,
                                           LocalDate effectiveFrom, LocalDate effectiveTo,
                                           UUID excludeAssignmentId) throws SQLException {
        if (positionId == null || occupancyMode != OccupancyMode.OCCUPYING) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM hr_employee_assignments " +
                "WHERE tenant_id = ? AND position_id = ? AND occupancy_mode = 'OCCUPYING' AND status = 'ACTIVE' " +
                "AND id <> ? " +
                "AND daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') " +
                "&& daterange(?::date, COALESCE(?::date + 1, 'infinity'::date), '[)') LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, positionId);
            ps.setObject(3, excludeAssignmentId);
            ps.setObject(4, java.sql.Date.valueOf(effectiveFrom));
            if (effectiveTo != null) ps.setObject(5, java.sql.Date.valueOf(effectiveTo));
            else ps.setNull(5, Types.DATE);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException(
                            "Position occupancy period overlaps an existing OCCUPYING assignment");
                }
            }
        }
    }

    private void validateReporting(Connection connection, UUID tenantId, UUID assignmentId,
                                   UUID reportsToAssignmentId,
                                   LocalDate effectiveFrom, LocalDate effectiveTo) throws SQLException {
        if (reportsToAssignmentId == null) return;
        if (assignmentId != null && reportsToAssignmentId.equals(assignmentId)) {
            throw new IllegalStateException("Self-reporting is not allowed");
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT tenant_id FROM hr_employee_assignments WHERE id = ?")) {
            ps.setObject(1, reportsToAssignmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Manager assignment not found: " + reportsToAssignmentId);
                }
                UUID managerTenant = rs.getObject("tenant_id", UUID.class);
                if (!tenantId.equals(managerTenant)) {
                    throw new IllegalStateException("Cross-tenant reporting link rejected");
                }
            }
        }

        if (assignmentId != null && checkCycleOnConnection(connection, assignmentId,
                reportsToAssignmentId, effectiveFrom, effectiveTo)) {
            throw new IllegalStateException(
                    "REPORTING_CYCLE: setting reports_to " + reportsToAssignmentId + " creates a cycle");
        }
    }

    private boolean checkCycleOnConnection(Connection connection, UUID assignmentId,
                                           UUID reportsToAssignmentId,
                                           LocalDate effectiveFrom, LocalDate effectiveTo) throws SQLException {
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
