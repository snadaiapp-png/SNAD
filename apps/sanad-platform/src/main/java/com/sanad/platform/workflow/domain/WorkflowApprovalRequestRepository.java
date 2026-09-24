package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowApprovalRequestRepository {
    WorkflowApprovalRequest save(WorkflowApprovalRequest request);
    Optional<WorkflowApprovalRequest> findById(UUID tenantId, UUID id);
    List<WorkflowApprovalRequest> findByTenant(UUID tenantId, int limit);
    List<WorkflowApprovalRequest> findByTenantAndStatus(UUID tenantId, WorkflowApprovalRequest.Status status, int limit);
    List<WorkflowApprovalRequest> findByInstance(UUID tenantId, UUID workflowInstanceId);
    List<WorkflowApprovalRequest> findByUser(UUID tenantId, UUID userId, int limit);
}
