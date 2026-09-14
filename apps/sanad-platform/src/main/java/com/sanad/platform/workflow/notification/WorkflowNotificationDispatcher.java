package com.sanad.platform.workflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification delivery dispatcher (GATE R2.5 / AD-3 / AD-16).
 *
 * <p>Claims due intents (PENDING / RETRY_WAIT / FAILED_RETRYABLE with
 * next_attempt_at due) in bounded batches and drives them through the
 * provider SPI in a transaction separate from the originating workflow
 * transition. A provider failure updates the intent only — it never
 * reverses committed workflow state. Retry model: attempt_count cap with
 * exponential backoff via next_attempt_at; retryable failures re-enter
 * the queue, terminal failures stop. At-least-once delivery; dedup stays
 * with the (tenant, deduplication_key) identity.</p>
 *
 * <p>Follows the WorkflowDeadlineEnforcementWorker execution convention:
 * public runnable method (tests invoke directly), bounded batch, per-intent
 * failure isolation, config-gated @Scheduled.</p>
 */
@Component
public class WorkflowNotificationDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowNotificationDispatcher.class);

    private static final int MAX_ATTEMPTS = 8;
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final WorkflowChannelRegistry registry;
    private final boolean enabled;

    public WorkflowNotificationDispatcher(
            JdbcTemplate jdbc,
            WorkflowChannelRegistry registry,
            @Value("${sanad.workflow.notification.dispatcher.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.registry = registry;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${sanad.workflow.notification.dispatcher.interval-ms:30000}",
               initialDelayString = "${sanad.workflow.notification.dispatcher.initial-delay-ms:30000}")
    public void dispatchDueNotifications() {
        if (!enabled) {
            return;
        }
        try {
            dispatchBatch();
        } catch (Exception e) {
            log.error("Notification dispatcher tick failed: {}", e.getMessage(), e);
        }
    }

    /**
     * One bounded dispatch pass. Returns the number of intents processed.
     * A per-intent failure is isolated; the batch continues.
     */
    @Transactional
    public int dispatchBatch() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, tenant_id, channel, event_type, workflow_instance_id, work_item_id,
                       external_action_id, recipient_user_id, recipient_participant_id,
                       recipient_address, title, body, template_key, locale, payload,
                       priority, deep_link, deduplication_key, correlation_id,
                       causation_id, attempt_count, policy_id
                  FROM workflow_notification_intents
                 WHERE delivery_status IN ('PENDING', 'RETRY_WAIT', 'FAILED_RETRYABLE')
                   AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                 ORDER BY created_at
                 LIMIT ?
                """, BATCH_SIZE);
        int processed = 0;
        for (Map<String, Object> row : due) {
            UUID intentId = (UUID) row.get("id");
            UUID tenantId = (UUID) row.get("tenant_id");
            try {
                if (claim(tenantId, intentId)) {
                    deliverOne(tenantId, intentId, row);
                    processed++;
                }
            } catch (Exception e) {
                recordUnexpectedFailure(tenantId, intentId, row, e);
            }
        }
        return processed;
    }

    /** Optimistic claim PENDING/RETRY_WAIT/FAILED_RETRYABLE -> PROCESSING. */
    private boolean claim(UUID tenantId, UUID intentId) {
        return jdbc.update("""
                UPDATE workflow_notification_intents
                   SET delivery_status = 'PROCESSING', updated_at = NOW()
                 WHERE tenant_id = ? AND id = ?
                   AND delivery_status IN ('PENDING', 'RETRY_WAIT', 'FAILED_RETRYABLE')
                   AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                """, tenantId, intentId) == 1;
    }

    private void deliverOne(UUID tenantId, UUID intentId, Map<String, Object> row) {
        WorkflowChannel channel = WorkflowChannel.parse((String) row.get("channel"));
        WorkflowChannelProvider provider;
        try {
            provider = registry.require(channel);
        } catch (IllegalStateException missing) {
            failTerminal(tenantId, intentId, WorkflowDeliveryResult.FC_CONFIG_ERROR,
                    missing.getMessage());
            return;
        }
        int attemptCount = ((Number) row.get("attempt_count")).intValue();
        WorkflowDeliveryRequest request = new WorkflowDeliveryRequest(
                tenantId, intentId, (String) row.get("event_type"),
                (UUID) row.get("recipient_user_id"),
                (UUID) row.get("recipient_participant_id"),
                (String) row.get("recipient_address"),
                (String) row.get("template_key"),
                (String) row.get("locale"),
                (String) row.get("title"),
                (String) row.get("body"),
                (String) row.get("deep_link"),
                (String) row.get("priority"),
                (UUID) row.get("workflow_instance_id"),
                (UUID) row.get("work_item_id"),
                (UUID) row.get("external_action_id"),
                (String) row.get("deduplication_key"),
                (UUID) row.get("correlation_id"),
                (String) row.get("causation_id"),
                toMap(row.get("payload")));
        WorkflowDeliveryResult result = provider.deliver(request);
        if (result.delivered()) {
            jdbc.update("""
                    UPDATE workflow_notification_intents
                       SET delivery_status = 'DELIVERED', delivered_at = NOW(),
                           provider_type = ?, provider_message_id = ?,
                           failure_category = 'NONE', attempt_count = attempt_count + 1,
                           last_error = NULL, next_attempt_at = NULL, updated_at = NOW()
                     WHERE tenant_id = ? AND id = ?
                    """, provider.providerType(), result.providerMessageId(),
                    tenantId, intentId);
            return;
        }
        boolean canRetry = result.retryable() && attemptCount + 1 < MAX_ATTEMPTS;
        if (canRetry) {
            long backoffSeconds = backoffSeconds(attemptCount + 1);
            jdbc.update("""
                    UPDATE workflow_notification_intents
                       SET delivery_status = 'RETRY_WAIT',
                           provider_type = ?, failure_category = ?,
                           attempt_count = attempt_count + 1, last_error = ?,
                           next_attempt_at = ?, updated_at = NOW()
                     WHERE tenant_id = ? AND id = ?
                    """, provider.providerType(), result.failureCategory(),
                    result.failureCategory(),
                    Timestamp.from(Instant.now().plusSeconds(backoffSeconds)),
                    tenantId, intentId);
        } else {
            failTerminal(tenantId, intentId, result.failureCategory(),
                    "Provider failure (retryable=" + result.retryable() + ")");
        }
    }

    private void failTerminal(UUID tenantId, UUID intentId, String category, String message) {
        jdbc.update("""
                UPDATE workflow_notification_intents
                   SET delivery_status = 'FAILED_TERMINAL', failure_category = ?,
                       last_error = ?, attempt_count = attempt_count + 1,
                       next_attempt_at = NULL, updated_at = NOW()
                 WHERE tenant_id = ? AND id = ?
                """, category, message, tenantId, intentId);
    }

    private void recordUnexpectedFailure(UUID tenantId, UUID intentId,
                                         Map<String, Object> row, Exception e) {
        log.error("Notification dispatcher: unexpected failure intent {}: {}",
                intentId, e.getMessage(), e);
        int attemptCount = ((Number) row.get("attempt_count")).intValue();
        if (attemptCount + 1 < MAX_ATTEMPTS) {
            jdbc.update("""
                    UPDATE workflow_notification_intents
                       SET delivery_status = 'RETRY_WAIT', failure_category = ?,
                           last_error = ?, attempt_count = attempt_count + 1,
                           next_attempt_at = ?, updated_at = NOW()
                     WHERE tenant_id = ? AND id = ?
                       AND delivery_status = 'PROCESSING'
                    """, WorkflowDeliveryResult.FC_TRANSIENT, truncate(e.getMessage()),
                    Timestamp.from(Instant.now().plusSeconds(
                            backoffSeconds(attemptCount + 1))), tenantId, intentId);
        } else {
            failTerminal(tenantId, intentId, WorkflowDeliveryResult.FC_TRANSIENT,
                    truncate(e.getMessage()));
        }
    }

    /** Exponential backoff 30s * 2^(n-1), capped at 1 hour (AD-3). */
    static long backoffSeconds(int attempt) {
        long seconds = 30L * (1L << Math.min(attempt - 1, 6));
        return Math.min(seconds, Duration.ofHours(1).toSeconds());
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
