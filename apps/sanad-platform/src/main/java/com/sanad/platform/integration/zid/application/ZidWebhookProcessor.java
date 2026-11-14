package com.sanad.platform.integration.zid.application;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.infrastructure.ZidConnectionRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Background processor for the webhook inbox. Deliberately decoupled
 * from ingestion: the controller only writes the inbox row and returns
 * 202; this worker claims rows with FOR UPDATE SKIP LOCKED (the same
 * single-writer pattern as HrOutboxWorker), applies the tenant GUC for
 * each row, and triggers the appropriate verified re-pull — the Zid
 * API (not the webhook payload) is the source of truth for all field
 * values.
 *
 * <p>Retry policy: bounded attempts with exponential backoff
 * (base delay x 2^attempt). After the final failure the event moves to
 * FAILED_MANUAL for operator review (visible in the admin UI).</p>
 */
@Component
public class ZidWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZidWebhookProcessor.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ZidIntegrationProperties properties;
    private final ZidProductSyncService productSyncService;
    private final ZidOrderSyncService orderSyncService;
    private final ZidConnectionRepository connections;
    private final ZidSyncAuditService auditService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ZidWebhookProcessor(NamedParameterJdbcTemplate jdbc,
                               ZidIntegrationProperties properties,
                               ZidProductSyncService productSyncService,
                               ZidOrderSyncService orderSyncService,
                               ZidConnectionRepository connections,
                               ZidSyncAuditService auditService,
                               TenantRlsTransactionContext tenantRlsContext) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.productSyncService = productSyncService;
        this.orderSyncService = orderSyncService;
        this.connections = connections;
        this.auditService = auditService;
        this.tenantRlsContext = tenantRlsContext;
    }

    @Scheduled(
            fixedDelayString = "${snad.zid.webhook-worker-delay-ms:5000}",
            initialDelayString = "${snad.zid.webhook-worker-initial-delay-ms:15000}")
    public void processPendingEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        while (processOne()) {
            // drain the queue
        }
    }

    /** Claims and processes one event; returns false when the queue is empty. */
    boolean processOne() {
        List<Map<String, Object>> claimed = jdbc.queryForList("""
                UPDATE zid_webhook_events
                SET status = 'PROCESSING', attempts = attempts + 1
                WHERE id = (
                    SELECT id FROM zid_webhook_events
                    WHERE status IN ('RECEIVED', 'FAILED')
                      AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                    ORDER BY received_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, tenant_id, connection_id, event_type, attempts
                """, new MapSqlParameterSource());
        if (claimed.isEmpty()) {
            return false;
        }
        Map<String, Object> row = claimed.get(0);
        UUID eventId = (UUID) row.get("id");
        UUID tenantId = (UUID) row.get("tenant_id");
        UUID connectionId = (UUID) row.get("connection_id");
        String eventType = String.valueOf(row.get("event_type"));
        int attempts = ((Number) row.get("attempts")).intValue();

        try {
            tenantRlsContext.applyForCurrentTransaction(tenantId);
            dispatch(eventType, connectionId);
            jdbc.update("""
                    UPDATE zid_webhook_events
                    SET status = 'PROCESSED', processed_at = NOW(), last_error = NULL
                    WHERE id = :id
                    """, new MapSqlParameterSource("id", eventId));
        } catch (RuntimeException e) {
            String reason = ZidSyncAuditService.redact(String.valueOf(e.getMessage()));
            boolean outOfScope = isOutOfAuthScope(reason);
            if (outOfScope) {
                // Rejected credentials will not heal via retries — park for re-auth.
                jdbc.update("""
                        UPDATE zid_webhook_events
                        SET status = 'FAILED_MANUAL', last_error = :lastError
                        WHERE id = :id
                        """, new MapSqlParameterSource()
                        .addValue("id", eventId)
                        .addValue("lastError", "connection requires re-authorization"));
                connections.updateStatus(connectionId, "REAUTH_REQUIRED", null);
            } else if (attempts >= properties.getWebhookMaxAttempts()) {
                jdbc.update("""
                        UPDATE zid_webhook_events
                        SET status = 'FAILED_MANUAL', last_error = :lastError
                        WHERE id = :id
                        """, new MapSqlParameterSource()
                        .addValue("id", eventId)
                        .addValue("lastError", reason));
            } else {
                long backoffSeconds = properties.getWebhookRetryBaseDelay().toSeconds() * (1L << Math.min(attempts, 6));
                jdbc.update("""
                        UPDATE zid_webhook_events
                        SET status = 'FAILED',
                            next_attempt_at = NOW() + make_interval(secs => :backoff),
                            last_error = :lastError
                        WHERE id = :id
                        """, new MapSqlParameterSource()
                        .addValue("id", eventId)
                        .addValue("backoff", backoffSeconds)
                        .addValue("lastError", reason));
            }
            auditService.record(tenantId, connectionId, ZidSyncAuditService.SyncType.WEBHOOK,
                    eventType, ZidSyncAuditService.Direction.WEBHOOK_INGEST,
                    ZidSyncAuditService.Status.FAILED, 0, 1, reason, null);
            log.info("Zid webhook processing failed: event={} attempt={} reason={}", eventId, attempts, reason);
        }
        return true;
    }

    /**
     * Route the event family to the matching VERIFIED pull. Out-of-order
     * events are naturally safe: every dispatch re-pulls current state,
     * so a stale event cannot overwrite fresher data.
     */
    private void dispatch(String eventType, UUID connectionId) {
        String normalized = eventType == null ? "" : eventType.toLowerCase();
        if (normalized.contains("product")) {
            productSyncService.syncConnection(connectionId);
        } else if (normalized.contains("order") || normalized.contains("cart")) {
            orderSyncService.syncConnection(connectionId);
        } else {
            // Unknown event families are recorded but not acted upon — no guessing.
            log.info("Zid webhook event type '{}' stored without dispatch (no verified mapping)", eventType);
        }
    }

    private static boolean isOutOfAuthScope(String reason) {
        return reason != null && reason.contains("401");
    }
}
