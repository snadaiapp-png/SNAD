package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStepInstanceRepository {
    WorkflowStepInstance save(WorkflowStepInstance stepInstance);
    List<WorkflowStepInstance> findByInstance(UUID workflowInstanceId);
    List<WorkflowStepInstance> findByTenantAndStatus(UUID tenantId, WorkflowStepInstance.Status status, int limit);

    /**
     * Tenant-scoped lookup of a single step instance by its primary key.
     *
     * <p>Used by the approval-service to validate that a
     * {@code workflowStepInstanceId} supplied by an API caller (a) exists,
     * (b) belongs to the authenticated tenant, and (c) belongs to the
     * workflow instance the approval is being created against. Returning
     * {@link Optional#empty()} in any of those failure cases lets the
     * service surface a controlled 4xx to the API client instead of
     * leaking internal identifiers.
     */
    Optional<WorkflowStepInstance> findById(UUID tenantId, UUID stepInstanceId);
}
