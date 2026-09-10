package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrAuditService;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.integration.JdbcHrEvidenceWriter;
import com.sanad.platform.hr.recruitment.domain.HrJobOpening;
import com.sanad.platform.hr.recruitment.domain.HrJobOpeningState;
import com.sanad.platform.integration.events.DomainEventEnvelope;
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
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for the HrJobOpening aggregate (HRM-G1 T3).
 *
 * <p>Every mutation runs on a SINGLE transaction-scoped connection with
 * {@code SET LOCAL app.tenant_id} (FORCE RLS stays fully enforced) and
 * appends the audit fact — and, for §12 state-change events, the outbox
 * envelope — on the SAME connection via the G0
 * {@link HrTransactionalEvidenceWriter}. Any evidence failure rolls the
 * business mutation back atomically.</p>
 */
@Repository
public class JdbcHrJobOpeningRepository {

    public static final String RESOURCE_TYPE = "HR_JOB_OPENING";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final HrTransactionalEvidenceWriter evidenceWriter;
    /** Audit-only evidence path (G0-composed, same as JdbcHrEvidenceWriter internals). */
    private final HrAuditService auditService;

    @Autowired
    public JdbcHrJobOpeningRepository(DataSource dataSource, HrTransactionalEvidenceWriter evidenceWriter) {
        this.dataSource = dataSource;
        this.evidenceWriter = evidenceWriter;
        HrRedactionGuard guard = new HrRedactionGuard();
        this.auditService = new HrAuditService(dataSource, guard, new JdbcHrAuditRepository(guard));
    }

    /** Inserts a new DRAFT opening, allocating a tenant-unique opening number. */
    public HrJobOpening insert(HrJobOpening opening, UUID actorId, UUID correlationId, UUID requestId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, opening.tenantId());
                HrJobOpening inserted = insertWithinTx(connection, opening, actorId, correlationId, requestId);
                connection.commit();
                return inserted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private HrJobOpening insertWithinTx(Connection connection, HrJobOpening opening,
                                        UUID actorId, UUID correlationId, UUID requestId) throws SQLException {
        String year = String.valueOf(OffsetDateTime.now().getYear());
        SQLException lastConflict = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            long candidate = countOpeningsThisYear(connection, opening.tenantId(), year) + 1 + attempt;
            String openingNumber = "HRU-" + year + "-" + candidate;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_job_openings (id, tenant_id, opening_number, job_id, job_version_id, "
                            + "org_unit_id, position_id, state, requested_headcount, filled_headcount, "
                            + "opens_at, closes_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0)")) {
                ps.setObject(1, opening.id());
                ps.setObject(2, opening.tenantId());
                ps.setString(3, openingNumber);
                ps.setObject(4, opening.jobId());
                setNullableUuid(ps, 5, opening.jobVersionId());
                ps.setObject(6, opening.orgUnitId());
                setNullableUuid(ps, 7, opening.positionId());
                ps.setString(8, opening.state().name());
                ps.setInt(9, opening.requestedHeadcount());
                setNullableTimestamp(ps, 10, opening.opensAt());
                setNullableTimestamp(ps, 11, opening.closesAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    lastConflict = e; // concurrent number allocation — retry with next candidate
                    continue;
                }
                throw e;
            }

