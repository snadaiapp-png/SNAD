package com.sanad.platform.crm.integration.application;

import java.util.UUID;

/**
 * Fault injector for testing crash-after-side-effect recovery.
 *
 * <p>When enabled, throws an exception after the CRM command commits
 * but before Transaction B (finalization). This simulates a worker
 * crash between the side effect and the outbox completion.</p>
 *
 * <p>Enabled only in test profiles via a system property or bean
 * injection. Production code never activates the fault.</p>
 */
public interface AfterCommandCommitFaultInjector {

    /**
     * Called after the CRM command commits but before Transaction B.
     * If enabled, throws a runtime exception to simulate a crash.
     *
     * @param decisionId the decision being executed
     */
    void injectFault(UUID decisionId);

    /**
     * No-op fault injector for production and default test behaviour.
     */
    AfterCommandCommitFaultInjector NO_OP = decisionId -> {};
}
