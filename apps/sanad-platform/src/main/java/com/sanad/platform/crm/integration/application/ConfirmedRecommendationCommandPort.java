package com.sanad.platform.crm.integration.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for executing a confirmed AI recommendation as a normal CRM command.
 * Only allowlisted action codes are permitted.
 *
 * <p>Each adapter must implement {@link #findExisting} to support crash
 * recovery. When the execution worker reclaims an event whose ledger is
 * in EXECUTING state, it calls {@code findExisting} first:</p>
 * <ul>
 *   <li>Found → finalize without recreating the artifact</li>
 *   <li>Not found → execute idempotently (the adapter's atomic
 *       INSERT...ON CONFLICT ensures no duplicate)</li>
 *   <li>Indeterminate → UNKNOWN_OUTCOME policy</li>
 * </ul>
 */
public interface ConfirmedRecommendationCommandPort {

    /**
     * Execute the confirmed recommendation.
     * Returns execution result with command reference.
     */
    CommandExecutionResult execute(ConfirmedRecommendation recommendation);

    /**
     * Find an existing command execution result for the given decisionId
     * and actionCode. Used by crash recovery to determine whether the
     * CRM command was already executed.
     *
     * @return {@link Optional#empty()} if no prior execution exists,
     *         or {@link Optional#of(result)} if the prior result can be
     *         established.
     */
    Optional<CommandExecutionResult> findExisting(UUID tenantId,
                                                    UUID decisionId,
                                                    String actionCode);

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
