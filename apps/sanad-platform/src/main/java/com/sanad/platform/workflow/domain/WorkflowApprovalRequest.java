package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowApprovalRequest(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        UUID requestedFromUserId,
        String requestedFromRole,
        Status status,
        Instant requestedAt,
        Instant dueAt,
        UUID actedBy,
        Instant actedAt,
        String decision,
        String comments,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED }

    public static WorkflowApprovalRequest create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
            UUID requestedFromUserId, String requestedFromRole, Instant dueAt) {
        var now = Instant.now();
        return new WorkflowApprovalRequest(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepInstanceId, requestedFromUserId, requestedFromRole,
                Status.PENDING, now, dueAt, null, null, null, null,
                0, now, now);
    }

    /**
     * Approve — enforces segregation of duties.
     * The actor who requested the approval cannot approve it (unless selfApprovalAllowed).
     */
    public WorkflowApprovalRequest approve(UUID approverId, String comments) {
        return resolve(approverId, "APPROVED", Status.APPROVED, comments);
    }

    public WorkflowApprovalRequest reject(UUID rejecterId, String comments) {
        return resolve(rejecterId, "REJECTED", Status.REJECTED, comments);
    }

    public WorkflowApprovalRequest cancel(UUID cancelledBy) {
        requireStatus(Status.PENDING, "cancel");
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, Status.CANCELLED,
                requestedAt, dueAt, cancelledBy, now, null, null,
                version + 1, createdAt, now);
    }

    public WorkflowApprovalRequest expire() {
        requireStatus(Status.PENDING, "expire");
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, Status.EXPIRED,
                requestedAt, dueAt, null, now, null, null,
                version + 1, createdAt, now);
    }

    private WorkflowApprovalRequest resolve(UUID actorId, String decisionStr, Status newStatus, String comments) {
        requireStatus(Status.PENDING, "resolve");
        if (actorId.equals(requestedFromUserId)) {
            throw new IllegalStateException(
                    "Segregation of duties: the person who requested the approval cannot approve/reject it");
        }
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, newStatus,
                requestedAt, dueAt, actorId, now, decisionStr, comments,
                version + 1, createdAt, now);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
