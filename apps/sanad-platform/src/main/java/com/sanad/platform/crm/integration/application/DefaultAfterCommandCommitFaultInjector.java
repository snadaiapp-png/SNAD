package com.sanad.platform.crm.integration.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default no-op fault injector for production and non-test profiles.
 *
 * <p>In test profiles, a test-specific bean can override this to inject
 * faults at the AfterCommandCommit point.</p>
 */
@Component
@Profile({"!test", "!local", "!crm-acceptance"})
public class DefaultAfterCommandCommitFaultInjector implements AfterCommandCommitFaultInjector {

    @Override
    public void injectFault(UUID decisionId) {
        // No-op in production
    }
}
