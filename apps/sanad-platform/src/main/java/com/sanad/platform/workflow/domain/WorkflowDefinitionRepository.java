package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository {
    WorkflowDefinition save(WorkflowDefinition def);
    Optional<WorkflowDefinition> findById(UUID tenantId, UUID id);
    Optional<WorkflowDefinition> findByCode(UUID tenantId, String code, int version);
    Optional<WorkflowDefinition> findActiveByCode(UUID tenantId, String code);
    List<WorkflowDefinition> findByTenant(UUID tenantId, int limit);
    List<WorkflowDefinition> findByTenantAndStatus(UUID tenantId, WorkflowDefinition.Status status, int limit);
    List<WorkflowStep> findSteps(UUID workflowDefinitionId);
    WorkflowStep saveStep(WorkflowStep step);
}
