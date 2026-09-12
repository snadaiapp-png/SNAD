package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * R1 GATES R1.19/R1.20 — Workflow Transaction Journey (append-only evidence).
 *
 * <p>Single authoritative writer. Rows are evidence of what HAPPENED, never a
 * current-state projection: the DB forbids UPDATE/DELETE (V20260911_3
 * triggers) and corrections must be appended as superseding events.
 * Writes are tenant-scoped, transactionally correlated with the Workflow
 * state change that produced them (REQUIRES propagation), idempotent where a
 * repeatable command can occur (event_key), ordered by the BIGSERIAL id for
 * deterministic read reconstruction, correlation- and causation-aware.</p>
 */
@Service
public class WorkflowJourneyService {

    public record JourneyEvent(String eventType, UUID workflowInstanceId, UUID stepInstanceId,
                               UUID workItemId, UUID externalActionId, UUID approvalId,
                               UUID definitionId, UUID definitionFamilyId, Integer definitionVersion,
                               String sourceModule, String sourceEntityType, UUID sourceEntityId,
                               String actorType, UUID actorUserId, UUID actorEmployeeId,
                               UUID externalParticipantId, String fromState, String toState,
                               String reason, UUID correlationId, UUID causationId,
                               String eventKey, java.util.Map<String, Object> metadata) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowJourneyService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void append(UUID tenantId, JourneyEvent e) {
        if (tenantId == null) throw new IllegalArgumentException("journey tenantId is required");
        if (e == null || e.eventType() == null || e.eventType().isBlank()) {
            throw new IllegalArgumentException("journey eventType is required");
        }
        jdbc.update("""
                INSERT INTO workflow_journey (
                    tenant_id, event_type, definition_id, definition_family_id, definition_version,
                    workflow_instance_id, step_instance_id, work_item_id, external_action_id, approval_id,
                    source_module, source_entity_type, source_entity_id,
                    actor_type, actor_user_id, actor_employee_id, external_participant_id,
                    from_state, to_state, occurred_at, reason, correlation_id, causation_id,
                    event_key, metadata)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),?,?,?,?,?::jsonb)
                """,
                tenantId, e.eventType(), e.definitionId(), e.definitionFamilyId(), e.definitionVersion(),
                e.workflowInstanceId(), e.stepInstanceId(), e.workItemId(), e.externalActionId(), e.approvalId(),
                e.sourceModule(), e.sourceEntityType(), e.sourceEntityId(),
                e.actorType() == null ? "SYSTEM" : e.actorType(), e.actorUserId(), e.actorEmployeeId(),
                e.externalParticipantId(), e.fromState(), e.toState(), e.reason(), e.correlationId(),
                e.causationId(), e.eventKey(), toJson(e.metadata()));
    }

    /** Deterministic reconstruction of one process journey (append order). */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> journey(UUID tenantId, UUID workflowInstanceId) {
        return jdbc.queryForList("""
                SELECT id, event_type, from_state, to_state, occurred_at, actor_type,
                       actor_user_id, reason, correlation_id, causation_id, event_key
                  FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                 ORDER BY id
                """, tenantId, workflowInstanceId);
    }

    private String toJson(java.util.Map<String, Object> metadata) {
        if (metadata == null) return null;
        try { return objectMapper.writeValueAsString(metadata); }
        catch (Exception ex) { throw new IllegalArgumentException("invalid journey metadata", ex); }
    }
}
