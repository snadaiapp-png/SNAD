package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow Approval Request — an approval work item with segregation of duties.
 *
 * <p>SOD rule: The user who created the approval request (requestedByUserId)
 * cannot approve or reject it. Only a different user (the assigned approver
 * or another authorized user) can act on it.
 *
 * <p>requestedFromUserId = the user FROM WHOM approval is requested (the assignee).
 * requestedByUserId = the user WHO created the approval request (the requester).
 */
public record WorkflowApprovalRequest(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        UUID requestedFromUserId,
        String requestedFromRole,
        UUID requestedByUserId,
        UUID requestedFromEmployeeId,
        WorkflowApprovalPolicy.Aggregation approvalPolicy,
        WorkflowApprovalPolicy.SelfApproval selfApprovalPolicy,
        String policySnapshot,
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
            UUID requestedFromUserId, String requestedFromRole, Instant dueAt,
            UUID requestedByUserId) {
        return create(tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, dueAt, requestedByUserId,
                null, WorkflowApprovalPolicy.defaultPolicy());
    }

    public static WorkflowApprovalRequest create(
            UUID tenantId, UUID workflowInstanceId, UUID workflowStepInstanceId,
            UUID requestedFromUserId, String requestedFromRole, Instant dueAt,
            UUID requestedByUserId, UUID requestedFromEmployeeId,
            WorkflowApprovalPolicy policy) {
        var now = Instant.now();
        return new WorkflowApprovalRequest(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepInstanceId, requestedFromUserId, requestedFromRole,
                requestedByUserId,
                requestedFromEmployeeId, policy.aggregation(), policy.selfApproval(),
                policy.snapshotJson(),
                Status.PENDING, now, dueAt, null, null, null, null,
                0, now, now);
    }

    /**
     * Approve — enforces segregation of duties.
     * The user who created the approval request (requestedByUserId) cannot approve it.
     */
    public WorkflowApprovalRequest approve(UUID approverId, String comments) {
        return resolve(approverId, "APPROVED", Status.APPROVED, comments, true);
    }

    public WorkflowApprovalRequest reject(UUID rejecterId, String comments) {
        if (comments == null || comments.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return resolve(rejecterId, "REJECTED", Status.REJECTED, comments, false);
    }

    public WorkflowApprovalRequest cancel(UUID cancelledBy) {
        requireStatus(Status.PENDING, "cancel");
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, requestedByUserId,
                requestedFromEmployeeId, approvalPolicy, selfApprovalPolicy, policySnapshot,
                Status.CANCELLED,
                requestedAt, dueAt, cancelledBy, now, null, null,
                version + 1, createdAt, now);
    }

    public WorkflowApprovalRequest expire() {
        requireStatus(Status.PENDING, "expire");
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, requestedByUserId,
                requestedFromEmployeeId, approvalPolicy, selfApprovalPolicy, policySnapshot,
                Status.EXPIRED,
                requestedAt, dueAt, null, now, null, null,
                version + 1, createdAt, now);
    }

    private WorkflowApprovalRequest resolve(UUID actorId, String decisionStr, Status newStatus,
                                            String comments, boolean selfApprovalPermittedByPolicy) {
        requireStatus(Status.PENDING, "resolve");
        // SOD/M3: the requester cannot act on their own request unless the
        // frozen step policy explicitly allows self-approval. Even then the
        // application boundary must independently verify the
        // WORKFLOW.SELF_APPROVAL_OVERRIDE capability.
        if (requestedByUserId != null && actorId.equals(requestedByUserId)
                && !(selfApprovalPermittedByPolicy && selfApprovalPolicy == WorkflowApprovalPolicy.SelfApproval.ALLOW)) {
            throw new IllegalStateException(
                    "Segregation of duties: the user who created the approval request cannot approve/reject it");
        }
        var now = Instant.now();
        return new WorkflowApprovalRequest(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                requestedFromUserId, requestedFromRole, requestedByUserId,
                requestedFromEmployeeId, approvalPolicy, selfApprovalPolicy, policySnapshot,
                newStatus,
                requestedAt, dueAt, actorId, now, decisionStr, comments,
                version + 1, createdAt, now);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
