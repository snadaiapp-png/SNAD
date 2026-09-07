package com.sanad.platform.hr.integration;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * HR domain-event outbox delivery worker (WS4 Task 6).
 *
 * <p><strong>Transaction model</strong> — never hold a DB transaction open
 * during external dispatch:</p>
 * <ol>
 *   <li>SHORT claim transaction (tenant-scoped): {@code SET LOCAL
 *       app.tenant_id} then claim one claimable row ({@code READY} past
 *       {@code available_at}, or a stale claim past {@code claim_expires_at})
 *       via {@code FOR UPDATE SKIP LOCKED} with an exclusive
 *       {@code claim_token}. Commit — the row lock is released. The GUC is
 *       transaction-local, so no tenant context ever leaks through the pool
 *       and FORCE RLS stays fully enforced (fail-closed for a contextless
 *       session).</li>
 *   <li>NO transaction: dispatch to every registered
 *       {@link HrOutboxEventConsumer} (AT_LEAST_ONCE; consumers are
 *       idempotent).</li>
 *   <li>SHORT finalize transaction (tenant-scoped): verify claim ownership
 *       ({@code event_id + claim_token + status='CLAIMED'}) and persist the
 *       outcome — {@code DELIVERED}, or failure with
 *       {@code attempt_count+1}, exponential backoff on {@code available_at},
 *       and {@code DEAD_LETTER} once attempts are exhausted.</li>
 * </ol>
 */
