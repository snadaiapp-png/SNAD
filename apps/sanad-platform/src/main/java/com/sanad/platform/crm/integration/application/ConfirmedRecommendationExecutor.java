package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.orchestration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Executes confirmed AI recommendations via a durable execution outbox.
 *
 * <p><strong>Durable execution model:</strong> the confirm transaction NEVER
 * calls a CRM command directly. Instead, it inserts a
 * {@code CONFIRMED_COMMAND_EXECUTION} outbox event. A scheduled worker
 * claims the event, transitions the request to EXECUTING, executes the real
 * CRM command idempotently by {@code decisionId}, and transitions to
 * EXECUTED or EXECUTION_REJECTED.</p>
 *
 * <p>Lifecycle (integration request): CONFIRMED → EXECUTING → EXECUTED or EXECUTION_REJECTED
 * Lifecycle (decision): CONFIRMED → EXECUTING → EXECUTED or EXECUTION_REJECTED</p>
 *
 * <p>Uses {@link CrmIntegrationStore#transitionStatus} (status-only) so the
 * original AI {@code result_payload} is preserved byte-for-byte.</p>
 */
@Component
public class ConfirmedRecommendationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConfirmedRecommendationExecutor.class);

    private final CrmIntegrationStore store;
    private final ConfirmedRecommendationCommandPort commandPort;
    private final ObjectMapper mapper;
    private final TransactionTemplate txTemplate;

    private final String workerId;
    private final int claimTimeoutSeconds;

    public ConfirmedRecommendationExecutor(
            CrmIntegrationStore store,
            ConfirmedRecommendationCommandPort commandPort,
            ObjectMapper mapper,
            TransactionTemplate txTemplate,
            @Value("${sanad.integration.worker-id:worker-1}") String workerId,
            @Value("${sanad.integration.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.store = store;
        this.commandPort = commandPort;
        this.mapper = mapper;
        this.txTemplate = txTemplate;
        this.workerId = workerId;
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    /**
     * Enqueue a durable execution event for a confirmed recommendation.
     * Called from {@code confirmRecommendation} (or its callers) AFTER the
     * decision has been recorded and the request transitioned to CONFIRMED.
     *
     * <p>This method is called inside the confirm transaction — it only
     * inserts an outbox event, no external call.</p>
     */
    public void enqueueExecution(UUID tenantId, UUID requestId, UUID decisionId,
                                  UUID actorId, String correlationId,
                                  long expectedIntegrationVersion) {
        CrmIntegrationStore.StoredRequest request = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found"));
        if (!"CONFIRMED".equals(request.status())) {
            throw new IntegrationException(IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Cannot enqueue execution: current status is " + request.status()
                    + ", expected CONFIRMED");
        }
        String actionCode = extractActionCode(request.resultPayload());
        if (actionCode == null || actionCode.isBlank()) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "No actionCode in AI result");
        }
        if (!ConfirmedRecommendationCommandPort.ALLOWED_ACTIONS.contains(actionCode)) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Unknown actionCode not in allowlist: " + actionCode);
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.put("decisionId", decisionId.toString());
        payload.put("actorId", actorId.toString());
        payload.put("correlationId", correlationId);
        payload.put("actionCode", actionCode);
        payload.put("expectedIntegrationVersion", expectedIntegrationVersion);

        store.createOutboxEvent(tenantId, requestId, "AI",
                "CONFIRMED_COMMAND_EXECUTION", "exec-" + decisionId, payload);
    }

    /**
     * Worker loop — claims and executes confirmed-command events.
     */
    @Scheduled(fixedDelay = 3000, initialDelay = 7000)
    public void processExecutionEvents() {
        while (true) {
            var claimed = txTemplate.execute(status ->
                    store.claimNextOutboxEvent(workerId, claimTimeoutSeconds));
            if (claimed == null || claimed.isEmpty()) break;
            CrmIntegrationStore.OutboxEvent event = claimed.get();
            if (!"CONFIRMED_COMMAND_EXECUTION".equals(event.eventType())) {
                // Not ours — leave it for another worker / next scan
                // (In practice the outbox supports multiple event types and each
                // worker can handle all; for now, skip non-execution events.)
                continue;
            }
            try {
                processSingleExecutionEvent(event);
            } catch (Exception e) {
                log.error("Worker error processing execution event", e);
            }
        }
    }

    void processSingleExecutionEvent(CrmIntegrationStore.OutboxEvent event) {
        UUID decisionId = UUID.fromString(event.payload().get("decisionId").asText());
        UUID actorId = UUID.fromString(event.payload().get("actorId").asText());
        String correlationId = event.payload().get("correlationId").asText();
        String actionCode = event.payload().get("actionCode").asText();
        long expectedVersion = event.payload().get("expectedIntegrationVersion").asLong();

        // Transition request CONFIRMED → EXECUTING (atomic, status-only — preserves AI result)
        CrmIntegrationStore.StoredRequest request = store.find(event.tenantId(), event.integrationRequestId())
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found for execution event"));

        if (!"CONFIRMED".equals(request.status())) {
            log.warn("Skipping execution for request {} — status is {} (expected CONFIRMED)",
                    event.integrationRequestId(), request.status());
            txTemplate.execute(status -> {
                store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                        event.claimToken(), workerId);
                return null;
            });
            return;
        }

        CrmIntegrationStore.TransitionResult execTr = store.transitionStatus(
                event.tenantId(), event.integrationRequestId(),
                expectedVersion, Set.of("CONFIRMED"), "EXECUTING");
        if (!execTr.success()) {
            log.warn("CONFIRMED → EXECUTING transition conflict for request {} — outbox stays CLAIMED",
                    event.integrationRequestId());
            return;
        }

        // Update decision to EXECUTING (non-terminal, completed_at stays NULL)
        CrmIntegrationStore.DecisionRecord decision = store.findDecisionById(
                event.tenantId(), event.integrationRequestId(), decisionId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Decision not found: " + decisionId));
        store.transitionDecision(event.tenantId(), decisionId, decision.version(),
                Set.of("CONFIRMED"), "EXECUTING", null, null);

        // Build and execute command (no DB transaction needed for the call itself;
        // any CRM-side mutation is committed by the underlying use case).
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        event.tenantId(), actorId, event.integrationRequestId(),
                        actionCode,
                        request.sourceEntityType(), request.sourceEntityId(),
                        request.sourceEntityVersion(), correlationId, decisionId);

        ConfirmedRecommendationCommandPort.CommandExecutionResult result =
                commandPort.execute(recommendation);

        // Finalize: status-only transition (preserves AI result)
        txTemplate.execute(status -> {
            String finalStatus = result.success() ? "EXECUTED" : "EXECUTION_REJECTED";
            String errorCode = result.success() ? null : result.errorCode();

            CrmIntegrationStore.TransitionResult finalTr = store.transitionStatus(
                    event.tenantId(), event.integrationRequestId(),
                    execTr.request().version(), Set.of("EXECUTING"), finalStatus);
            if (!finalTr.success()) {
                throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                        "EXECUTING → " + finalStatus + " transition conflict for request "
                        + event.integrationRequestId());
            }

            String decisionFinalStatus = result.success() ? "EXECUTED" : "EXECUTION_REJECTED";
            store.transitionDecision(event.tenantId(), decisionId,
                    decision.version() + 1,
                    Set.of("EXECUTING"), decisionFinalStatus,
                    result.commandReference(), errorCode);

            store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                    event.claimToken(), workerId);
            return null;
        });

        log.info("Confirmed recommendation executed: request={}, decision={}, status={}",
                event.integrationRequestId(), decisionId,
                result.success() ? "EXECUTED" : "EXECUTION_REJECTED");
    }

    private String extractActionCode(JsonNode resultPayload) {
        if (resultPayload == null) return null;
        if (resultPayload.has("actionCode")) {
            return resultPayload.get("actionCode").asText(null);
        }
        return null;
    }
}
