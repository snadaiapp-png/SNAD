package com.sanad.platform.workflow.domain;

import java.util.UUID;

/** Raised when a system action fails permanently — carries the incident id. */
public class WorkflowSystemActionException extends IllegalStateException {

    private final UUID incidentId;

    public WorkflowSystemActionException(String message, UUID incidentId) {
        super(message);
        this.incidentId = incidentId;
    }

    public UUID incidentId() {
        return incidentId;
    }
}
