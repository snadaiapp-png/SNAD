package com.sanad.platform.workflow.application;

import java.util.UUID;

/**
 * Immutable inputs the assignment resolver may read. Kept deliberately
 * narrow so resolution cannot reach arbitrary workflow state.
 */
public record WorkflowAssignmentContext(UUID requesterEmployeeId) {

    public static WorkflowAssignmentContext empty() {
        return new WorkflowAssignmentContext(null);
    }
}
