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
        /**
         * Activation-time SLA policy snapshot (V3): the resolved mode,
         * pinned calendar, and duration are frozen when the step instance is
         * created so later definition/calendar edits never change historical
         * evidence. Nullable for pre-snapshot rows.
         */
        String slaMode,
        UUID slaCalendarId,
        Integer slaHours,
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
        return create(tenantId, workflowInstanceId, workflowStepId, stepKey,
                new SlaSnapshot(dueAt, null, null, null), assignedUserId, assignedRole);
    }

    /** Activation-time SLA policy snapshot carried with the create command. */
    public record SlaSnapshot(Instant dueAt, String slaMode, UUID slaCalendarId, Integer slaHours) {}

    public static WorkflowStepInstance create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepId,
            String stepKey, SlaSnapshot slaSnapshot, UUID assignedUserId, String assignedRole) {
        var now = Instant.now();
        return new WorkflowStepInstance(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepId, stepKey, Status.PENDING, assignedUserId, assignedRole,
                null, null, slaSnapshot.dueAt(), slaSnapshot.slaMode(), slaSnapshot.slaCalendarId(),
                slaSnapshot.slaHours(), 0, null, 0, now, now);
    }

    public WorkflowStepInstance start() {
        requireStatus(Status.PENDING, "start");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.IN_PROGRESS, assignedUserId, assignedRole,
                now, null, dueAt, slaMode, slaCalendarId, slaHours, attemptCount + 1, result,
                version + 1, createdAt, now);
    }

    public WorkflowStepInstance complete(String result) {
        requireStatus(Status.IN_PROGRESS, "complete");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.COMPLETED, assignedUserId, assignedRole,
                startedAt, now, dueAt, slaMode, slaCalendarId, slaHours, attemptCount, result,
                version + 1, createdAt, now);
    }

    public WorkflowStepInstance fail(String reason) {
        requireStatus(Status.IN_PROGRESS, "fail");
        var now = Instant.now();
        return new WorkflowStepInstance(id, tenantId, workflowInstanceId, workflowStepId,
                stepKey, Status.FAILED, assignedUserId, assignedRole,
                startedAt, null, dueAt, slaMode, slaCalendarId, slaHours, attemptCount, reason,
                version + 1, createdAt, now);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
