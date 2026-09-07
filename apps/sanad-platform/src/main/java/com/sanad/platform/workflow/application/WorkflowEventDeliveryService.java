package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowEventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox delivery (design decision X3). Events are appended in
 * the same transaction as the workflow mutation and dispatched separately
 * with bounded retries — at-least-once semantics only.
 */
@Service
public class WorkflowEventDeliveryService {

    private static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbc;

    public WorkflowEventDeliveryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Appends an event to the outbox inside the caller's transaction. */
    @Transactional
    public UUID enqueue(UUID tenantId, String eventType, String aggregateType, UUID aggregateId,
                        String payloadJson, UUID correlationId, UUID causationId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_event_outbox (
                    id, tenant_id, event_type, aggregate_type, aggregate_id, payload_json,
                    correlation_id, causation_id, status, available_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW(), NOW(), NOW())
                """, id, tenantId, eventType, aggregateType, aggregateId, payloadJson,
                correlationId != null ? correlationId.toString() : null,
                causationId != null ? causationId.toString() : null);
        return id;
    }

    /**
     * Atomically claims a batch of due events (the claim is the status flip,
     * so two dispatchers cannot take the same event).
     */
    @Transactional
    public List<Map<String, Object>> claimDue(UUID tenantId, int limit) {
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM workflow_event_outbox
                WHERE tenant_id = ? AND status = 'PENDING' AND available_at <= NOW()
                ORDER BY available_at ASC LIMIT ?
                """, UUID.class, tenantId, Math.min(limit, 100));
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] claimArgs = new Object[ids.size() + 1];
        claimArgs[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) {
            claimArgs[i + 1] = ids.get(i);
        }
        jdbc.update("UPDATE workflow_event_outbox SET status = 'PROCESSING', claimed_at = NOW(), "
                + "updated_at = NOW() WHERE tenant_id = ? AND id IN (" + placeholders + ")", claimArgs);
        return jdbc.queryForList(
                "SELECT * FROM workflow_event_outbox WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                claimArgs);
    }

    /** Marks dispatch outcome; transient failures back off exponentially. */
    @Transactional
    public void recordDispatch(UUID tenantId, UUID eventId, boolean published, String error) {
        if (published) {
            jdbc.update("""
                    UPDATE workflow_event_outbox
                    SET status = 'PUBLISHED', published_at = NOW(), updated_at = NOW()
                    WHERE tenant_id = ? AND id = ?
                    """, tenantId, eventId);
            return;
        }
        jdbc.update("""
                UPDATE workflow_event_outbox
                SET attempt_count = attempt_count + 1, last_error = ?,
                    status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    available_at = NOW() + (make_interval(secs => power(2, attempt_count) * 5)),
                    updated_at = NOW()
                WHERE tenant_id = ? AND id = ?
                """, error, MAX_ATTEMPTS, tenantId, eventId);
    }
}
