package com.sanad.platform.hr.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.audit.PlatformAuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * HR audit delivery worker (WS4 Task 6).
 *
 * <p>Delivers committed {@code hr_audit_delivery} rows to the shared
 * {@link PlatformAuditSink}. Same short-transaction model as
 * {@link com.sanad.platform.hr.integration.HrOutboxWorker}:</p>
 * <ol>
 *   <li>SHORT claim transaction (tenant-scoped via {@code SET LOCAL
 *       app.tenant_id}): exclusive {@code claim_token} on one claimable row
 *       ({@code PENDING}/{@code FAILED} past {@code available_at}, or a stale
 *       claim past {@code claim_expires_at}) via {@code FOR UPDATE SKIP
 *       LOCKED}. FORCE RLS stays fully enforced — a contextless session sees
 *       nothing.</li>
 *   <li>NO transaction: dispatch to the platform audit sink. The metadata
 *       read happens in its own SHORT tenant-scoped transaction that commits
 *       BEFORE {@code sink.accept} is invoked.</li>
 *   <li>SHORT finalize transaction (tenant-scoped): claim-ownership-verified
 *       {@code DELIVERED}, or failure with {@code attempt_count+1}, backoff
 *       on {@code available_at} ({@code FAILED}), and {@code DEAD_LETTER}
 *       once attempts are exhausted.</li>
 * </ol>
 *
 * <p>The dispatched record carries identifiers / classification / reason /
 * correlation metadata ONLY — ledger state snapshots are never shipped to the
 * sink, so raw restricted values cannot reach the platform audit trail.</p>
 */
