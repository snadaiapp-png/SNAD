package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowBranchTokenRepository {

    WorkflowBranchToken insert(WorkflowBranchToken token);

    WorkflowBranchToken save(WorkflowBranchToken token);

    Optional<WorkflowBranchToken> findById(UUID tenantId, UUID id);

    List<WorkflowBranchToken> findByFork(UUID tenantId, UUID workflowInstanceId, UUID forkStepInstanceId);

    List<WorkflowBranchToken> findByJoin(UUID tenantId, UUID workflowInstanceId, UUID joinStepId);
}
