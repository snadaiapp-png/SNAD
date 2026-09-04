package com.sanad.platform.hr.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — read-side service for the immutable audit ledger
 * (GET /api/v2/hr/audit).
 *
 * <p>Reads run under the caller's tenant GUC so fail-closed FORCE RLS
 * governs visibility. The projection carries identifiers, classification
 * and metadata only; state snapshots were already redacted at write time
 * by the {@link HrRedactionGuard} and are returned as fetched.
 */
@Service
public class HrAuditReadService {

    private final DataSource dataSource;

    @Autowired
    public HrAuditReadService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<HrAuditEntry> findRecent(UUID tenantId, int limit, String resourceType) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                String sql = "SELECT id, tenant_id, actor_user_id, action, resource_type, resource_id, " +
                        "data_classification, reason, result, correlation_id, occurred_at " +
                        "FROM hr_audit_ledger WHERE tenant_id = ?" +
                        (resourceType != null && !resourceType.isBlank() ? " AND resource_type = ?" : "") +
                        " ORDER BY occurred_at DESC LIMIT ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    int idx = 2;
                    if (resourceType != null && !resourceType.isBlank()) {
                        ps.setString(idx++, resourceType);
                    }
                    ps.setInt(idx, Math.min(Math.max(limit, 1), 200));
                    try (ResultSet rs = ps.executeQuery()) {
                        List<HrAuditEntry> entries = new ArrayList<>();
                        while (rs.next()) {
                            Instant occurredAt = rs.getTimestamp("occurred_at") == null
                                    ? null
                                    : rs.getTimestamp("occurred_at").toInstant();
                            entries.add(new HrAuditEntry(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("action"),
                                    rs.getString("resource_type"),
                                    rs.getObject("resource_id", UUID.class),
                                    rs.getString("data_classification"),
                                    rs.getString("reason"),
                                    rs.getString("result"),
                                    rs.getObject("actor_user_id", UUID.class),
                                    occurredAt));
                        }
                        connection.commit();
                        return entries;
                    }
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw new IllegalStateException("HRM_AUDIT_READ_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_READ_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Safe audit projection — identifiers/classification/metadata only.
     * before_state / after_state are deliberately excluded from the v2
     * surface: raw JSON snapshots must never be exposed through the API.
     */
    public record HrAuditEntry(
            UUID auditId,
            String action,
            String resourceType,
            UUID resourceId,
            String dataClassification,
            String reason,
            String result,
            UUID actorUserId,
            Instant occurredAt) {
    }
}
