package com.sanad.platform.workflow.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Notification orchestration boundary (design decision K3). Workflow records
 * notification intents inside the committed transition; delivery happens
 * separately and a delivery failure updates the intent — it never reverses a
 * workflow state change. IN_APP is the primary qualifying channel; EMAIL and
 * WEBHOOK are extensible.
 */
@Service
public class WorkflowNotificationService {

    private final JdbcTemplate jdbc;

    public WorkflowNotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Enqueues an intent. Deduplication keys collapse repeated intents (for
     * example a reminder re-fire) to one notification.
     */
    @Transactional
    public UUID enqueue(UUID tenantId, String eventType, UUID workflowInstanceId,
                        UUID workItemId, UUID recipientUserId, String channel,
                        String deduplicationKey) {
        if (deduplicationKey != null) {
            List<UUID> existing = jdbc.queryForList("""
                    SELECT id FROM workflow_notification_intents
                    WHERE tenant_id = ? AND deduplication_key = ?
                    """, UUID.class, tenantId, deduplicationKey);
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_notification_intents (
                    id, tenant_id, event_type, workflow_instance_id, work_item_id,
                    recipient_user_id, channel, deduplication_key, delivery_status,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW(), NOW())
                """, id, tenantId, eventType, workflowInstanceId, workItemId,
                recipientUserId, channel != null ? channel : "IN_APP", deduplicationKey);
        return id;
    }

    /**
     * Attempts delivery of one intent. A provider failure records the error
     * and FAILED status — callers must not treat this as a workflow failure.
     */
    @Transactional
    public boolean attemptDelivery(UUID tenantId, UUID intentId,
                                   DeliveryProvider provider) {
        var intent = jdbc.queryForMap("""
                SELECT event_type, recipient_user_id, attempt_count
                FROM workflow_notification_intents
                WHERE tenant_id = ? AND id = ?
                """, tenantId, intentId);
        try {
            provider.deliver(tenantId, intentId,
                    (String) intent.get("event_type"), (UUID) intent.get("recipient_user_id"));
            jdbc.update("""
                    UPDATE workflow_notification_intents
                    SET delivery_status = 'SENT', attempt_count = attempt_count + 1, updated_at = NOW()
                    WHERE tenant_id = ? AND id = ?
                    """, tenantId, intentId);
            return true;
        } catch (RuntimeException e) {
            jdbc.update("""
                    UPDATE workflow_notification_intents
                    SET delivery_status = 'FAILED', attempt_count = attempt_count + 1,
                        last_error = ?, updated_at = NOW()
                    WHERE tenant_id = ? AND id = ?
                    """, String.valueOf(e.getMessage()), tenantId, intentId);
            return false;
        }
    }

    /** Pluggable delivery provider (IN_APP store, email bridge, webhook bridge). */
    public interface DeliveryProvider {
        void deliver(UUID tenantId, UUID intentId, String eventType, UUID recipientUserId);
    }
}
