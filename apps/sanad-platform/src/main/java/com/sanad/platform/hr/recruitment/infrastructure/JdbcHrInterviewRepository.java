package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrInterviewState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JDBC persistence for the HrInterview aggregate (HRM-G1 T6).
 *
 * <p>Every mutation runs on a transaction-scoped connection with
 * {@code SET LOCAL app.tenant_id} (FORCE RLS) and appends the audit fact and
 * the §12 outbox event on the SAME connection via the G0 transactional
 * evidence writer. Feedback is one row per (interview, participant) — the
 * UK-enforced pair — versioned in place on edit.</p>
 */
@Repository
public class JdbcHrInterviewRepository {

    public static final String RESOURCE_TYPE = "HR_INTERVIEW";

    public static final String ACTION_SCHEDULED = "HRM.RECRUITMENT.INTERVIEW_SCHEDULED";
    public static final String ACTION_COMPLETED = "HRM.RECRUITMENT.INTERVIEW_COMPLETED";
    public static final String ACTION_FEEDBACK = "HRM.RECRUITMENT.INTERVIEW_FEEDBACK_RECORDED";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final JdbcHrEvidenceWriter evidenceWriter;

    @Autowired
    public JdbcHrInterviewRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.evidenceWriter = new JdbcHrEvidenceWriter(dataSource);
    }

    /** Inserts a SCHEDULED interview with its tenant panel. */
    public UUID insert(UUID tenantId, UUID applicationId, OffsetDateTime plannedAt, String mode,
                       Integer durationMinutes, java.util.List<UUID> panelUserIds,
                       UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID interviewId = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_interviews (id, tenant_id, application_id, planned_at, mode, "
                                + "duration_minutes, state) VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED')")) {
                    ps.setObject(1, interviewId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, applicationId);
                    ps.setObject(4, Timestamp.from(plannedAt.toInstant()));
                    ps.setString(5, mode);
                    if (durationMinutes == null) {
                        ps.setNull(6, Types.INTEGER);
                    } else {
                        ps.setInt(6, durationMinutes);
                    }
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_interview_participants (tenant_id, interview_id, user_id) "
                                + "VALUES (?, ?, ?)")) {
                    for (UUID panelUser : panelUserIds) {
                        ps.setObject(1, tenantId);
                        ps.setObject(2, interviewId);
                        ps.setObject(3, panelUser);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                writeEvidence(connection, audit(tenantId, actorId, ACTION_SCHEDULED, interviewId, correlationId),
                        envelope(tenantId, ACTION_SCHEDULED, interviewId, actorId, correlationId));
                connection.commit();
                return interviewId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Guarded optimistic outcome transition (SCHEDULED → terminal state). */
    public void recordOutcome(UUID tenantId, UUID interviewId, String state, String outcome,
                              UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_interviews SET state = ?, outcome = ?, version = version + 1, "
                                + "updated_at = NOW() WHERE id = ? AND tenant_id = ? AND state = 'SCHEDULED'")) {
                    ps.setString(1, state);
                    if (outcome == null) {
                        ps.setNull(2, Types.VARCHAR);
                    } else {
                        ps.setString(2, outcome);
                    }
                    ps.setObject(3, interviewId);
                    ps.setObject(4, tenantId);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        connection.rollback();
                        throw new IllegalStateException("HRM_INTERVIEW_STATE_CONFLICT: "
                                + "interview is not in SCHEDULED state");
                    }
                }
                writeEvidence(connection, audit(tenantId, actorId, ACTION_COMPLETED, interviewId, correlationId),
                        envelope(tenantId, ACTION_COMPLETED, interviewId, actorId, correlationId));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof IllegalStateException ise ? ise
                        : e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Interview load (tenant-scoped; FORCE RLS). */
    public InterviewRow find(UUID tenantId, UUID interviewId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                InterviewRow row = findWithinTx(connection, tenantId, interviewId);
                connection.commit();
                return row;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Panel member rows for the interview (tenant users via IAM). */
    public java.util.List<UUID> participantUserIds(UUID tenantId, UUID interviewId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                java.util.List<UUID> users = new java.util.ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT user_id FROM hr_interview_participants WHERE interview_id = ? AND tenant_id = ?")) {
                    ps.setObject(1, interviewId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            users.add((UUID) rs.getObject("user_id"));
                        }
                    }
                }
                connection.commit();
                return users;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** True when every panel user resolves to a user of the tenant. */
    public boolean allUsersInTenant(UUID tenantId, java.util.List<UUID> userIds) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean all = true;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM users WHERE id = ? AND tenant_id = ?")) {
                    for (UUID userId : userIds) {
                        ps.setObject(1, userId);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            if (rs.getInt(1) != 1) {
                                all = false;
                                break;
                            }
                        }
                    }
                }
                connection.commit();
                return all;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Upserts the participant scorecard (one row per participant, versioned in place). */
    public void upsertFeedback(UUID tenantId, UUID interviewId, UUID participantRowId, JsonNode scorecard,
                               UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_interview_feedback SET scorecard = ?::jsonb, version = version + 1, "
                                + "updated_at = NOW() WHERE interview_id = ? AND participant_id = ? "
                                + "AND tenant_id = ?")) {
                    ps.setString(1, scorecard.toString());
                    ps.setObject(2, interviewId);
                    ps.setObject(3, participantRowId);
                    ps.setObject(4, tenantId);
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO hr_interview_feedback (tenant_id, interview_id, participant_id, "
                                    + "scorecard, version) VALUES (?, ?, ?, ?::jsonb, 1)")) {
                        ps.setObject(1, tenantId);
                        ps.setObject(2, interviewId);
                        ps.setObject(3, participantRowId);
                        ps.setString(4, scorecard.toString());
                        ps.executeUpdate();
                    }
                }
                writeEvidence(connection, audit(tenantId, actorId, ACTION_FEEDBACK, interviewId, correlationId),
                        envelope(tenantId, ACTION_FEEDBACK, interviewId, actorId, correlationId));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Resolves the participant row id for (interview, user) or null. */
    public UUID participantRowId(UUID tenantId, UUID interviewId, UUID userId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                UUID participantId = null;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id FROM hr_interview_participants WHERE interview_id = ? AND user_id = ? "
                                + "AND tenant_id = ? LIMIT 1")) {
                    ps.setObject(1, interviewId);
                    ps.setObject(2, userId);
                    ps.setObject(3, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            participantId = (UUID) rs.getObject("id");
                        }
                    }
                }
                connection.commit();
                return participantId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_INTERVIEW_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // --- internals ---

    private InterviewRow findWithinTx(Connection connection, UUID tenantId, UUID interviewId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, application_id, planned_at, mode, duration_minutes, state, outcome, version "
                        + "FROM hr_interviews WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, interviewId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new InterviewRow(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("application_id"),
                        rs.getObject("planned_at", OffsetDateTime.class),
                        rs.getString("mode"),
                        (Integer) rs.getObject("duration_minutes"),
                        HrInterviewState.valueOf(rs.getString("state")),
                        rs.getString("outcome"),
                        rs.getLong("version"));
            }
        }
    }

    /** Interview projection (§13.1 row 14). */
    public record InterviewRow(UUID id, UUID tenantId, UUID applicationId, OffsetDateTime plannedAt,
                               String mode, Integer durationMinutes, HrInterviewState state,
                               String outcome, long version) {
    }

    private void writeEvidence(Connection connection, HrAuditRecord audit,
                               com.sanad.platform.integration.events.DomainEventEnvelope envelope) {
        evidenceWriter.writeEvidence(connection, audit, envelope);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID interviewId,
                                UUID correlationId) {
        ObjectNode after = JSON.createObjectNode().put("state_event", action);
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, interviewId,
                null, null, "OPERATIONAL", null, JSON.createObjectNode(), after, "SUCCESS",
                correlationId, null, null);
    }

    private com.sanad.platform.integration.events.DomainEventEnvelope envelope(
            UUID tenantId, String eventType, UUID interviewId, UUID actorId, UUID correlationId) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("interview_id", interviewId.toString());
        payload.put("event_type", eventType);
        return new com.sanad.platform.integration.events.DomainEventEnvelope(
                UUID.randomUUID(), eventType, 1, RESOURCE_TYPE, interviewId,
                tenantId, null, actorId, OffsetDateTime.now().toInstant(), correlationId, null,
                null, "OPERATIONAL", payload);
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }
}
