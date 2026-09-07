package com.sanad.platform.hr.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JDBC persistence for the immutable HR audit ledger and its separate
 * delivery state (WS4 Task 4).
 *
 * <p>INSERT-only by contract: the ledger's database trigger rejects
 * UPDATE/DELETE (HRM_AUDIT_IMMUTABLE). All operations run on the
 * {@link Connection} supplied by the caller — the same transaction as the
 * canonical mutation. The central {@link HrRedactionGuard} is applied HERE,
 * at the final gate before durable storage, so even directly-invoked appends
 * cannot persist unmasked sensitive state.</p>
 */
@Repository
public class JdbcHrAuditRepository {

    private final HrRedactionGuard redactionGuard;

    public JdbcHrAuditRepository(HrRedactionGuard redactionGuard) {
        this.redactionGuard = redactionGuard == null ? new HrRedactionGuard() : redactionGuard;
    }

    /** No-arg constructor: uses the default central guard. */
    public JdbcHrAuditRepository() {
        this(new HrRedactionGuard());
    }

    /**
     * Inserts the audit fact (redacted) and returns the generated ledger id.
     * The ledger row is NOT visible to other transactions until the caller
     * commits — it rolls back together with the business mutation.
     */
    public UUID insertLedgerRow(Connection connection, HrAuditRecord record) throws SQLException {
        UUID auditId = UUID.randomUUID();
        String sql = "INSERT INTO hr_audit_ledger " +
                "(id, tenant_id, actor_user_id, action, resource_type, resource_id, organization_id, " +
                "legal_entity_id, data_classification, reason, before_state, after_state, result, " +
                "correlation_id, request_id, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, auditId);
            ps.setObject(2, record.tenantId());
            setNullableUuid(ps, 3, record.actorUserId());
            ps.setString(4, record.action());
            ps.setString(5, record.resourceType());
            setNullableUuid(ps, 6, record.resourceId());
            setNullableUuid(ps, 7, record.organizationId());
            setNullableUuid(ps, 8, record.legalEntityId());
            ps.setString(9, record.dataClassification() == null ? "OPERATIONAL" : record.dataClassification());
            ps.setString(10, record.reason());
            setNullableJson(ps, 11, redactionGuard.redact(record.beforeState()));
            setNullableJson(ps, 12, redactionGuard.redact(record.afterState()));
            ps.setString(13, record.result() == null ? "SUCCESS" : record.result());
            setNullableUuid(ps, 14, record.correlationId());
            setNullableUuid(ps, 15, record.requestId());
            ps.setObject(16, OffsetDateTime.ofInstant(record.occurredAt(), ZoneOffset.UTC));
            ps.executeUpdate();
        }
        return auditId;
    }

    /** Inserts the separate, mutable delivery state row for the audit fact. */
    public void insertDeliveryRow(Connection connection, UUID auditId, UUID tenantId) throws SQLException {
        String sql = "INSERT INTO hr_audit_delivery (audit_id, tenant_id, status) VALUES (?, ?, 'PENDING')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, auditId);
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private void setNullableJson(PreparedStatement ps, int index, JsonNode value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setString(index, value.toString());
        }
    }
}