@Component
public class HrAuditDeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(HrAuditDeliveryWorker.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final PlatformAuditSink sink;
    private final String workerId;
    private final int claimTimeoutSeconds;

    @Autowired
    public HrAuditDeliveryWorker(
            DataSource dataSource,
            ObjectMapper objectMapper,
            PlatformAuditSink sink,
            @Value("${sanad.hr.audit-worker.id:hr-audit-worker-1}") String workerId,
            @Value("${sanad.hr.audit-worker.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    /** One claimed audit delivery row with its ownership token. */
    public record Claim(UUID tenantId, UUID auditId, UUID claimToken) {
    }

    /**
     * Claims exactly one claimable audit delivery row across tenants, one
     * SHORT tenant-scoped transaction at a time. Returns {@code null} when
     * nothing is claimable.
     */
    public Claim claimNext() {
        for (UUID tenantId : listTenantIds()) {
            Claim claim = claimNextForTenant(tenantId);
            if (claim != null) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Claims one claimable audit delivery row for the given tenant in a SHORT
     * transaction that commits before dispatch. Returns {@code null} when the
     * tenant has nothing claimable.
     */
    public Claim claimNextForTenant(UUID tenantId) {
        UUID claimToken = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(CLAIM_SQL)) {
                    ps.setObject(1, claimToken);
                    ps.setString(2, workerId);
                    ps.setInt(3, claimTimeoutSeconds);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return null;
                        }
                        UUID auditId = UUID.fromString(rs.getString("audit_id"));
                        connection.commit();
                        return new Claim(tenantId, auditId, claimToken);
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_AUDIT_DELIVERY_CLAIM_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_DELIVERY_CLAIM_FAILED: " + e.getMessage(), e);
        }
    }

    /** Marks the claimed audit delivery DELIVERED — only the valid claimant can finalize. */
    public boolean finalizeDelivered(UUID tenantId, UUID auditId, UUID claimToken) {
        return finalize(tenantId, auditId, claimToken, DELIVERED_SQL, null);
    }

    /**
     * Records a failed delivery attempt: increments {@code attempt_count},
     * sets exponential backoff on {@code available_at} (status {@code FAILED}),
     * and transitions to {@code DEAD_LETTER} when attempts are exhausted.
     */
    public boolean finalizeFailed(UUID tenantId, UUID auditId, UUID claimToken, String errorCode) {
        return finalize(tenantId, auditId, claimToken, FAILED_SQL, errorCode);
    }

    private boolean finalize(UUID tenantId, UUID auditId, UUID claimToken, String sql, String errorCode) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (errorCode != null) {
                        ps.setString(1, truncate(errorCode, 80));
                        ps.setObject(2, auditId);
                        ps.setObject(3, claimToken);
                    } else {
                        ps.setObject(1, auditId);
                        ps.setObject(2, claimToken);
                    }
                    boolean claimed = ps.executeUpdate() == 1;
                    connection.commit();
                    return claimed;
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_AUDIT_DELIVERY_FINALIZE_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_DELIVERY_FINALIZE_FAILED: " + e.getMessage(), e);
        }
    }

    /**
     * Claims one audit delivery row, dispatches OUTSIDE any transaction, then
     * finalizes in a SHORT transaction. Returns {@code true} when a row was
     * processed.
     */
    public boolean processOnce() {
        Claim claim = claimNext();
        if (claim == null) {
            return false;
        }
        try {
            dispatch(claim);
            finalizeDelivered(claim.tenantId(), claim.auditId(), claim.claimToken());
        } catch (RuntimeException e) {
            log.warn("HR audit delivery failed for audit {} (worker {}): {}",
                    claim.auditId(), workerId, e.getMessage());
            finalizeFailed(claim.tenantId(), claim.auditId(), claim.claimToken(), "DISPATCH_FAILED");
        }
        return true;
    }

    /** Production polling loop — each iteration owns its own transactions. */
    @Scheduled(fixedDelayString = "${sanad.hr.audit-worker.fixed-delay-ms:2000}",
               initialDelayString = "${sanad.hr.audit-worker.initial-delay-ms:5000}")
    public void processDeliveries() {
        while (processOnce()) {
            // drain deliverable audit rows one short-transaction claim at a time
        }
    }

    private void dispatch(Claim claim) {
        DispatchMetadata metadata = readMetadata(claim);
        // Metadata-only dispatch — identifiers/classification/reason. Ledger
        // state snapshots are deliberately NOT shipped to the sink.
        String sanitizedDetails = sanitizedDetails(metadata.resourceType, metadata.resourceId,
                metadata.classification, metadata.reason, metadata.result);
        sink.accept(new PlatformAuditSink.AuditSinkRecord(
                claim.tenantId(),
                metadata.organizationId,
                metadata.actorUserId,
                metadata.action,
                metadata.resourceType,
                metadata.resourceId,
                metadata.occurredAt,
                metadata.correlationId == null ? null : metadata.correlationId.toString(),
                metadata.result,
                sanitizedDetails));
    }

    /**
     * Reads the ledger metadata in a SHORT tenant-scoped read transaction
     * that commits BEFORE the sink call — never hold a DB transaction open
     * during external dispatch.
     */
    private DispatchMetadata readMetadata(Claim claim) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, claim.tenantId());
                try (PreparedStatement ps = connection.prepareStatement(METADATA_SQL)) {
                    ps.setObject(1, claim.auditId());
                    ps.setObject(2, claim.claimToken());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException(
                                    "HRM_AUDIT_DELIVERY_METADATA_MISSING: " + claim.auditId());
                        }
                        DispatchMetadata metadata = new DispatchMetadata(
                                uuidOrNull(rs, "organization_id"),
                                uuidOrNull(rs, "actor_user_id"),
                                rs.getString("action"),
                                rs.getString("resource_type"),
                                uuidOrNull(rs, "resource_id"),
                                rs.getTimestamp("occurred_at").toInstant(),
                                uuidOrNull(rs, "correlation_id"),
                                rs.getString("result"),
                                rs.getString("data_classification"),
                                rs.getString("reason"));
                        connection.commit();
                        return metadata;
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_AUDIT_DELIVERY_DISPATCH_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_DELIVERY_DISPATCH_FAILED: " + e.getMessage(), e);
        }
    }

    private String sanitizedDetails(String resourceType, UUID resourceId,
                                    String classification, String reason, String result) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("resourceType", resourceType);
            node.put("resourceId", resourceId == null ? null : resourceId.toString());
            node.put("classification", classification);
            node.put("reason", reason);
            node.put("result", result);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("HRM_AUDIT_DELIVERY_SANITIZE_FAILED: " + e.getMessage(), e);
        }
    }

    private record DispatchMetadata(
            UUID organizationId,
            UUID actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Instant occurredAt,
            UUID correlationId,
            String result,
            String classification,
            String reason) {
    }

    /**
     * Tenant ids to sweep (see HrOutboxWorker — authoritative registry, no
     * tenant RLS on the registry itself; every HR-table transaction stays
     * tenant-scoped via {@code SET LOCAL}).
     */
    private List<UUID> listTenantIds() {
        List<UUID> tenantIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT id FROM tenants ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tenantIds.add(UUID.fromString(rs.getString("id")));
            }
            return tenantIds;
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_AUDIT_DELIVERY_TENANT_SWEEP_FAILED: " + e.getMessage(), e);
        }
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT d2.audit_id FROM hr_audit_delivery d2
                WHERE (d2.status IN ('PENDING', 'FAILED') AND d2.available_at <= NOW())
                   OR (d2.claim_token IS NOT NULL AND d2.claim_expires_at < NOW())
                ORDER BY d2.available_at
                FOR UPDATE OF d2 SKIP LOCKED
                LIMIT 1
            )
            UPDATE hr_audit_delivery SET
                claim_token = ?,
                claimed_by = ?,
                claim_expires_at = NOW() + (? * INTERVAL '1 second')
            FROM candidate
            WHERE hr_audit_delivery.audit_id = candidate.audit_id
            RETURNING hr_audit_delivery.audit_id, hr_audit_delivery.tenant_id
            """;

    private static final String DELIVERED_SQL = """
            UPDATE hr_audit_delivery SET
                status = 'DELIVERED',
                delivered_at = NOW(),
                claim_token = NULL,
                claimed_by = NULL,
                claim_expires_at = NULL
            WHERE audit_id = ? AND claim_token = ? AND status IN ('PENDING', 'FAILED')
            """;

    private static final String FAILED_SQL = """
            UPDATE hr_audit_delivery SET
                attempt_count = attempt_count + 1,
                last_error_code = ?,
                status = CASE WHEN attempt_count + 1 >= max_attempts THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                available_at = CASE WHEN attempt_count + 1 >= max_attempts THEN available_at
                                    ELSE NOW() + (LEAST(600, POWER(2, attempt_count + 1) * 5) * INTERVAL '1 second') END,
                claim_token = NULL,
                claimed_by = NULL,
                claim_expires_at = NULL
            WHERE audit_id = ? AND claim_token = ? AND status IN ('PENDING', 'FAILED')
            """;

    private static final String METADATA_SQL = """
            SELECT l.tenant_id, l.actor_user_id, l.action, l.resource_type, l.resource_id,
                   l.organization_id, l.legal_entity_id, l.data_classification, l.reason,
                   l.result, l.occurred_at, l.correlation_id
            FROM hr_audit_delivery d
            JOIN hr_audit_ledger l ON l.id = d.audit_id
            WHERE d.audit_id = ? AND d.claim_token = ?
            """;
}
