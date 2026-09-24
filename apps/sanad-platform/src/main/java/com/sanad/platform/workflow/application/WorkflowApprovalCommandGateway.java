package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Workflow-owned facade for approval commands issued by other business modules. */
@Component
public class WorkflowApprovalCommandGateway implements WorkflowApprovalCommandPort {

    private final WorkflowApprovalService approvalService;

    public WorkflowApprovalCommandGateway(WorkflowApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public WorkflowApprovalRequest approve(
            UUID tenantId,
            UUID approvalRequestId,
            UUID approverId,
            long expectedVersion,
            String comments) {
        return approvalService.approve(tenantId, approvalRequestId, approverId, expectedVersion, comments);
    }

    @Override
    public WorkflowApprovalRequest reject(
            UUID tenantId,
            UUID approvalRequestId,
            UUID rejecterId,
            long expectedVersion,
            String comments) {
        return approvalService.reject(tenantId, approvalRequestId, rejecterId, expectedVersion, comments);
    }
}
