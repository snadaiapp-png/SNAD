package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Frozen eligibility snapshot for one WORK_POOL WorkItem candidate
 * (design decision N3). Persisted at step activation as historical evidence
 * of the resolved candidate set — never used as a standing authorization
 * grant: every command revalidates actionability and capabilities live.
 */
public record WorkflowWorkItemCandidate(
        UUID tenantId,
        UUID workItemId,
        UUID employeeId,
        String resolutionSource,
        Instant resolvedAt,
        String snapshotMetadata
) {
    public static WorkflowWorkItemCandidate create(
            UUID tenantId, UUID workItemId, UUID employeeId, String resolutionSource) {
        return new WorkflowWorkItemCandidate(tenantId, workItemId, employeeId,
                resolutionSource != null ? resolutionSource : "UNKNOWN",
                Instant.now(), "{}");
    }
}
