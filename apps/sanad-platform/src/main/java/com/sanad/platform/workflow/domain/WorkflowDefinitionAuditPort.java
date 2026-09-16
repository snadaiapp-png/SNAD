package com.sanad.platform.workflow.domain;

import java.util.UUID;

/**
 * Durable business-audit boundary for Workflow definition lifecycle events.
 * Implementations must persist append-only evidence; technical logs are not
 * sufficient for this contract.
 */
public interface WorkflowDefinitionAuditPort {

    enum Action {
        CREATE,
        ACTIVATE,
        DEACTIVATE,
        ARCHIVE,
        PUBLISH,
        NEXT_DRAFT
    }

    void record(UUID actorUserId,
                WorkflowDefinition before,
                WorkflowDefinition after,
                Action action);
}
