package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowStepInstance(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepId,
        String stepKey,
        Status status,
        UUID assignedUserId,
        String assignedRole,
        Instant startedAt,
        Instant completedAt,
        Instant dueAt,
        int attemptCount,
        String result,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { PENDING, IN_PROGRESS, COMPLETED, SKIPPED, FAILED }

    public static WorkflowStepInstance create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepId,
            String stepKey, Instant dueAt, UUID assignedUserId, String assignedRole) {
        var now = Instant.now();
        return new WorkflowStepInstance(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepId, stepKey, Status.PENDING, assignedUserId, assignedRole,
                null, null, dueAt, 0, null, 0, now, now);
    }

    public WorkflowStepInstance start() {
        requireStatus(Status.PENDING, "start");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.IN_PROGRESS, assignedUserId, assignedRole,
                now, null, dueAt, attemptCount + 1, result, version + 1, createdAt, now);
    }

    public WorkflowStepInstance complete(String result) {
        requireStatus(Status.IN_PROGRESS, "complete");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.COMPLETED, assignedUserId, assignedRole,
                startedAt, now, dueAt, attemptCount, result, version + 1, createdAt, now);
    }

    public WorkflowStepInstance fail(String reason) {
        requireStatus(Status.IN_PROGRESS, "fail");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.FAILED, assignedUserId, assignedRole,
                startedAt, null, dueAt, attemptCount, reason, version + 1, createdAt, now);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
