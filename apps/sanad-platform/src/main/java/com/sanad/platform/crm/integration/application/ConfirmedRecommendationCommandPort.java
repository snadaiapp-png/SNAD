package com.sanad.platform.crm.integration.application;

import java.util.UUID;

/**
 * Port for executing a confirmed AI recommendation as a normal CRM command.
 * Only allowlisted action codes are permitted.
 */
public interface ConfirmedRecommendationCommandPort {

    /**
     * Execute the confirmed recommendation.
     * Returns execution result with command reference.
     */
    CommandExecutionResult execute(ConfirmedRecommendation recommendation);

    record ConfirmedRecommendation(
            UUID tenantId,
            UUID actorId,
            UUID integrationRequestId,
            String actionCode,
            String sourceEntityType,
            UUID sourceEntityId,
            long sourceEntityVersion,
            String correlationId,
            UUID decisionId
    ) {}

    record CommandExecutionResult(
            boolean success,
            String commandType,
            String commandReference,
            String errorCode
    ) {}

    /** Allowlisted action codes that can be executed after confirmation. */
    java.util.Set<String> ALLOWED_ACTIONS = java.util.Set.of(
            "CREATE_FOLLOW_UP_ACTIVITY",
            "SCHEDULE_CONTACT",
            "REQUEST_OPPORTUNITY_REVIEW"
    );
}
