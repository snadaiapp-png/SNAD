package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.UUID;

public interface WorkflowTransitionAuditRepository {
    WorkflowTransitionAudit save(WorkflowTransitionAudit audit);
    List<WorkflowTransitionAudit> findByInstance(UUID tenantId, UUID workflowInstanceId);
    List<WorkflowTransitionAudit> findByTenant(UUID tenantId, int limit);
}
