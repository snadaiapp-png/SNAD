package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowAssignmentRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resolved, immutable eligibility evidence (design decision N3): concrete
 * Employee IDs plus the rule that produced them and the resolution instant.
 * Persisted as the WorkItem candidate snapshot at step activation.
 *
 * <p>Assignment never grants authorization — the snapshot is historical
 * evidence only.</p>
 */
public record ResolvedAssignment(
        List<UUID> employeeIds,
        WorkflowAssignmentRule rule,
        String resolutionSource,
        Instant resolvedAt
) {
    public ResolvedAssignment {
        employeeIds = List.copyOf(employeeIds);
    }

    public boolean isEmpty() {
        return employeeIds.isEmpty();
    }
}
