package com.sanad.platform.hr.payroll.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * G4-T5 PostgreSQL persistence boundary for payroll lifecycle commands.
 *
 * <p>All methods participate in the caller supplied transaction. The caller
 * must establish the tenant GUC before invoking this repository. There is no
 * independent commit/rollback here.</p>
 */
public class JdbcPayrollLifecycleRepository {

    public PayrollRunRow lockRun(Connection connection, UUID tenantId, UUID runId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, tenant_id, legal_entity_id, period_start, period_end,
                       currency_code, status, version, created_by,
                       reviewed_by, reviewed_at, approved_by, approved_at, exported_at
                  FROM hr_payroll_runs
                 WHERE tenant_id = ?
                   AND id = ?
                 FOR UPDATE
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PayrollRunRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("legal_entity_id", UUID.class),
                        rs.getObject("period_start", LocalDate.class),
                        rs.getObject("period_end", LocalDate.class),
                        rs.getString("currency_code"),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getObject("created_by", UUID.class),
                        rs.getObject("reviewed_by", UUID.class),
                        instant(rs, "reviewed_at"),
                        rs.getObject("approved_by", UUID.class),
                        instant(rs, "approved_at"),
                        instant(rs, "exported_at"));
            }
        }
    }

    public long updateRunStatus(
            Connection connection,
            UUID tenantId,
            UUID runId,
            String targetStatus,
            long expectedVersion,
            UUID actorUserId) throws SQLException {
        String sql = """
                UPDATE hr_payroll_runs
                   SET status = ?,
                       version = version + 1,
                       reviewed_by = CASE WHEN ? = 'REVIEWED' THEN ? ELSE reviewed_by END,
                       reviewed_at = CASE WHEN ? = 'REVIEWED' THEN NOW() ELSE reviewed_at END,
                       approved_by = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_by END,
                       approved_at = CASE WHEN ? = 'APPROVED' THEN NOW() ELSE approved_at END,
                       exported_at = CASE WHEN ? = 'EXPORTED' THEN NOW() ELSE exported_at END,
                       updated_at = NOW()
                 WHERE tenant_id = ?
                   AND id = ?
                   AND version = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetStatus);
            ps.setString(2, targetStatus);
            ps.setObject(3, actorUserId);
            ps.setString(4, targetStatus);
            ps.setString(5, targetStatus);
            ps.setObject(6, actorUserId);
            ps.setString(7, targetStatus);
            ps.setString(8, targetStatus);
            ps.setObject(9, tenantId);
            ps.setObject(10, runId);
            ps.setLong(11, expectedVersion);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_VERSION_CONFLICT: expected version " + expectedVersion);
            }
        }
        return expectedVersion + 1;
    }

    public long incrementForRecalculation(
            Connection connection,
            UUID tenantId,
            UUID runId,
            long expectedVersion) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_payroll_runs
                   SET version = version + 1,
                       updated_at = NOW()
                 WHERE tenant_id = ?
                   AND id = ?
                   AND status = 'CALCULATED'
                   AND version = ?
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, runId);
            ps.setLong(3, expectedVersion);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "HRM_PAYROLL_VERSION_CONFLICT: expected CALCULATED version " + expectedVersion);
            }
        }
        return expectedVersion + 1;
    }

    public void projectItemStatus(
            Connection connection,
            UUID tenantId,
            UUID runId,
            String targetStatus) throws SQLException {
        if (!("REVIEWED".equals(targetStatus)
                || "APPROVED".equals(targetStatus)
                || "EXPORTED".equals(targetStatus)
                || "CANCELLED".equals(targetStatus))) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_payroll_items
                   SET status = ?,
                       version = version + 1,
                       updated_at = NOW()
                 WHERE tenant_id = ?
                   AND payroll_run_id = ?
                   AND status <> ?
                """)) {
            ps.setString(1, targetStatus);
            ps.setObject(2, tenantId);
            ps.setObject(3, runId);
            ps.setString(4, targetStatus);
            ps.executeUpdate();
        }
    }

    public IdempotencyAdmission admit(
            Connection connection,
            UUID tenantId,
            UUID principalId,
            String operationCode,
            String idempotencyKey,
            String fingerprint) throws SQLException {
        for (int attempt = 0; attempt < 2; attempt++) {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO hr_idempotency_records (
                        id, tenant_id, principal_id, operation_code,
                        idempotency_key, request_fingerprint, created_at, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW() + INTERVAL '24 hours')
                    ON CONFLICT (tenant_id, principal_id, operation_code, idempotency_key)
                    DO NOTHING
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, principalId);
                ps.setString(4, operationCode);
                ps.setString(5, idempotencyKey);
                ps.setString(6, fingerprint);
                if (ps.executeUpdate() == 1) {
                    return IdempotencyAdmission.fresh(id);
                }
            }

            ExistingIdempotency existing = lockIdempotency(
                    connection, tenantId, principalId, operationCode, idempotencyKey);
            if (existing == null) {
                continue;
            }
            if (existing.expiresAt().isBefore(Instant.now())) {
                deleteIdempotency(connection, existing.id());
                continue;
            }
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalStateException(
                        "HRM_IDEMPOTENCY_CONFLICT: key reused with a different fingerprint");
            }
            if (existing.responseStatus() == null || existing.responseBody() == null) {
                throw new IllegalStateException(
                        "HRM_IDEMPOTENCY_CONFLICT: operation is still in flight");
            }
            return IdempotencyAdmission.replay(
                    existing.id(), existing.responseStatus(), existing.responseBody());
        }
        throw new IllegalStateException(
                "HRM_IDEMPOTENCY_CONFLICT: unable to resolve idempotency boundary");
    }

    public void completeIdempotency(
            Connection connection,
            UUID id,
            UUID operationReference,
            String responseBody) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE hr_idempotency_records
                   SET operation_reference = ?,
                       response_status = 200,
                       response_body = ?::jsonb
                 WHERE id = ?
                   AND response_status IS NULL
                """)) {
            ps.setObject(1, operationReference);
            ps.setString(2, responseBody);
            ps.setObject(3, id);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "HRM_IDEMPOTENCY_COMPLETE_FAILED: admission is not uniquely completable");
            }
        }
    }

    private ExistingIdempotency lockIdempotency(
            Connection connection,
            UUID tenantId,
            UUID principalId,
            String operationCode,
            String idempotencyKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, request_fingerprint, response_status,
                       response_body::text, expires_at
                  FROM hr_idempotency_records
                 WHERE tenant_id = ?
                   AND principal_id = ?
                   AND operation_code = ?
                   AND idempotency_key = ?
                 FOR UPDATE
                """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, principalId);
            ps.setString(3, operationCode);
            ps.setString(4, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Integer status = (Integer) rs.getObject("response_status");
                return new ExistingIdempotency(
                        rs.getObject("id", UUID.class),
                        rs.getString("request_fingerprint"),
                        status,
                        rs.getString("response_body"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant());
            }
        }
    }

    private void deleteIdempotency(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM hr_idempotency_records WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record PayrollRunRow(
            UUID id,
            UUID tenantId,
            UUID legalEntityId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String currencyCode,
            String status,
            long version,
            UUID createdBy,
            UUID reviewedBy,
            Instant reviewedAt,
            UUID approvedBy,
            Instant approvedAt,
            Instant exportedAt) {
    }

    public record IdempotencyAdmission(
            UUID id,
            boolean replay,
            Integer responseStatus,
            String responseBody) {
        public static IdempotencyAdmission fresh(UUID id) {
            return new IdempotencyAdmission(id, false, null, null);
        }

        public static IdempotencyAdmission replay(UUID id, int status, String body) {
            return new IdempotencyAdmission(id, true, status, body);
        }
    }

    private record ExistingIdempotency(
            UUID id,
            String fingerprint,
            Integer responseStatus,
            String responseBody,
            Instant expiresAt) {
    }
}
