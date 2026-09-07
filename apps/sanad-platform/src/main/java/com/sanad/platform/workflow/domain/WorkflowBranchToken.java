package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable branch token for controlled parallelism (design decision R3).
 * Each PARALLEL_FORK outgoing edge mints exactly one token; join completion
 * is granted by a compare-and-set on the join's step instance so a race can
 * never advance the graph twice.
 */
public record WorkflowBranchToken(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID forkStepInstanceId,
        String branchKey,
        Status status,
        UUID joinStepId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { RUNNING, COMPLETED, FAILED, CANCELLED }

    public static WorkflowBranchToken create(UUID tenantId, UUID workflowInstanceId,
                                             UUID forkStepInstanceId, String branchKey,
                                             UUID joinStepId) {
        var now = Instant.now();
        return new WorkflowBranchToken(UUID.randomUUID(), tenantId, workflowInstanceId,
                forkStepInstanceId, branchKey, Status.RUNNING, joinStepId, 0, now, now);
    }
}
