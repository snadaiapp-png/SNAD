package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of one automated action attempt (design decision O3).
 * Every attempt is persisted before and after execution — the platform
 * never silently converts an exhausted failure into a success.
 */
public record WorkflowExecutionAttempt(
        UUID id,
        UUID tenantId,
        UUID workflowInstanceId,
        UUID workflowStepInstanceId,
        int attemptNumber,
        String idempotencyKey,
        Outcome outcome,
        String failureCategory,
        String externalReference,
        String diagnostics,
        Instant startedAt,
        Instant finishedAt
) {
    public enum Outcome { SUCCEEDED, FAILED_TRANSIENT, FAILED_PERMANENT, TIMED_OUT, SKIPPED }

    public static WorkflowExecutionAttempt start(UUID tenantId, UUID workflowInstanceId,
                                                 UUID stepInstanceId, int attemptNumber,
                                                 String idempotencyKey) {
        var now = Instant.now();
        return new WorkflowExecutionAttempt(UUID.randomUUID(), tenantId, workflowInstanceId,
                stepInstanceId, attemptNumber, idempotencyKey, null, null, null, "{}", now, null);
    }

    public WorkflowExecutionAttempt finish(Outcome outcome, String failureCategory,
                                           String externalReference, String diagnostics) {
        return new WorkflowExecutionAttempt(id, tenantId, workflowInstanceId, workflowStepInstanceId,
                attemptNumber, idempotencyKey, outcome, failureCategory, externalReference,
                diagnostics != null ? diagnostics : "{}", startedAt, Instant.now());
    }
}
