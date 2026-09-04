package com.sanad.platform.workflow.domain;

/**
 * Raised when a command loses an optimistic-concurrency race (claim,
 * complete, reassign, release, publish) or targets state that another
 * actor already changed. Extends {@link IllegalStateException} so the
 * platform's existing 409 mapping applies.
 */
public class WorkflowVersionConflictException extends IllegalStateException {

    public WorkflowVersionConflictException(String message) {
        super(message);
    }
}
