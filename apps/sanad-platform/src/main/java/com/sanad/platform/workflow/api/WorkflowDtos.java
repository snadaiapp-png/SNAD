package com.sanad.platform.workflow.api;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;

import java.util.UUID;

/**
 * Typed response boundary for Workflow Y2 endpoints.
 *
 * <p>Field names and sentinel values intentionally match the existing v1 JSON
 * contract. New fields are additive only.</p>
 */
public final class WorkflowDtos {

    private WorkflowDtos() {}

    public record WorkItemResponse(
            UUID id,
            UUID workflowInstanceId,
            UUID workflowStepInstanceId,
            String type,
            String status,
            String assigneeEmployeeId,
            String claimedByEmployeeId,
            String assignmentMode,
            String title,
            int priority,
            String dueAt,
            long version
    ) {
        public static WorkItemResponse from(WorkflowWorkItem item) {
            return new WorkItemResponse(
                    item.id(),
                    item.workflowInstanceId(),
                    item.workflowStepInstanceId(),
                    item.type().name(),
                    item.status().name(),
                    item.assigneeEmployeeId() != null ? item.assigneeEmployeeId().toString() : "",
                    item.claimedByEmployeeId() != null ? item.claimedByEmployeeId().toString() : "",
                    item.assignmentMode().name(),
                    item.title(),
                    item.priority(),
                    item.dueAt() != null ? item.dueAt().toString() : "",
                    item.version());
        }
    }

    public record ApprovalResponse(
            UUID id,
            UUID workflowInstanceId,
            UUID workflowStepInstanceId,
            UUID requestedFromUserId,
            UUID requestedFromEmployeeId,
            String status,
            String decision,
            String comments,
            long version
    ) {
        public static ApprovalResponse from(WorkflowApprovalRequest approval) {
            return new ApprovalResponse(
                    approval.id(),
                    approval.workflowInstanceId(),
                    approval.workflowStepInstanceId(),
                    approval.requestedFromUserId(),
                    approval.requestedFromEmployeeId(),
                    approval.status().name(),
                    approval.decision() != null ? approval.decision() : "",
                    approval.comments() != null ? approval.comments() : "",
                    approval.version());
        }
    }
}
