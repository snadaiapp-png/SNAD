package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTransitionAudit(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        UUID actorUserId,
        String action,
        String fromState,
        String toState,
        UUID correlationId,
        String metadata,  // JSON
        Instant createdAt
) {
    public enum Action {
        CREATE, UPDATE, ACTIVATE, DEACTIVATE, START, PAUSE, RESUME,
        CANCEL, ADVANCE, APPROVE, REJECT, EXPIRE, FAIL, COMPLETE, ARCHIVE, ASSIGN
    }

    public static WorkflowTransitionAudit create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
            UUID actorUserId, Action action, String fromState, String toState,
            UUID correlationId, String metadata) {
        return new WorkflowTransitionAudit(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepInstanceId, actorUserId, action.name(), fromState, toState,
                correlationId, metadata, Instant.now());
    }
}
