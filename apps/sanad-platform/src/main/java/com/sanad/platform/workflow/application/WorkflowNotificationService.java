package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;

    public WorkflowNotificationService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues an intent. Deduplication keys collapse repeated intents (for
     * example a reminder re-fire) to one notification — including under
     * concurrent enqueue races, enforced by the
     * {@code uq_wf_notification_dedup} unique index. Intents with a null
     * deduplication key are never collapsed (SQL unique indexes treat NULLs
     * as distinct).
     *
     * <p>Race contract: a loser of an enqueue race replays the winner's
     * durable intent id. The arbiter handles most conflicts in-statement
     * (the conflict leaves the transaction healthy, so the winner is read
     * directly); under a tight race the arbiter can surface the unique
     * violation instead, aborting this transaction — that loser throws the
     * controlled {@link IllegalStateException} below and the caller retries
     * the enqueue in a fresh transaction, which lands on the replay path
     * (the same contract as {@link WorkflowTriggerService} duplicate
     * delivery). Exactly one durable intent per (tenant, key) holds in every
     * case.</p>
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
        int inserted;
        try {
            inserted = jdbc.update("""
                    INSERT INTO workflow_notification_intents (
                        id, tenant_id, event_type, workflow_instance_id, work_item_id,
                        recipient_user_id, channel, deduplication_key, delivery_status,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW(), NOW())
                    ON CONFLICT (tenant_id, deduplication_key) DO NOTHING
                    """, id, tenantId, eventType, workflowInstanceId, workItemId,
                    recipientUserId, channel != null ? channel : "IN_APP", deduplicationKey);
        } catch (DataIntegrityViolationException raced) {
            if (deduplicationKey != null
                    && String.valueOf(raced.getMostSpecificCause().getMessage())
                            .contains("uq_wf_notification_dedup")) {
                // Tight enqueue race: the arbiter surfaced the dedup unique
                // violation instead of an in-statement conflict, aborting this
                // transaction. Controlled duplicate-delivery signal — the
                // caller retries the enqueue and lands on the replay path.
                throw new IllegalStateException(
                        "Duplicate notification intent raced another enqueue: "
                                + deduplicationKey, raced);
            }
            throw raced;
        }
        if (inserted == 0 && deduplicationKey != null) {
            // In-statement conflict with the winner: the ON CONFLICT arbiter
            // left this transaction healthy, so the winner's durable intent
            // can be read and replayed directly.
            return jdbc.queryForObject("""
                    SELECT id FROM workflow_notification_intents
                    WHERE tenant_id = ? AND deduplication_key = ?
                    """, UUID.class, tenantId, deduplicationKey);
        }
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

    // ===== R2 rich intent surface (GATE R2.5) =====

    /**
     * R2 intent request. Carries the full bounded delivery context: any
     * recipient (user or external participant), any channel, correlation,
     * template/priority/deep-link, and renderable title/body. Dedup and
     * race semantics are identical to the Y2 enqueue above — the same
     * {@code uq_wf_notification_dedup} identity arbitrates.
     */
    public record NotificationIntentRequest(
            String eventType,
            UUID workflowInstanceId,
            UUID workItemId,
            UUID externalActionId,
            UUID recipientUserId,
            UUID recipientParticipantId,
            String recipientAddress,
            String channel,
            String deduplicationKey,
            String title,
            String body,
            String templateKey,
            String locale,
            String priority,
            String deepLink,
            UUID policyId,
            UUID correlationId,
            String causationId,
            Map<String, Object> payload) {
    }

    /**
     * Enqueues a rich R2 intent with the same fail-closed dedup contract as
     * the Y2 enqueue. Channel defaults to IN_APP; priority defaults to
     * NORMAL. The transaction-local insert keeps state commit and durable
     * intent atomic (AD-16); delivery happens exclusively via the
     * dispatcher worker.
     */
    @Transactional
    public UUID enqueue(UUID tenantId, NotificationIntentRequest request) {
        if (request.eventType() == null || request.eventType().isBlank()) {
            throw new IllegalArgumentException("Notification eventType is required");
        }
        if (request.deduplicationKey() != null) {
            List<UUID> existing = jdbc.queryForList("""
                    SELECT id FROM workflow_notification_intents
                    WHERE tenant_id = ? AND deduplication_key = ?
                    """, UUID.class, tenantId, request.deduplicationKey());
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }
        UUID id = UUID.randomUUID();
        String payloadJson;
        try {
            payloadJson = request.payload() == null ? null
                    : objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Notification payload not serializable", e);
        }
        try {
            jdbc.update("""
                    INSERT INTO workflow_notification_intents (
                        id, tenant_id, event_type, workflow_instance_id, work_item_id,
                        external_action_id, recipient_user_id, recipient_participant_id,
                        recipient_address, channel, deduplication_key, delivery_status,
                        title, body, template_key, locale, priority, deep_link,
                        policy_id, correlation_id, causation_id, payload,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING',
                              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())
                    ON CONFLICT (tenant_id, deduplication_key) DO NOTHING
                    """,
                    id, tenantId, request.eventType(), request.workflowInstanceId(),
                    request.workItemId(), request.externalActionId(),
                    request.recipientUserId(), request.recipientParticipantId(),
                    request.recipientAddress(),
                    request.channel() == null ? "IN_APP" : request.channel(),
                    request.deduplicationKey(),
                    request.title(), request.body(), request.templateKey(),
                    request.locale(), request.priority() == null
                            ? "NORMAL" : request.priority(),
                    request.deepLink(), request.policyId(),
                    request.correlationId(), request.causationId(), payloadJson);
        } catch (DataIntegrityViolationException raced) {
            if (request.deduplicationKey() != null
                    && String.valueOf(raced.getMostSpecificCause().getMessage())
                            .contains("uq_wf_notification_dedup")) {
                throw new IllegalStateException(
                        "Duplicate notification intent raced another enqueue: "
                                + request.deduplicationKey(), raced);
            }
            throw raced;
        }
        if (request.deduplicationKey() != null) {
            List<UUID> winners = jdbc.queryForList("""
                    SELECT id FROM workflow_notification_intents
                    WHERE tenant_id = ? AND deduplication_key = ?
                    """, UUID.class, tenantId, request.deduplicationKey());
            if (!winners.isEmpty()) {
                return winners.get(0);
            }
        }
        return id;
    }
}