            ObjectNode before = JSON.createObjectNode().putNull("state");
            ObjectNode after = JSON.createObjectNode().put("state", opening.state().name());
            writeEvidence(connection, auditRecord(opening.tenantId(), actorId,
                    "HRM.RECRUITMENT.OPENING_CREATED", opening.id(),
                    before, after, "SUCCESS", correlationId, requestId), null);
            return new HrJobOpening(opening.id(), opening.tenantId(), openingNumber, opening.jobId(),
                    opening.jobVersionId(), opening.orgUnitId(), opening.positionId(), opening.state(),
                    opening.requestedHeadcount(), 0, null, null, opening.opensAt(), opening.closesAt(), 0L);
        }
        throw new IllegalStateException("HRM_OPENING_NUMBER_ALLOCATION_FAILED: " + lastConflict.getMessage(),
                lastConflict);
    }

    private long countOpeningsThisYear(Connection connection, UUID tenantId, String year) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM hr_job_openings WHERE tenant_id = ? AND opening_number LIKE ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "HRU-" + year + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Tenant-scoped load (FORCE RLS applies; foreign-tenant ids resolve empty). */
    public Optional<HrJobOpening> find(UUID tenantId, UUID openingId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<HrJobOpening> found = findWithinTx(connection, tenantId, openingId);
                connection.commit();
                return found;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private Optional<HrJobOpening> findWithinTx(Connection connection, UUID tenantId, UUID openingId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, opening_number, job_id, job_version_id, org_unit_id, position_id, "
                        + "state, requested_headcount, filled_headcount, compliance_decision, "
                        + "compliance_decided_at, opens_at, closes_at, version "
                        + "FROM hr_job_openings WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, openingId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    /**
     * Guarded, optimistic state transition with in-transaction evidence.
     *
     * @param outboxEventType null for audit-only transitions (submit/reject)
     */
    public HrJobOpening transition(
            UUID tenantId, UUID openingId, HrJobOpeningState expectedFrom, HrJobOpeningState to,
            long expectedVersion, String complianceDecision, String auditAction, String outboxEventType,
            JsonNode eventPayload, UUID actorId, UUID correlationId, UUID requestId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_job_openings SET state = ?, "
                                + "compliance_decision = COALESCE(?, compliance_decision), "
                                + "compliance_decided_at = CASE WHEN ? IS NULL THEN compliance_decided_at "
                                + "ELSE NOW() END, version = version + 1, updated_at = NOW() "
                                + "WHERE id = ? AND tenant_id = ? AND state = ? AND version = ?")) {
                    ps.setString(1, to.name());
                    if (complianceDecision == null) {
                        ps.setNull(2, Types.VARCHAR);
                        ps.setNull(3, Types.VARCHAR);
                    } else {
                        ps.setString(2, complianceDecision);
                        ps.setString(3, complianceDecision);
                    }
                    ps.setObject(4, openingId);
                    ps.setObject(5, tenantId);
                    ps.setString(6, expectedFrom.name());
                    ps.setLong(7, expectedVersion);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        connection.rollback();
                        throw new IllegalStateException("HRM_OPENING_STATE_CONFLICT: expected state "
                                + expectedFrom + " at version " + expectedVersion);
                    }
                }

                HrJobOpening updated = findWithinTx(connection, tenantId, openingId)
                        .orElseThrow(() -> new IllegalStateException("HRM_OPENING_NOT_FOUND: " + openingId));

                ObjectNode before = JSON.createObjectNode().put("state", expectedFrom.name());
                ObjectNode after = JSON.createObjectNode().put("state", to.name());
                if (complianceDecision != null) {
                    after.put("compliance_decision", complianceDecision);
                }
                HrAuditRecord audit = auditRecord(tenantId, actorId, auditAction, openingId,
                        before, after, "SUCCESS", correlationId, requestId);
                DomainEventEnvelope envelope = outboxEventType == null ? null
                        : envelope(tenantId, outboxEventType, openingId, eventPayload, actorId,
                        correlationId, requestId);
                writeEvidence(connection, audit, envelope);
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof IllegalStateException ise) {
                    throw ise;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Actor attributed by the audit ledger (separation-of-duties source of truth). */
    public Optional<UUID> lastActionActor(UUID tenantId, UUID openingId, String action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<UUID> actor;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT actor_user_id FROM hr_audit_ledger "
                                + "WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? AND action = ? "
                                + "ORDER BY occurred_at DESC, id DESC LIMIT 1")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, RESOURCE_TYPE);
                    ps.setObject(3, openingId);
                    ps.setString(4, action);
                    try (ResultSet rs = ps.executeQuery()) {
                        actor = rs.next() ? Optional.of((UUID) rs.getObject("actor_user_id")) : Optional.empty();
                    }
                }
                connection.commit();
                return actor;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // --- tenant-gate existence checks (service-level cross-tenant denial) ---

    public boolean jobExistsInTenant(UUID tenantId, UUID jobId) {
        return exists("SELECT COUNT(*) FROM hr_jobs WHERE tenant_id = ? AND id = ?", tenantId, jobId);
    }

    public boolean orgUnitExistsInTenant(UUID tenantId, UUID orgUnitId) {
        return exists("SELECT COUNT(*) FROM hr_org_units WHERE tenant_id = ? AND id = ?", tenantId, orgUnitId);
    }

    public boolean positionExistsInTenant(UUID tenantId, UUID positionId) {
        return exists("SELECT COUNT(*) FROM hr_positions WHERE tenant_id = ? AND id = ?", tenantId, positionId);
    }

    public boolean jobVersionExistsInTenant(UUID tenantId, UUID jobId, UUID jobVersionId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean exists;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM hr_job_versions WHERE tenant_id = ? AND id = ? AND job_id = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, jobVersionId);
                    ps.setObject(3, jobId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        exists = rs.getInt(1) == 1;
                    }
                }
                connection.commit();
                return exists;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private boolean exists(String sql, UUID tenantId, UUID id) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean exists;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        exists = rs.getInt(1) == 1;
                    }
                }
                connection.commit();
                return exists;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OPENING_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // --- evidence ---

    private void writeEvidence(Connection connection, HrAuditRecord audit, DomainEventEnvelope envelope) {
        if (envelope == null) {
            // Audit-only fact (create/submit/reject): audit row in the same
            // transaction, deliberately NO outbox event (§13.1, §6.1).
            auditService.appendMutationAudit(connection, audit);
        } else {
            evidenceWriter.writeEvidence(connection, audit, envelope);
        }
    }

    private HrAuditRecord auditRecord(UUID tenantId, UUID actorId, String action, UUID openingId,
                                      JsonNode before, JsonNode after, String result,
                                      UUID correlationId, UUID requestId) {
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, openingId,
                null, null, "OPERATIONAL", null, before, after, result,
                correlationId, requestId, null);
    }

    private DomainEventEnvelope envelope(UUID tenantId, String eventType, UUID openingId,
                                         JsonNode payload, UUID actorId, UUID correlationId, UUID requestId) {
        return new DomainEventEnvelope(UUID.randomUUID(), eventType, 1, RESOURCE_TYPE, openingId,
                tenantId, null, actorId, OffsetDateTime.now().toInstant(), correlationId, null,
                requestId == null ? null : requestId.toString(), "OPERATIONAL", payload);
    }

    // --- jdbc helpers ---

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, OffsetDateTime value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, Timestamp.from(value.toInstant()));
        }
    }

    private static HrJobOpening mapRow(ResultSet rs) throws SQLException {
        OffsetDateTime decidedAt = rs.getObject("compliance_decided_at", OffsetDateTime.class);
        OffsetDateTime opensAt = rs.getObject("opens_at", OffsetDateTime.class);
        OffsetDateTime closesAt = rs.getObject("closes_at", OffsetDateTime.class);
        String complianceDecision = rs.getString("compliance_decision");
        return new HrJobOpening(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("opening_number"),
                (UUID) rs.getObject("job_id"),
                (UUID) rs.getObject("job_version_id"),
                (UUID) rs.getObject("org_unit_id"),
                (UUID) rs.getObject("position_id"),
                HrJobOpeningState.valueOf(rs.getString("state")),
                rs.getInt("requested_headcount"),
                rs.getInt("filled_headcount"),
                complianceDecision,
                decidedAt,
                opensAt,
                closesAt,
                rs.getLong("version"));
    }
}
