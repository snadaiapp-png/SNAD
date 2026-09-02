package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowEventEnvelope;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent trigger consumption (design decisions J3/X3). A DOMAIN_EVENT
 * start is keyed by (tenant, eventId, triggerKey, definition): the inbox
 * unique constraint plus the instance idempotency index guarantee a
 * duplicate delivery reuses the prior start result and never creates a
 * second instance.
 */
@Service
public class WorkflowTriggerService {

    private final JdbcTemplate jdbc;
    private final WorkflowInstanceRepositoryPort instanceRepo;

    /** Narrow write port so the trigger service stays persistence-agnostic. */
    public interface WorkflowInstanceRepositoryPort {
        WorkflowInstance save(WorkflowInstance instance);
        Optional<WorkflowInstance> findByIdempotencyKey(UUID tenantId, String idempotencyKey);
    }

    public WorkflowTriggerService(JdbcTemplate jdbc, WorkflowInstanceRepositoryPort instanceRepo) {
        this.jdbc = jdbc;
        this.instanceRepo = instanceRepo;
    }

    public record TriggerConsumeResult(UUID instanceId, boolean duplicate) {}

    /**
     * Consumes a domain event for one published definition. Replays return
     * the original instance with {@code duplicate=true}.
     */
    @Transactional
    public TriggerConsumeResult consumeDomainEvent(WorkflowEventEnvelope event,
                                                   String triggerKey,
                                                   UUID definitionId,
                                                   UUID definitionFamilyId,
                                                   int definitionVersion,
                                                   String firstStepKey,
                                                   UUID startedBy) {
        UUID inboxId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO workflow_event_inbox (
                        id, tenant_id, event_id, trigger_key, workflow_definition_id,
                        received_at, status
                    ) VALUES (?, ?, ?, ?, ?, NOW(), 'RECEIVED')
                    """, inboxId, event.tenantId(), event.eventId(), triggerKey, definitionId);
        } catch (DataIntegrityViolationException e) {
            UUID prior = jdbc.queryForObject("""
                    SELECT workflow_instance_id FROM workflow_event_inbox
                    WHERE tenant_id = ? AND event_id = ? AND trigger_key = ?
                      AND workflow_definition_id = ?
                    """, UUID.class, event.tenantId(), event.eventId(), triggerKey, definitionId);
            return new TriggerConsumeResult(prior, true);
        }

        String idempotencyKey = "event:" + event.eventId() + ":" + triggerKey;
        WorkflowInstance instance = WorkflowInstance.startY2(
                event.tenantId(), definitionFamilyId, definitionId, definitionVersion,
                event.aggregateType(), event.aggregateId(), firstStepKey, startedBy,
                event.correlationId(), "DOMAIN_EVENT", event.eventId(), idempotencyKey,
                event.causationId(), null);
        UUID instanceId;
        try {
            instanceId = instanceRepo.save(instance).id();
        } catch (DataIntegrityViolationException e) {
            // The instance idempotency index is the second guard.
            instanceId = instanceRepo.findByIdempotencyKey(event.tenantId(), idempotencyKey)
                    .map(WorkflowInstance::id)
                    .orElseThrow(() -> e);
            jdbc.update("""
                    UPDATE workflow_event_inbox
                    SET workflow_instance_id = ?, status = 'PROCESSED', processed_at = NOW()
                    WHERE id = ? AND tenant_id = ?
                    """, instanceId, inboxId, event.tenantId());
            return new TriggerConsumeResult(instanceId, true);
        }

        jdbc.update("""
                UPDATE workflow_event_inbox
                SET workflow_instance_id = ?, status = 'PROCESSED', processed_at = NOW()
                WHERE id = ? AND tenant_id = ?
                """, instanceId, inboxId, event.tenantId());
        return new TriggerConsumeResult(instanceId, false);
    }
}
