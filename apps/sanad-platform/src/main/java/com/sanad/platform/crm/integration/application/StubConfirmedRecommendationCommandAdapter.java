package com.sanad.platform.crm.integration.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub implementation for test/local only.
 * Production must use a real adapter — ProductionWorkflowStubGuard
 * will fail startup if this bean is active in prod.
 */
@Component
@Profile({"test", "local"})
public class StubConfirmedRecommendationCommandAdapter implements ConfirmedRecommendationCommandPort {

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        if (!ALLOWED_ACTIONS.contains(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }
        String commandReference = recommendation.actionCode() + ":" + UUID.randomUUID();
        return new CommandExecutionResult(true, recommendation.actionCode(), commandReference, null);
    }
}
