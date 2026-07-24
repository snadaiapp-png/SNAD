package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub implementation of ConfirmedRecommendationCommandPort.
 * In production, this would dispatch to actual CRM use cases.
 * For now, it validates the action code and returns a reference.
 */
@Component
public class StubConfirmedRecommendationCommandAdapter implements ConfirmedRecommendationCommandPort {

    @Override
    public CommandExecutionResult execute(ConfirmedRecommendation recommendation) {
        // Validate action is in allowlist
        if (!ALLOWED_ACTIONS.contains(recommendation.actionCode())) {
            return new CommandExecutionResult(false, null, null, "UNKNOWN_ACTION_CODE");
        }

        // In a real implementation, this would:
        // 1. Route to the appropriate CRM use case based on actionCode
        // 2. Execute the command with tenant/actor/entity context
        // 3. Return the command reference
        // For now, return success with a reference
        String commandReference = recommendation.actionCode() + ":" + UUID.randomUUID();
        return new CommandExecutionResult(true, recommendation.actionCode(), commandReference, null);
    }
}
