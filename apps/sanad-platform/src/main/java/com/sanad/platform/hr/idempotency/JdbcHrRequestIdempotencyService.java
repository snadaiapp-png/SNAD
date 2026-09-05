package com.sanad.platform.hr.idempotency;

import com.sanad.platform.idempotency.IdempotencyBeginResult;
import com.sanad.platform.idempotency.RequestIdempotencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable producer-local HR request idempotency service (WS4 Task 8).
 *
 * <p>Implements the shared {@link RequestIdempotencyService} contract over
 * the existing {@code hr_idempotency_records} table (V20260903_2 — no
 * competing storage). Deterministic rules, applied by RE-READING the
 * canonical row after an insert conflict (a unique violation is never
 * silently swallowed):</p>
 * <ul>
 *   <li>same key + same fingerprint + completed → replay the stored result</li>
 *   <li>same key + different fingerprint → {@code HRM_IDEMPOTENCY_CONFLICT}</li>
 *   <li>same key + in-flight (no completed result yet) → deterministic
 *       retry-later ({@code alreadyExists=true} with no prior status)</li>
 *   <li>expired completed record → the new operation wins (deterministic
 *       CAS reclaim)</li>
 * </ul>
 *
 * <p>Concurrency: the unique constraint
 * {@code uq_hr_idempotency_boundary (tenant_id, principal_id, operation_code,
 * idempotency_key)} makes concurrent {@code begin()} races safe — exactly one
 * logical operation wins. The request fingerprint (SHA-256) is supplied by
 * the caller.</p>
 *
 * <p>Transaction/RLS semantics: {@code begin()} runs each statement in a
 * SHORT tenant-scoped transaction ({@code SET LOCAL app.tenant_id}). The
 * DataSource in production is the RLS-wrapped platform DataSource, so
 * {@link #complete} and {@link #fail} run inside the caller's tenant context;
 * they assert their exact one-row effect and fail closed otherwise.</p>
 */
@Service
public class JdbcHrRequestIdempotencyService implements RequestIdempotencyService {

    private static final int MAX_RECLAIM_ATTEMPTS = 3;

    private final DataSource dataSource;

    @Autowired
    public JdbcHrRequestIdempotencyService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public IdempotencyBeginResult begin(UUID tenantId, UUID principalId, String operation,
                                        String idempotencyKey, String requestFingerprint) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(principalId, "principalId");
        requireText(operation, "operation");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(requestFingerprint, "requestFingerprint");

        UUID operationId = UUID.randomUUID();
        for (int attempt = 0; attempt < MAX_RECLAIM_ATTEMPTS; attempt++) {
            // 1. Try to start a NEW operation (in-flight row). The unique
            //    boundary constraint arbitrates concurrent begins.
            boolean inserted = insertInFlight(tenantId, principalId, operation, idempotencyKey,
                    requestFingerprint, operationId);
            if (inserted) {
                return new IdempotencyBeginResult(operationId, false, null, null);
            }

            // 2. Conflict — re-read the canonical row and apply the rules.
            ExistingRow row = readExisting(tenantId, principalId, operation, idempotencyKey);
            if (row == null) {
                // The conflicting insert vanished (loser of a reclaim race) —
                // retry the insert.
                continue;
            }
            if (row.expired()) {
                reclaimExpired(row.id());
                continue; // expired row removed (or lost the race) — re-run deterministically
            }
            if (row.responseStatus() == null) {
                // In-flight: deterministic retry-later (no completed result to replay).
                return new IdempotencyBeginResult(row.id(), true, null, null);
            }
            if (!row.fingerprint().equals(requestFingerprint)) {
                throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: idempotency key was already used "
                        + "with a different request fingerprint (key=" + idempotencyKey + ")");
            }
            return new IdempotencyBeginResult(row.id(), true, row.responseStatus(), row.responseBody());
        }
        throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: unable to resolve idempotency boundary for key "
                + idempotencyKey + " after " + MAX_RECLAIM_ATTEMPTS + " attempts");
    }

    @Override
    public void complete(UUID operationId, int statusCode, String responseBody) {
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(COMPLETE_SQL)) {
                    ps.setInt(1, statusCode);
                    ps.setString(2, responseBody);
                    ps.setObject(3, operationId);
                    updated = ps.executeUpdate();
                }
                connection.commit();
                if (updated != 1) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_COMPLETE_FAILED: operation " + operationId
                            + " not visible in the current tenant context (rows=" + updated + ")");
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IDEMPOTENCY_COMPLETE_FAILED: " + e.getMessage(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // closing connection cleanup
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_COMPLETE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Marks the operation failed by deleting the in-flight row, so a retry
     * can start a fresh operation (deterministic; no orphan in-flight rows).
     */
    @Override
    public void fail(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int deleted;
                try (PreparedStatement ps = connection.prepareStatement(FAIL_SQL)) {
                    ps.setObject(1, operationId);
                    deleted = ps.executeUpdate();
                }
                connection.commit();
                if (deleted > 1) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_FAIL_FAILED: ambiguous operation "
                            + operationId + " (rows=" + deleted + ")");
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IDEMPOTENCY_FAIL_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_FAIL_FAILED: " + e.getMessage(), e);
        }
    }

    // ==================== internals ====================

    private boolean insertInFlight(UUID tenantId, UUID principalId, String operation,
                                   String idempotencyKey, String fingerprint, UUID operationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                boolean inserted;
                try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
                    ps.setObject(1, operationId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, principalId);
                    ps.setString(4, operation);
                    ps.setString(5, idempotencyKey);
                    ps.setString(6, fingerprint);
                    inserted = ps.executeUpdate() == 1;
                }
                connection.commit();
                return inserted;
            } catch (SQLException e) {
                connection.rollback();
                if (isUniqueViolation(e)) {
                    return false; // conflict — caller re-reads the canonical row
                }
                throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
        }
    }

    private ExistingRow readExisting(UUID tenantId, UUID principalId, String operation, String idempotencyKey) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                ExistingRow row;
                try (PreparedStatement ps = connection.prepareStatement(SELECT_SQL)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, principalId);
                    ps.setString(3, operation);
                    ps.setString(4, idempotencyKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        row = rs.next()
                                ? new ExistingRow(
                                        UUID.fromString(rs.getString("id")),
                                        rs.getString("request_fingerprint"),
                                        rs.getObject("response_status") == null
                                                ? null : rs.getInt("response_status"),
                                        rs.getString("response_body"),
                                        rs.getTimestamp("expires_at").toInstant())
                                : null;
                    }
                }
                connection.commit();
                return row;
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
        }
    }

    /** Deterministic CAS reclaim: only deletes while the row is still expired. */
    private void reclaimExpired(UUID rowId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(RECLAIM_SQL)) {
                    ps.setObject(1, rowId);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_IDEMPOTENCY_BEGIN_FAILED: " + e.getMessage(), e);
        }
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        SQLException current = e;
        while (current != null) {
            if ("23505".equals(current.getSQLState())) {
                return true;
            }
            current = current.getNextException() != null ? current.getNextException()
                    : (current.getCause() instanceof SQLException se ? se : null);
        }
        return false;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HRM_IDEMPOTENCY_INVALID: " + field + " is required");
        }
    }

    private record ExistingRow(
            UUID id,
            String fingerprint,
            Integer responseStatus,
            String responseBody,
            java.time.Instant expiresAt) {

        boolean expired() {
            return expiresAt != null && expiresAt.isBefore(java.time.Instant.now());
        }
    }

    private static final String INSERT_SQL = """
            INSERT INTO hr_idempotency_records
                (id, tenant_id, principal_id, operation_code, idempotency_key, request_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String SELECT_SQL = """
            SELECT id, request_fingerprint, response_status, response_body, expires_at
            FROM hr_idempotency_records
            WHERE tenant_id = ? AND principal_id = ? AND operation_code = ? AND idempotency_key = ?
            """;

    private static final String RECLAIM_SQL = """
            DELETE FROM hr_idempotency_records
            WHERE id = ? AND expires_at <= NOW()
            """;

    private static final String COMPLETE_SQL = """
            UPDATE hr_idempotency_records
            SET response_status = ?, response_body = ?::jsonb, expires_at = NOW() + INTERVAL '24 hours'
            WHERE id = ? AND response_status IS NULL
            """;

    private static final String FAIL_SQL = """
            DELETE FROM hr_idempotency_records WHERE id = ? AND response_status IS NULL
            """;
}
