package com.sanad.platform.crm.integration.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation for test/local/crm-acceptance profiles only.
 * Production must use the composite adapter with real adapters —
 * ProductionWorkflowStubGuard will fail startup if this bean is
 * active in prod.
 */
@Component
@Profile({"test", "local", "crm-acceptance"})
public class StubConfirmedRecommendationCommandAdapter implements ConfirmedRecommendationCommandPort {

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!ALLOWED_ACTIONS.contains(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        String commandReference = recommendation.actionCode() + ":" + recommendation.decisionId();
        return new CommandExecutionResult(true, recommendation.actionCode(), commandReference, null);
    }

    @Override
    public Optional<CommandExecutionResult> findExisting(UUID tenantId,
                                                            UUID decisionId,
                                                            String actionCode) {
        // Stub does not persist artifacts — always returns empty
        return Optional.empty();
    }
}
