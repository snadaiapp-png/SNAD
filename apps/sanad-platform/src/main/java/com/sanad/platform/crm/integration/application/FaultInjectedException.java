package com.sanad.platform.crm.integration.application;

/**
 * Exception thrown by {@link AfterCommandCommitFaultInjector} to simulate
 * a worker crash after the CRM command commits but before Transaction B.
 *
 * <p>This is a test-only exception — production code never throws it.
 * The executor catches it and skips Transaction B, leaving the outbox
 * event in CLAIMED state for the next worker to recover.</p>
 */
public class FaultInjectedException extends RuntimeException {
    public FaultInjectedException(String message) {
        super(message);
    }
}
