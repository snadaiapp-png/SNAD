package com.sanad.platform.hr.recruitment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.audit.HrAuditService;
import com.sanad.platform.hr.audit.HrRedactionGuard;
import com.sanad.platform.hr.audit.JdbcHrAuditRepository;
import com.sanad.platform.hr.recruitment.domain.HrCandidate;
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
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for the HrCandidate aggregate (HRM-G1 T4).
 *
 * <p>Contact columns carry ONLY ciphertext + blind-index hash (§10). Every
 * mutation runs on a transaction-scoped connection with
 * {@code SET LOCAL app.tenant_id} (FORCE RLS) and appends the audit fact on
 * the SAME connection (fail-closed evidence, no REQUIRES_NEW).</p>
 */
@Repository
public class JdbcHrCandidateRepository {

    public static final String RESOURCE_TYPE = "HR_CANDIDATE";

    public static final String ACTION_CREATED = "HRM.RECRUITMENT.CANDIDATE_CREATED";
    public static final String ACTION_DUPLICATE = "HRM.RECRUITMENT.CANDIDATE_DUPLICATE";
    public static final String ACTION_ARCHIVED = "HRM.RECRUITMENT.CANDIDATE_ARCHIVED";
    public static final String ACTION_READ = "HRM.RECRUITMENT.CANDIDATE_READ";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final HrAuditService auditService;

