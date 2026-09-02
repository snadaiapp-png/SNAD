package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * First-class workflow incident (design decision AF3). Created whenever
 * manual operational intervention is required — exhausted retries,
 * compensation failure, graph-resolution ambiguity, unresolvable assignee.
 */
public record WorkflowIncident(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        String source,
        Severity severity,
        String failureCategory,
        Status status,
        UUID owner,
        String resolution,
        UUID retryStepInstanceId,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED, CLOSED }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public static WorkflowIncident open(UUID tenantId, UUID workflowInstanceId,
                                        UUID workflowStepInstanceId, String source,
                                        Severity severity, String failureCategory) {
        var now = Instant.now();
        return new WorkflowIncident(UUID.randomUUID(), tenantId, workflowInstanceId,
                workflowStepInstanceId, source, severity, failureCategory,
                Status.OPEN, null, null, null, now, now);
    }

    public WorkflowIncident acknowledge(UUID actor) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Cannot acknowledge incident in status " + status);
        }
        return copy(Status.ACKNOWLEDGED, actor, resolution);
    }

    public WorkflowIncident resolve(UUID actor, String resolution) {
        if (status == Status.RESOLVED || status == Status.CLOSED) {
            throw new IllegalStateException("Cannot resolve incident in status " + status);
        }
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("Incident resolution note is required");
        }
        return copy(Status.RESOLVED, actor, resolution);
    }

    private WorkflowIncident copy(Status newStatus, UUID owner, String resolution) {
        return new WorkflowIncident(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                source, severity, failureCategory, newStatus, owner, resolution,
                retryStepInstanceId, createdAt, Instant.now());
    }
}
