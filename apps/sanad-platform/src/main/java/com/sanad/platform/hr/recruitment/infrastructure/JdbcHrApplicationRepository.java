package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrApplication;
import com.sanad.platform.hr.recruitment.domain.HrApplicationState;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for the HrApplication aggregate (HRM-G1 T5).
 *
 * <p>Every mutation runs on a transaction-scoped connection with
 * {@code SET LOCAL app.tenant_id} (FORCE RLS) and appends the audit fact and
 * the §12 outbox event on the SAME connection via the G0 transactional
 * evidence writer. Stage-period history is append-only: rows are inserted,
 * never updated. The single-active-application invariant is enforced by the
 * partial unique index {@code uq_hr_applications_one_active_per_opening}
 * (23505 → mapped by the service).</p>
 */
@Repository
public class JdbcHrApplicationRepository {

    public static final String RESOURCE_TYPE = "HR_APPLICATION";

    public static final String ACTION_SUBMITTED = "HRM.RECRUITMENT.APPLICATION_SUBMITTED";
    public static final String ACTION_ADVANCED = "HRM.RECRUITMENT.APPLICATION_ADVANCED";
    public static final String ACTION_REJECTED = "HRM.RECRUITMENT.APPLICATION_REJECTED";
    public static final String ACTION_WITHDRAWN = "HRM.RECRUITMENT.APPLICATION_WITHDRAWN";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final JdbcHrEvidenceWriter evidenceWriter;

    @Autowired
    public JdbcHrApplicationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.evidenceWriter = new JdbcHrEvidenceWriter(dataSource);
    }

    /** Inserts the application (state APPLIED) with its first stage period. */
    public HrApplication insert(HrApplication application, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, application.tenantId());
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_applications (id, tenant_id, candidate_id, job_opening_id, state, version) "
                                + "VALUES (?, ?, ?, ?, ?, 0)")) {
                    ps.setObject(1, application.id());
                    ps.setObject(2, application.tenantId());
                    ps.setObject(3, application.candidateId());
                    ps.setObject(4, application.jobOpeningId());
                    ps.setString(5, application.state().name());
                    ps.executeUpdate();
                }
                insertStagePeriod(connection, application.tenantId(), application.id(),
                        application.stage());
                writeEvidence(connection, audit(application.tenantId(), actorId, ACTION_SUBMITTED,
                        application.id(), correlationId), envelope(application.tenantId(),
                        ACTION_SUBMITTED, application.id(), actorId, correlationId));
                connection.commit();
                return application;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof SQLException se && "23505".equals(se.getSQLState())) {
                    throw new IllegalStateException("HRM_APPLICATION_ALREADY_ACTIVE: "
                            + "one active application per (candidate, opening)");
                }
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Tenant-scoped load (FORCE RLS applies; foreign-tenant ids resolve empty). */
    public Optional<HrApplication> find(UUID tenantId, UUID applicationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<HrApplication> found = findWithinTx(connection, tenantId, applicationId);
                connection.commit();
                return found;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Guarded optimistic transition; a stage advance appends the new stage
     * period row (append-only history). No path in this repository may set
     * state HIRED — that belongs exclusively to the §7 conversion (T8).
     */
    public HrApplication transition(UUID tenantId, UUID applicationId, HrApplicationState expectedFrom,
                                    HrApplicationState to, boolean appendStagePeriod, String auditAction,
                                    UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_applications SET state = ?, version = version + 1, updated_at = NOW() "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? AND version = "
                                + "(SELECT version FROM hr_applications WHERE id = ? AND tenant_id = ?)")) {
                    ps.setString(1, to.name());
                    ps.setObject(2, applicationId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, expectedFrom.name());
                    ps.setObject(5, applicationId);
                    ps.setObject(6, tenantId);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        connection.rollback();
                        throw new IllegalStateException("HRM_APPLICATION_STATE_CONFLICT: expected state "
                                + expectedFrom + " (concurrent modification)");
                    }
                }
                if (appendStagePeriod) {
                    insertStagePeriod(connection, tenantId, applicationId, to.name());
                }
                HrApplication updated = findWithinTx(connection, tenantId, applicationId)
                        .orElseThrow(() -> new IllegalStateException("HRM_APPLICATION_NOT_FOUND: " + applicationId));
                writeEvidence(connection, audit(tenantId, actorId, auditAction, applicationId, correlationId),
                        envelope(tenantId, auditAction, applicationId, actorId, correlationId));
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof IllegalStateException ise ? ise
                        : e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Deterministic pipeline history (from_at ASC, id ASC). */
    public List<HrApplication.StagePeriod> stagePeriods(UUID tenantId, UUID applicationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                List<HrApplication.StagePeriod> periods = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, tenant_id, application_id, stage, from_at "
                                + "FROM hr_application_stage_periods WHERE application_id = ? AND tenant_id = ? "
                                + "ORDER BY from_at ASC, id ASC")) {
                    ps.setObject(1, applicationId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            periods.add(new HrApplication.StagePeriod(
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("tenant_id"),
                                    (UUID) rs.getObject("application_id"),
                                    rs.getString("stage"),
                                    rs.getObject("from_at", OffsetDateTime.class)));
                        }
                    }
                }
                connection.commit();
                return periods;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_APPLICATION_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // --- internals ---

    private void insertStagePeriod(Connection connection, UUID tenantId, UUID applicationId, String stage)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_application_stage_periods (tenant_id, application_id, stage) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, applicationId);
            ps.setString(3, stage);
            ps.executeUpdate();
        }
    }

    private Optional<HrApplication> findWithinTx(Connection connection, UUID tenantId, UUID applicationId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, candidate_id, job_opening_id, state, version "
                        + "FROM hr_applications WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, applicationId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HrApplication(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("candidate_id"),
                        (UUID) rs.getObject("job_opening_id"),
                        HrApplicationState.valueOf(rs.getString("state")),
                        rs.getLong("version")));
            }
        }
    }

    private void writeEvidence(Connection connection, HrAuditRecord audit, DomainEventEnvelope envelope) {
        evidenceWriter.writeEvidence(connection, audit, envelope);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID applicationId,
                                UUID correlationId) {
        ObjectNode after = JSON.createObjectNode().put("state_event", action);
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, applicationId,
                null, null, "OPERATIONAL", null, JSON.createObjectNode(), after, "SUCCESS",
                correlationId, null, null);
    }

    private DomainEventEnvelope envelope(UUID tenantId, String eventType, UUID applicationId,
                                         UUID actorId, UUID correlationId) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("application_id", applicationId.toString());
        payload.put("event_type", eventType);
        return new DomainEventEnvelope(UUID.randomUUID(), eventType, 1, RESOURCE_TYPE, applicationId,
                tenantId, null, actorId, OffsetDateTime.now().toInstant(), correlationId, null,
                null, "OPERATIONAL", payload);
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }
}