    @Autowired
    public JdbcHrCandidateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        HrRedactionGuard guard = new HrRedactionGuard();
        this.auditService = new HrAuditService(dataSource, guard, new JdbcHrAuditRepository(guard));
    }

    /** Inserts a new ACTIVE candidate, allocating a tenant-unique candidate number. */
    public HrCandidate insert(HrCandidate candidate, UUID actorId, UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, candidate.tenantId());
                HrCandidate inserted = insertWithinTx(connection, candidate, actorId, correlationId);
                connection.commit();
                return inserted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    private HrCandidate insertWithinTx(Connection connection, HrCandidate candidate, UUID actorId,
                                       UUID correlationId) throws SQLException {
        String year = String.valueOf(OffsetDateTime.now().getYear());
        SQLException lastConflict = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            long seq = countThisYear(connection, candidate.tenantId(), year) + 1 + attempt;
            String number = "CND-" + year + "-" + seq;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO hr_candidates (id, tenant_id, candidate_number, display_name, "
                            + "contact_email_ciphertext, contact_email_hash, contact_phone_ciphertext, "
                            + "contact_phone_hash, pool_state, compensation_expectations_cipher, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
                ps.setObject(1, candidate.id());
                ps.setObject(2, candidate.tenantId());
                ps.setString(3, number);
                ps.setString(4, candidate.displayName());
                setCiphertextAndHash(ps, 5, 6, candidate.email());
                setCiphertextAndHash(ps, 7, 8, candidate.phone());
                ps.setString(9, candidate.poolState());
                if (candidate.compensationExpectationsCiphertext() == null) {
                    ps.setNull(10, Types.VARCHAR);
                } else {
                    ps.setString(10, candidate.compensationExpectationsCiphertext());
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    lastConflict = e;
                    continue;
                }
                throw e;
            }
            writeAudit(connection, audit(candidate.tenantId(), actorId, ACTION_CREATED,
                    candidate.id(), correlationId));
            return new HrCandidate(candidate.id(), candidate.tenantId(), number,
                    candidate.displayName(), candidate.email(), candidate.phone(),
                    candidate.compensationExpectationsCiphertext(), candidate.poolState(), 0L);
        }
        throw new IllegalStateException("HRM_CANDIDATE_NUMBER_ALLOCATION_FAILED: "
                + (lastConflict == null ? "unknown" : lastConflict.getMessage()), lastConflict);
    }

    /** Tenant-scoped load (FORCE RLS applies; foreign-tenant ids resolve empty). */
    public Optional<HrCandidate> find(UUID tenantId, UUID candidateId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                Optional<HrCandidate> found;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, tenant_id, candidate_number, display_name, contact_email_ciphertext, "
                                + "contact_email_hash, contact_phone_ciphertext, contact_phone_hash, "
                                + "pool_state, compensation_expectations_cipher, version "
                                + "FROM hr_candidates WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, candidateId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        found = rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
                    }
                }
                connection.commit();
                return found;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Blind-index duplicate probe (advisory; excludes the candidate itself). */
    public boolean contactHashExists(UUID tenantId, String channelColumn, String hash, UUID excludeCandidateId) {
        String column = "contact_email_hash".equals(channelColumn)
                ? "contact_email_hash" : "contact_phone_hash";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean exists;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT(*) FROM hr_candidates WHERE tenant_id = ? AND " + column + " = ? "
                                + "AND id <> ?")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, hash);
                    ps.setObject(3, excludeCandidateId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        exists = rs.getInt(1) > 0;
                    }
                }
                connection.commit();
                return exists;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Guarded pool-state transition (archive); ARCHIVED/HIRED are terminal. */
    public HrCandidate archive(UUID tenantId, UUID candidateId, String expectedState, UUID actorId,
                               UUID correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE hr_candidates SET pool_state = ?, version = version + 1, updated_at = NOW() "
                                + "WHERE id = ? AND tenant_id = ? AND pool_state = ?")) {
                    ps.setString(1, HrCandidate.STATE_ARCHIVED);
                    ps.setObject(2, candidateId);
                    ps.setObject(3, tenantId);
                    ps.setString(4, expectedState);
                    int updated = ps.executeUpdate();
                    if (updated != 1) {
                        connection.rollback();
                        throw new IllegalStateException("HRM_CANDIDATE_STATE_CONFLICT: expected pool_state "
                                + expectedState);
                    }
                }
                writeAudit(connection, audit(tenantId, actorId, ACTION_ARCHIVED, candidateId, correlationId));
                HrCandidate updated = findWithinTx(connection, tenantId, candidateId)
                        .orElseThrow(() -> new IllegalStateException("HRM_CANDIDATE_NOT_FOUND: " + candidateId));
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof IllegalStateException ise ? ise
                        : e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Duplicate-contact WARNING audit (§5.1: warning + audit, never a merge).
     * Payload carries the channel and hash ONLY — raw values never enter
     * evidence (§10.2).
     */
    public void auditDuplicate(UUID tenantId, UUID candidateId, UUID actorId, UUID correlationId, String channel) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                ObjectNode after = JSON.createObjectNode()
                        .put("duplicate_contact_channel", channel)
                        .put("duplicate_match", "HASH_MATCH");
                writeAudit(connection, new HrAuditRecord(tenantId, actorId, ACTION_DUPLICATE, RESOURCE_TYPE,
                        candidateId, null, null, "PII", null,
                        JSON.createObjectNode(), after, "SUCCESS", correlationId, null, null));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    /** Sensitive-read audit (candidate-detail level, §10.1). */
    public void auditRead(UUID tenantId, UUID candidateId, UUID actorId, UUID correlationId, boolean contactRevealed) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                HrAuditRecord record = audit(tenantId, actorId, ACTION_READ, candidateId, correlationId);
                if (contactRevealed) {
                    // Full-contact reveal detail — metadata only, never values.
                    ObjectNode after = JSON.createObjectNode().put("contact_revealed", true);
                    record = new HrAuditRecord(tenantId, actorId, ACTION_READ, RESOURCE_TYPE, candidateId,
                            null, null, "PII", null,
                            JSON.createObjectNode(), after, "SUCCESS", correlationId, null, null);
                }
                writeAudit(connection, record);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_CANDIDATE_PERSISTENCE_FAILED: " + e.getMessage(), e);
        }
    }

    // --- internals ---

    private Optional<HrCandidate> findWithinTx(Connection connection, UUID tenantId, UUID candidateId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, tenant_id, candidate_number, display_name, contact_email_ciphertext, "
                        + "contact_email_hash, contact_phone_ciphertext, contact_phone_hash, "
                        + "pool_state, compensation_expectations_cipher, version "
                        + "FROM hr_candidates WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, candidateId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    private long countThisYear(Connection connection, UUID tenantId, String year) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM hr_candidates WHERE tenant_id = ? AND candidate_number LIKE ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, "CND-" + year + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void writeAudit(Connection connection, HrAuditRecord record) {
        auditService.appendMutationAudit(connection, record);
    }

    private HrAuditRecord audit(UUID tenantId, UUID actorId, String action, UUID candidateId,
                                UUID correlationId) {
        ObjectNode after = JSON.createObjectNode();
        if (ACTION_DUPLICATE.equals(action)) {
            // Hash-only payload — raw contact values NEVER enter evidence (§10.2).
            after.put("duplicate_of_existing", true);
        }
        return new HrAuditRecord(tenantId, actorId, action, RESOURCE_TYPE, candidateId,
                null, null, ACTION_DUPLICATE.equals(action) ? "PII" : "OPERATIONAL",
                null, JSON.createObjectNode(), after, "SUCCESS", correlationId, null, null);
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }

    private static void setCiphertextAndHash(PreparedStatement ps, int cipherIdx, int hashIdx,
                                             HrCandidate.StoredContact contact) throws SQLException {
        if (contact == null) {
            ps.setNull(cipherIdx, Types.VARCHAR);
            ps.setNull(hashIdx, Types.VARCHAR);
        } else {
            ps.setString(cipherIdx, contact.ciphertext());
            ps.setString(hashIdx, contact.hash());
        }
    }

    private static HrCandidate mapRow(ResultSet rs) throws SQLException {
        String emailCipher = rs.getString("contact_email_ciphertext");
        String emailHash = rs.getString("contact_email_hash");
        String phoneCipher = rs.getString("contact_phone_ciphertext");
        String phoneHash = rs.getString("contact_phone_hash");
        return new HrCandidate(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("candidate_number"),
                rs.getString("display_name"),
                emailCipher == null ? null : new HrCandidate.StoredContact(emailCipher, emailHash),
                phoneCipher == null ? null : new HrCandidate.StoredContact(phoneCipher, phoneHash),
                rs.getString("compensation_expectations_cipher"),
                rs.getString("pool_state"),
                rs.getLong("version"));
    }
}
