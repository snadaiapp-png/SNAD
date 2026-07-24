package com.sanad.platform.crm.integration.application;

import com.sanad.platform.crm.integration.orchestration.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Executes confirmed AI recommendations as normal CRM commands.
 * Only allowlisted action codes are permitted.
 *
 * Lifecycle: CONFIRMED → EXECUTING → EXECUTED or EXECUTION_REJECTED
 * Uses transitionStatus (preserves AI result_payload).
 */
@Component
public class ConfirmedRecommendationExecutor {

    private final CrmIntegrationStore store;
    private final ConfirmedRecommendationCommandPort commandPort;

    public ConfirmedRecommendationExecutor(
            CrmIntegrationStore store,
            ConfirmedRecommendationCommandPort commandPort) {
        this.store = store;
        this.commandPort = commandPort;
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest execute(
            UUID tenantId, UUID actorId, UUID requestId, UUID decisionId,
            String correlationId) {

        CrmIntegrationStore.StoredRequest request = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        if (!"CONFIRMED".equals(request.status())) {
            throw new IllegalStateException(
                    "Cannot execute: current status is " + request.status() +
                    ", expected CONFIRMED");
        }

        // Extract actionCode from immutable AI result_payload
        String actionCode = extractActionCode(request.resultPayload());
        if (actionCode == null || actionCode.isBlank()) {
            throw new IllegalStateException("No actionCode in AI result");
        }
        if (!ConfirmedRecommendationCommandPort.ALLOWED_ACTIONS.contains(actionCode)) {
            throw new IllegalStateException("Unknown actionCode not in allowlist: " + actionCode);
        }

        // Status-only transition: CONFIRMED → EXECUTING (preserves AI result)
        CrmIntegrationStore.TransitionResult execTransition = store.transitionStatus(
                tenantId, requestId, request.version(),
                Set.of("CONFIRMED"), "EXECUTING");

        if (!execTransition.success()) {
            throw new IllegalStateException("State transition conflict: CONFIRMED → EXECUTING");
        }

        // Update decision to EXECUTING (non-terminal, completed_at stays NULL)
        CrmIntegrationStore.DecisionRecord decision = store.findDecisionById(tenantId, requestId, decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found"));
        store.transitionDecision(tenantId, decisionId, decision.version(),
                Set.of("CONFIRMED"), "EXECUTING", null, null);

        // Build and execute command
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        tenantId, actorId, requestId, actionCode,
                        request.sourceEntityType(), request.sourceEntityId(),
                        request.sourceEntityVersion(), correlationId, decisionId);

        ConfirmedRecommendationCommandPort.CommandExecutionResult result =
                commandPort.execute(recommendation);

        // Status-only transition to final state (preserves AI result)
        String finalStatus = result.success() ? "EXECUTED" : "EXECUTION_REJECTED";
        String errorCode = result.success() ? null : result.errorCode();

        CrmIntegrationStore.TransitionResult finalTransition = store.transitionStatus(
                tenantId, requestId, execTransition.request().version(),
                Set.of("EXECUTING"), finalStatus);

        // Update decision
        String decisionFinalStatus = result.success() ? "EXECUTED" : "EXECUTION_REJECTED";
        store.transitionDecision(tenantId, decisionId,
                decision.version() + 1,
                Set.of("EXECUTING"), decisionFinalStatus,
                result.commandReference(), errorCode);

        return finalTransition.request();
    }

    private String extractActionCode(JsonNode resultPayload) {
        if (resultPayload == null) return null;
        if (resultPayload.has("actionCode")) {
            return resultPayload.get("actionCode").asText(null);
        }
        return null;
    }
}
