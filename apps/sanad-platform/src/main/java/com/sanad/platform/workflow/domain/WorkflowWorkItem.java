package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Central actionable WorkItem (design decisions C3/L3).
 *
 * <p>Only {@code HUMAN_TASK} and {@code APPROVAL} steps produce WorkItems.
 * A DIRECT item names one employee assignee; a WORK_POOL item lists
 * candidates and is won by exactly one atomic claim (optimistic
 * {@code version} + status guard — two employees can never claim the same
 * item version).</p>
 */
public record WorkflowWorkItem(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        Type type,
        Status status,
        UUID assigneeEmployeeId,
        UUID claimedByEmployeeId,
        AssignmentMode assignmentMode,
        String sourceModule,
        String sourceEntityType,
        UUID sourceEntityId,
        String title,
        String description,
        int priority,
        Instant dueAt,
        Instant slaDueAt,
        Instant claimedAt,
        Instant completedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Type { HUMAN_TASK, APPROVAL }
    public enum Status {
        AVAILABLE, CLAIMED, IN_PROGRESS, ASSIGNEE_UNAVAILABLE,
        COMPLETED, CANCELLED, EXPIRED
    }
    public enum AssignmentMode { DIRECT, WORK_POOL }

    public static WorkflowWorkItem create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
            Type type, AssignmentMode assignmentMode, UUID assigneeEmployeeId,
            String sourceModule, String sourceEntityType, UUID sourceEntityId,
            String title, String description, int priority,
            Instant dueAt, Instant slaDueAt) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (assignmentMode == AssignmentMode.DIRECT && assigneeEmployeeId == null) {
            throw new IllegalArgumentException("DIRECT work items require an assignee employee");
        }
        var now = Instant.now();
        boolean direct = assignmentMode == AssignmentMode.DIRECT;
        return new WorkflowWorkItem(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepInstanceId, type,
                direct ? Status.CLAIMED : Status.AVAILABLE,
                assigneeEmployeeId,
                direct ? assigneeEmployeeId : null,
                assignmentMode,
                sourceModule, sourceEntityType, sourceEntityId,
                title, description, priority, dueAt, slaDueAt,
                direct ? now : null,
                null, 0, now, now);
    }
}
