package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.UUID;

public interface WorkflowStepInstanceRepository {
    WorkflowStepInstance save(WorkflowStepInstance stepInstance);
    List<WorkflowStepInstance> findByInstance(UUID workflowInstanceId);
    List<WorkflowStepInstance> findByTenantAndStatus(UUID tenantId, WorkflowStepInstance.Status status, int limit);
}