@Component
public class HrOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(HrOutboxWorker.class);

    private final DataSource dataSource;
    private final List<HrOutboxEventConsumer> consumers;
    private final String workerId;
    private final int claimTimeoutSeconds;

    @Autowired
    public HrOutboxWorker(
            DataSource dataSource,
            List<HrOutboxEventConsumer> consumers,
            @Value("${sanad.hr.outbox-worker.id:hr-outbox-worker-1}") String workerId,
            @Value("${sanad.hr.outbox-worker.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    /** One claimable outbox row with its dispatch token. */
    public record Claim(UUID eventId, UUID claimToken, HrOutboxEvent event) {
    }

    /**
     * Claims exactly one claimable event across tenants, one SHORT
     * tenant-scoped transaction at a time. Returns {@code null} when nothing
     * is claimable.
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
     * Claims one claimable event for the given tenant in a SHORT transaction
     * that commits before any dispatch happens. Returns {@code null} when the
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
                        HrOutboxEvent event = new HrOutboxEvent(
                                UUID.fromString(rs.getString("event_id")),
                                UUID.fromString(rs.getString("tenant_id")),
                                rs.getString("event_type"),
                                rs.getInt("event_version"),
                                rs.getString("aggregate_type"),
                                uuidOrNull(rs, "aggregate_id"),
                                uuidOrNull(rs, "organization_id"),
                                uuidOrNull(rs, "actor_user_id"),
                                rs.getTimestamp("occurred_at").toInstant(),
                                uuidOrNull(rs, "correlation_id"),
                                uuidOrNull(rs, "causation_id"),
                                rs.getString("idempotency_key"),
                                rs.getString("data_classification"),
                                rs.getString("payload"));
                        connection.commit();
                        return new Claim(event.eventId(), claimToken, event);
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("HRM_OUTBOX_CLAIM_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_OUTBOX_CLAIM_FAILED: " + e.getMessage(), e);
        }
    }

    /** Marks the claimed event DELIVERED — only the valid claimant can finalize. */
    public boolean finalizeDelivered(UUID tenantId, UUID eventId, UUID claimToken) {
        return finalize(tenantId, eventId, claimToken, DELIVERED_SQL, "HRM_OUTBOX_FINALIZE_FAILED", null);
    }

    /**
     * Records a failed dispatch attempt: increments {@code attempt_count},
     * sets exponential backoff on {@code available_at}, and transitions to
     * {@code DEAD_LETTER} when attempts are exhausted. Only the valid
     * claimant can finalize.
     */
    public boolean finalizeFailed(UUID tenantId, UUID eventId, UUID claimToken, String errorCode) {
        return finalize(tenantId, eventId, claimToken, FAILED_SQL, "HRM_OUTBOX_FINALIZE_FAILED", errorCode);
    }

    private boolean finalize(UUID tenantId, UUID eventId, UUID claimToken,
                             String sql, String errorPrefix, String errorCode) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, tenantId);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    if (errorCode != null) {
                        ps.setString(1, truncate(errorCode, 80));
                        ps.setObject(2, eventId);
                        ps.setObject(3, claimToken);
                    } else {
                        ps.setObject(1, eventId);
                        ps.setObject(2, claimToken);
                    }
                    boolean claimed = ps.executeUpdate() == 1;
                    connection.commit();
                    return claimed;
                }
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException(errorPrefix + ": " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(errorPrefix + ": " + e.getMessage(), e);
        }
    }

    /**
     * Claims one event, dispatches OUTSIDE any transaction, then finalizes in
     * a SHORT transaction. Returns {@code true} when an event was processed.
     */
    public boolean processOnce() {
        Claim claim = claimNext();
        if (claim == null) {
            return false;
        }
        UUID tenantId = claim.event().tenantId();
        try {
            for (HrOutboxEventConsumer consumer : consumers) {
                consumer.onEvent(claim.event());
            }
            finalizeDelivered(tenantId, claim.eventId(), claim.claimToken());
        } catch (RuntimeException e) {
            log.warn("HR outbox dispatch failed for event {} (worker {}): {}",
                    claim.eventId(), workerId, e.getMessage());
            finalizeFailed(tenantId, claim.eventId(), claim.claimToken(), "DISPATCH_FAILED");
        }
        return true;
    }

    /** Production polling loop — each iteration owns its own transactions. */
    @Scheduled(fixedDelayString = "${sanad.hr.outbox-worker.fixed-delay-ms:2000}",
               initialDelayString = "${sanad.hr.outbox-worker.initial-delay-ms:5000}")
    public void processOutboxEvents() {
        while (processOnce()) {
            // drain claimable events one short-transaction claim at a time
        }
    }

    /**
     * Tenant ids to sweep. The {@code tenants} table is the authoritative
     * registry and carries no tenant RLS; every subsequent claim/finalize
     * transaction is still tenant-scoped through {@code SET LOCAL}, so FORCE
     * RLS remains fully enforced on all HR tables.
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
            throw new IllegalStateException("HRM_OUTBOX_TENANT_SWEEP_FAILED: " + e.getMessage(), e);
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
                SELECT event_id FROM hr_domain_event_outbox
                WHERE (status = 'READY' AND available_at <= NOW())
                   OR (status = 'CLAIMED' AND claim_expires_at < NOW())
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE hr_domain_event_outbox SET
                status = 'CLAIMED',
                claim_token = ?,
                claimed_by = ?,
                claim_expires_at = NOW() + (? * INTERVAL '1 second')
            FROM candidate
            WHERE hr_domain_event_outbox.event_id = candidate.event_id
            RETURNING hr_domain_event_outbox.event_id, hr_domain_event_outbox.tenant_id, event_type, event_version,
                      aggregate_type, aggregate_id, organization_id, actor_user_id, occurred_at, correlation_id,
                      causation_id, idempotency_key, data_classification, payload
            """;

    private static final String DELIVERED_SQL = """
            UPDATE hr_domain_event_outbox SET
                status = 'DELIVERED',
                delivered_at = NOW(),
                claim_token = NULL,
                claimed_by = NULL,
                claim_expires_at = NULL
            WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
            """;

    private static final String FAILED_SQL = """
            UPDATE hr_domain_event_outbox SET
                attempt_count = attempt_count + 1,
                last_error_code = ?,
                status = CASE WHEN attempt_count + 1 >= max_attempts THEN 'DEAD_LETTER' ELSE 'READY' END,
                available_at = CASE WHEN attempt_count + 1 >= max_attempts THEN available_at
                                    ELSE NOW() + (LEAST(600, POWER(2, attempt_count + 1) * 5) * INTERVAL '1 second') END,
                claim_token = NULL,
                claimed_by = NULL,
                claim_expires_at = NULL
            WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
            """;
}
