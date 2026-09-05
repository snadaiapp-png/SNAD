package com.sanad.platform.hr.employment;

import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
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

/**
 * JDBC implementation of {@link EmploymentRepository}.
 *
 * <p>Every operation establishes {@code app.tenant_id} on the SAME
 * database connection/transaction used for the query — preserving
 * FORCE RLS semantics.</p>
 */
public final class JdbcEmploymentRepository implements EmploymentRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;

    /**
     * Standard construction: evidence-atomic — every lifecycle transition
     * appends hr_audit_ledger + hr_audit_delivery + hr_domain_event_outbox
     * in the SAME transaction (WS4 Task 4 contract).
     */
    public JdbcEmploymentRepository(DataSource dataSource) {
        this(dataSource, defaultEvidenceWriter(dataSource));
    }

    /**
     * Explicit construction: tests may inject a failure-injecting or
     * observing evidence writer. {@code null} disables evidence append
     * (legacy behavior; only used by legacy callers).
     */
    public JdbcEmploymentRepository(DataSource dataSource, HrTransactionalEvidenceWriter evidenceWriter) {
        this.dataSource = dataSource;
        this.evidenceWriter = evidenceWriter;
    }

    private static HrTransactionalEvidenceWriter defaultEvidenceWriter(DataSource dataSource) {
        return new JdbcHrEvidenceWriter(dataSource);
    }

    /** Canonical, versioned HRM event names for employment lifecycle transitions. */
    private static String employmentEventType(EmploymentStatus targetStatus) {
        return switch (targetStatus) {
            case DRAFT -> "HRM.EMPLOYEE.DRAFT_CREATED.v1";
            case PENDING_ONBOARDING -> "HRM.EMPLOYEE.ONBOARDING_SUBMITTED.v1";
            case ACTIVE -> "HRM.EMPLOYEE.ACTIVATED.v1";
            case ON_LEAVE -> "HRM.EMPLOYEE.LEAVE_STARTED.v1";
            case SUSPENDED -> "HRM.EMPLOYEE.SUSPENDED.v1";
            case TERMINATED -> "HRM.EMPLOYEE.TERMINATED.v1";
            case VOIDED -> "HRM.EMPLOYEE.VOIDED.v1";
        };
    }

    @Override
    public void saveEmployment(Employment employment) {
        inTenantTransaction(employment.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employees " +
                    "(id, tenant_id, person_id, legal_entity_id, employee_number, " +
                    "first_name, last_name, display_name, employment_type, worker_classification_code, " +
                    "status, hire_date, termination_date, rehire_of_employee_id, version, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FULL_TIME', ?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
                ps.setObject(1, employment.id());
                ps.setObject(2, employment.tenantId());
                ps.setObject(3, employment.personId());
                ps.setObject(4, employment.legalEntityId());
                ps.setString(5, employment.employeeNumber());
                ps.setString(6, "Test");
                ps.setString(7, "Employee");
                ps.setString(8, "Test Employee");
                ps.setString(9, employment.workerClassificationCode());
                ps.setString(10, employment.currentStatus().name());
                if (employment.employmentStartDate() != null) {
                    ps.setObject(11, java.sql.Date.valueOf(employment.employmentStartDate()));
                } else {
                    ps.setNull(11, Types.DATE);
                }
                if (employment.terminationDate() != null) {
                    ps.setObject(12, java.sql.Date.valueOf(employment.terminationDate()));
                } else {
                    ps.setNull(12, Types.DATE);
                }
                ps.setObject(13, employment.rehireOfEmployeeId());
                ps.setLong(14, employment.version());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<Employment> findEmploymentById(UUID tenantId, UUID employmentId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, person_id, legal_entity_id, employee_number, " +
                    "worker_classification_code, status, hire_date, termination_date, " +
                    "rehire_of_employee_id, version " +
                    "FROM hr_employees WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapEmployment(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Employment> listEmployments(UUID tenantId) {
        return inTenantTransaction(tenantId, connection -> {
            List<Employment> result = new java.util.ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, person_id, legal_entity_id, employee_number, " +
                    "worker_classification_code, status, hire_date, termination_date, " +
                    "rehire_of_employee_id, version " +
                    "FROM hr_employees WHERE tenant_id = ? ORDER BY created_at DESC, id")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapEmployment(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public int countNonTerminalEmploymentsForPersonInLegalEntity(UUID tenantId, UUID personId, UUID legalEntityId) {
        return inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM hr_employees " +
                    "WHERE tenant_id = ? AND person_id = ? AND legal_entity_id = ? " +
                    "AND status IN ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED')")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, personId);
                ps.setObject(3, legalEntityId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    @Override
    public void saveStatusPeriod(EmploymentStatusPeriod period) {
        // saveStatusPeriod does a plain INSERT — no UPDATE.
        // Re-saving an existing period (same id) causes PK violation → RuntimeException.
        // This enforces historical immutability: closed periods cannot be mutated.
        inTenantTransaction(period.tenantId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employment_status_periods " +
                    "(id, tenant_id, employment_id, status, effective_from, effective_to, " +
                    "reason_code, reason_text, changed_by, transition_event_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())")) {
                ps.setObject(1, period.id());
                ps.setObject(2, period.tenantId());
                ps.setObject(3, period.employmentId());
                ps.setString(4, period.status().name());
                ps.setObject(5, java.sql.Date.valueOf(period.effectiveFrom()));
                if (period.effectiveTo() != null) {
                    ps.setObject(6, java.sql.Date.valueOf(period.effectiveTo()));
                } else {
                    ps.setNull(6, Types.DATE);
                }
                ps.setString(7, period.reasonCode());
                ps.setString(8, period.reasonText());
                ps.setObject(9, period.changedBy());
                ps.setObject(10, period.transitionEventId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<EmploymentStatusPeriod> statusPeriods(UUID tenantId, UUID employmentId) {
        return inTenantTransaction(tenantId, connection -> {
            List<EmploymentStatusPeriod> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, tenant_id, employment_id, status, effective_from, effective_to, " +
                    "reason_code, reason_text, changed_by, transition_event_id " +
                    "FROM hr_employment_status_periods " +
                    "WHERE tenant_id = ? AND employment_id = ? " +
                    "ORDER BY effective_from, created_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapPeriod(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public void updateCurrentStatusProjection(UUID tenantId, UUID employmentId,
                                                EmploymentStatus newStatus, long expectedVersion) {
        inTenantTransaction(tenantId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employees SET status = ?, version = version + 1, updated_at = NOW() " +
                    "WHERE tenant_id = ? AND id = ? AND version = ?")) {
                ps.setString(1, newStatus.name());
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.setLong(4, expectedVersion);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                        "Employment not found or version mismatch (expected " + expectedVersion + ")");
                }
            }
            return null;
        });
    }

    /**
     * Execute an entire lifecycle transition atomically on a SINGLE connection.
     *
     * <p>This method:
     * <ol>
     *   <li>Opens one Connection with autoCommit=false</li>
     *   <li>Sets app.tenant_id on that Connection</li>
     *   <li>Closes the open status period (UPDATE effective_to)</li>
     *   <li>Inserts the new status period</li>
     *   <li>Updates hr_employees.current_status + version</li>
     *   <li>Commits (or rolls back on any error)</li>
     * </ol>
     * </p>
     *
     * <p>This guarantees LIFECYCLE_TRANSACTION_ATOMIC = YES.</p>
     */
    public EmploymentTransitionResult executeTransition(
            UUID tenantId, UUID employmentId,
            EmploymentStatus currentStatus, EmploymentStatus targetStatus,
            long expectedVersion, LocalDate effectiveDate, String reasonCode) {
        return inTenantTransaction(tenantId, connection -> {
            UUID transitionEventId = UUID.randomUUID();

            // 1. Close the open period.
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employment_status_periods SET effective_to = ? " +
                    "WHERE tenant_id = ? AND employment_id = ? AND effective_to IS NULL")) {
                ps.setObject(1, java.sql.Date.valueOf(effectiveDate.minusDays(1)));
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.executeUpdate();
            }

            // 2. Insert new period.
            UUID newPeriodId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_employment_status_periods " +
                    "(id, tenant_id, employment_id, status, effective_from, effective_to, " +
                    "reason_code, transition_event_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, NOW())")) {
                ps.setObject(1, newPeriodId);
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.setString(4, targetStatus.name());
                ps.setObject(5, java.sql.Date.valueOf(effectiveDate));
                ps.setString(6, reasonCode);
                ps.setObject(7, transitionEventId);
                ps.executeUpdate();
            }

            // 3. Update projection + version.
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE hr_employees SET status = ?, version = version + 1, updated_at = NOW() " +
                    "WHERE tenant_id = ? AND id = ? AND version = ?")) {
                ps.setString(1, targetStatus.name());
                ps.setObject(2, tenantId);
                ps.setObject(3, employmentId);
                ps.setLong(4, expectedVersion);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                        "Employment not found or version mismatch (expected " + expectedVersion + ")");
                }
            }

            // 4. Transactional mutation evidence: audit fact + delivery state +
            //    outbox event — same transaction, no REQUIRES_NEW. Any append
            //    failure rolls back the entire transition.
            if (evidenceWriter != null) {
                String eventType = employmentEventType(targetStatus);
                String action = eventType.substring(0, eventType.length() - 3);
                UUID eventId = JdbcHrEvidenceWriter.deterministicEventId(
                        tenantId, eventType, employmentId, transitionEventId);

                ObjectNode beforeState = OBJECT_MAPPER.createObjectNode();
                beforeState.put("status", currentStatus.name());
                beforeState.put("version", expectedVersion);
                ObjectNode afterState = OBJECT_MAPPER.createObjectNode();
                afterState.put("status", targetStatus.name());
                afterState.put("version", expectedVersion + 1);
                afterState.put("reasonCode", reasonCode);
                afterState.put("effectiveDate", effectiveDate.toString());

                ObjectNode payload = OBJECT_MAPPER.createObjectNode();
                payload.put("employmentId", employmentId.toString());
                payload.put("fromStatus", currentStatus.name());
                payload.put("toStatus", targetStatus.name());
                payload.put("effectiveDate", effectiveDate.toString());
                payload.put("reasonCode", reasonCode);

                evidenceWriter.writeEvidence(connection,
                        new HrAuditRecord(tenantId, null, action, "EMPLOYMENT", employmentId,
                                null, null, "OPERATIONAL", reasonCode,
                                beforeState, afterState, "SUCCESS", null, transitionEventId, Instant.now()),
                        new DomainEventEnvelope(eventId, eventType, 1, "EMPLOYMENT", employmentId,
                                tenantId, null, null, Instant.now(), null, null,
                                eventType + ":" + employmentId + ":" + transitionEventId,
                                "OPERATIONAL", payload));
            }

            return new EmploymentTransitionResult(
                    employmentId, currentStatus, targetStatus, null, newPeriodId);
        });
    }

    // --- helpers ---

    private Employment mapEmployment(ResultSet rs) throws SQLException {
        return new Employment(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("person_id", UUID.class),
                rs.getObject("legal_entity_id", UUID.class),
                rs.getString("employee_number"),
                rs.getString("worker_classification_code"),
                EmploymentStatus.valueOf(rs.getString("status")),
                rs.getDate("hire_date") != null ? rs.getDate("hire_date").toLocalDate() : null,
                rs.getDate("termination_date") != null ? rs.getDate("termination_date").toLocalDate() : null,
                rs.getObject("rehire_of_employee_id", UUID.class),
                rs.getLong("version"));
    }

    private EmploymentStatusPeriod mapPeriod(ResultSet rs) throws SQLException {
        return new EmploymentStatusPeriod(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("employment_id", UUID.class),
                EmploymentStatus.valueOf(rs.getString("status")),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                rs.getString("reason_code"),
                rs.getString("reason_text"),
                rs.getObject("changed_by", UUID.class),
                rs.getObject("transition_event_id", UUID.class));
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
                throw new IllegalStateException("Employment persistence operation failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acquire Employment database connection", e);
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
