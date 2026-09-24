package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;

import java.util.UUID;

/**
 * Workflow-owned command boundary for approval resolution.
 *
 * <p>Business modules depend on this boundary instead of coupling directly to
 * {@link WorkflowApprovalService}. Workflow Y2 remains the sole authority for
 * approval policy, segregation of duties, optimistic locking, and graph advancement.
 */
public interface WorkflowApprovalCommandPort {

    WorkflowApprovalRequest approve(
            UUID tenantId,
            UUID approvalRequestId,
            UUID approverId,
            long expectedVersion,
            String comments);

    WorkflowApprovalRequest reject(
            UUID tenantId,
            UUID approvalRequestId,
            UUID rejecterId,
            long expectedVersion,
            String comments);
}
