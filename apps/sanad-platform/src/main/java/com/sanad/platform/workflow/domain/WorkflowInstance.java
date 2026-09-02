package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowInstance(
        UUID id,
        UUID tenantId,
        UUID workflowDefinitionId,
        int workflowVersion,
        String businessEntityType,
        UUID businessEntityId,
        Status status,
        String currentStepKey,
        UUID startedBy,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancelReason,
        UUID correlationId,
        EngineGeneration engineGeneration,
        UUID definitionFamilyId,
        UUID definitionVersionId,
        UUID parentInstanceId,
        String triggerType,
        UUID triggerId,
        String idempotencyKey,
        UUID causationId,
        String contextJson,
        int contextSchemaVersion,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED }
    public enum EngineGeneration { LEGACY, Y2 }

    public static WorkflowInstance start(
            UUID tenantId, UUID workflowDefinitionId, int workflowVersion,
            String businessEntityType, UUID businessEntityId,
            String firstStepKey, UUID startedBy, UUID correlationId) {
        var now = Instant.now();
        return new WorkflowInstance(UUID.randomUUID(), tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, Status.RUNNING, firstStepKey,
                startedBy, now, null, null, null, null, correlationId,
                EngineGeneration.LEGACY, null, null, null, null, null, null, null, "{}", 1,
                0, now, now);
    }

    /**
     * Y2 start: the concrete definition version is resolved first and its
     * generation persisted on the instance so all later commands route from
     * the persisted value only (design decisions Z3/AA3).
     */
    public static WorkflowInstance startY2(
            UUID tenantId, UUID definitionFamilyId, UUID definitionVersionId, int workflowVersion,
            String businessEntityType, UUID businessEntityId,
            String firstStepKey, UUID startedBy, UUID correlationId,
            String triggerType, UUID triggerId, String idempotencyKey, UUID causationId) {
        var now = Instant.now();
        return new WorkflowInstance(UUID.randomUUID(), tenantId, definitionVersionId, workflowVersion,
                businessEntityType, businessEntityId, Status.RUNNING, firstStepKey,
                startedBy, now, null, null, null, null, correlationId,
                EngineGeneration.Y2, definitionFamilyId, definitionVersionId, null,
                triggerType, triggerId, idempotencyKey, causationId, "{}", 1,
                0, now, now);
    }

    public WorkflowInstance pause() {
        requireStatus(Status.RUNNING, "pause");
        return withStatus(Status.PAUSED);
    }

    public WorkflowInstance resume() {
        requireStatus(Status.PAUSED, "resume");
        return withStatus(Status.RUNNING);
    }

    public WorkflowInstance complete() {
        requireStatus(Status.RUNNING, "complete");
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, Status.COMPLETED, null,
                startedBy, startedAt, now, null, null, null, correlationId,
                engineGeneration, definitionFamilyId, definitionVersionId, parentInstanceId,
                triggerType, triggerId, idempotencyKey, causationId, contextJson, contextSchemaVersion,
                version + 1, createdAt, now);
    }

    public WorkflowInstance cancel(UUID cancelledBy, String reason) {
        if (status == Status.COMPLETED) throw new IllegalStateException("Cannot cancel COMPLETED instance");
        var now = Instant.now();
        return new WorkflowInstance(id, tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, Status.CANCELLED, null,
                startedBy, startedAt, null, now, cancelledBy, reason, correlationId,
                engineGeneration, definitionFamilyId, definitionVersionId, parentInstanceId,
                triggerType, triggerId, idempotencyKey, causationId, contextJson, contextSchemaVersion,
                version + 1, createdAt, now);
    }

    public WorkflowInstance fail() {
        if (status == Status.COMPLETED) throw new IllegalStateException("Cannot fail COMPLETED instance");
        return withStatus(Status.FAILED);
    }

    public WorkflowInstance advanceToStep(String newStepKey) {
        requireStatus(Status.RUNNING, "advance");
        return new WorkflowInstance(id, tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, status, newStepKey,
                startedBy, startedAt, completedAt, cancelledAt, cancelledBy, cancelReason, correlationId,
                engineGeneration, definitionFamilyId, definitionVersionId, parentInstanceId,
                triggerType, triggerId, idempotencyKey, causationId, contextJson, contextSchemaVersion,
                version + 1, createdAt, Instant.now());
    }

    private WorkflowInstance withStatus(Status newStatus) {
        return new WorkflowInstance(id, tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, newStatus, currentStepKey,
                startedBy, startedAt, completedAt, cancelledAt, cancelledBy, cancelReason, correlationId,
                engineGeneration, definitionFamilyId, definitionVersionId, parentInstanceId,
                triggerType, triggerId, idempotencyKey, causationId, contextJson, contextSchemaVersion,
                version + 1, createdAt, Instant.now());
    }

    /**
     * Typed-context write (S3): replaces the whole context payload; namespace
     * discipline is enforced by the context service, not by the instance.
     */
    public WorkflowInstance withContext(String contextJson, int contextSchemaVersion) {
        return new WorkflowInstance(id, tenantId, workflowDefinitionId, workflowVersion,
                businessEntityType, businessEntityId, status, currentStepKey,
                startedBy, startedAt, completedAt, cancelledAt, cancelledBy, cancelReason, correlationId,
                engineGeneration, definitionFamilyId, definitionVersionId, parentInstanceId,
                triggerType, triggerId, idempotencyKey, causationId, contextJson, contextSchemaVersion,
                version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
