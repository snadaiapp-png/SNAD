package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository {
    WorkflowInstance save(WorkflowInstance instance);
    Optional<WorkflowInstance> findById(UUID tenantId, UUID id);
    List<WorkflowInstance> findByTenant(UUID tenantId, int limit);
    List<WorkflowInstance> findByTenantAndStatus(UUID tenantId, WorkflowInstance.Status status, int limit);
    List<WorkflowInstance> findByBusinessEntity(UUID tenantId, String entityType, UUID entityId);
}
