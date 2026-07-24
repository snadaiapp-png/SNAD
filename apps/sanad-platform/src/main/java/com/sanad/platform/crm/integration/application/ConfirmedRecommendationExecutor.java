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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Executes confirmed AI recommendations via a durable execution outbox
 * with crash-safe recovery.
 *
 * <p><strong>Durable execution model:</strong></p>
 * <ol>
 *   <li>The confirm transaction (in {@link CrmIntegrationUseCases}) creates
 *       a {@code CONFIRMED_COMMAND_EXECUTION} outbox event atomically with
 *       the decision and request transitions. No CRM command is invoked
 *       in the confirm transaction.</li>
 *   <li>This worker claims only {@code CONFIRMED_COMMAND_EXECUTION} events
 *       (event-type-filtered claim — never claims AI events).</li>
 *   <li><strong>Transaction A:</strong> claim event, create/read execution
 *       ledger, transition decision and request to EXECUTING.</li>
 *   <li><strong>No shared transaction:</strong> execute CRM command using
 *       decisionId as idempotency key.</li>
 *   <li><strong>Transaction B:</strong> persist command outcome, transition
 *       request and decision, complete execution event.</li>
 * </ol>
 *
 * <p><strong>Crash recovery:</strong> if the worker crashes after the CRM
 * side effect but before Transaction B, the next worker reads the ledger.
 * If the ledger shows EXECUTING, the worker queries the CRM command result
 * by idempotency key (decisionId). If the command already exists, the
 * worker resumes finalization. If not, the worker retries idempotently.
 * If the result cannot be established, the worker marks the ledger
 * UNKNOWN_OUTCOME and the request EXECUTION_REJECTED.</p>
 *
 * <p>Lifecycle (integration request): CONFIRMED → EXECUTING → EXECUTED or EXECUTION_REJECTED
 * Lifecycle (decision): CONFIRMED → EXECUTING → EXECUTED or EXECUTION_REJECTED
 * Lifecycle (ledger): PENDING → EXECUTING → EXECUTED, EXECUTION_REJECTED, or UNKNOWN_OUTCOME</p>
 */
@Component
public class ConfirmedRecommendationExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConfirmedRecommendationExecutor.class);

    /** Event types this worker is allowed to claim. */
    public static final Set<String> ACCEPTED_EVENT_TYPES = Set.of("CONFIRMED_COMMAND_EXECUTION");

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
     * Called from {@code confirmRecommendation} inside the confirm transaction.
     * Only inserts an outbox event — no external call.
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
     * Worker loop — claims ONLY CONFIRMED_COMMAND_EXECUTION events.
     * Never claims AI_REQUEST_DISPATCH events (event-type-filtered claim).
     */
    @Scheduled(fixedDelay = 3000, initialDelay = 7000)
    public void processExecutionEvents() {
        while (true) {
            var claimed = txTemplate.execute(status ->
                    store.claimNextOutboxEvent(workerId, claimTimeoutSeconds, ACCEPTED_EVENT_TYPES));
            if (claimed == null || claimed.isEmpty()) break;
            CrmIntegrationStore.OutboxEvent event = claimed.get();
            try {
                processSingleExecutionEvent(event);
            } catch (Exception e) {
                log.error("Worker error processing execution event", e);
            }
        }
    }

    /**
     * Process a single execution event with crash-safe recovery.
     */
    public void processSingleExecutionEvent(CrmIntegrationStore.OutboxEvent event) {
        UUID decisionId = UUID.fromString(event.payload().get("decisionId").asText());
        UUID actorId = UUID.fromString(event.payload().get("actorId").asText());
        String correlationId = event.payload().get("correlationId").asText();
        String actionCode = event.payload().get("actionCode").asText();
        long expectedVersion = event.payload().get("expectedIntegrationVersion").asLong();

        // ===== Transaction A: claim ledger + transition to EXECUTING =====
        final CrmIntegrationStore.CommandExecutionLedger ledger;
        final CrmIntegrationStore.StoredRequest requestAfterExec;
        final CrmIntegrationStore.DecisionRecord decisionAfterExec;

        try {
            var txResult = txTemplate.execute(status -> {
                CrmIntegrationStore.StoredRequest request = store.find(event.tenantId(), event.integrationRequestId())
                        .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                                "Integration request not found for execution event"));

                if (!"CONFIRMED".equals(request.status()) && !"EXECUTING".equals(request.status())) {
                    log.warn("Skipping execution for request {} — status is {} (expected CONFIRMED or EXECUTING)",
                            event.integrationRequestId(), request.status());
                    store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                            event.claimToken(), event.claimedBy());
                    return null;
                }

                // Create or read the execution ledger (idempotent by decisionId)
                CrmIntegrationStore.LedgerResult lr = store.createExecutionLedger(
                        event.tenantId(), decisionId, event.integrationRequestId(),
                        actorId, actionCode, "exec-" + decisionId, event.claimToken());

                CrmIntegrationStore.CommandExecutionLedger led = lr.ledger();

                if ("EXECUTING".equals(request.status()) && lr.created()) {
                    // Recovery path: request is already EXECUTING but ledger is new?
                    // This shouldn't happen, but if it does, mark as conflict.
                    throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "Request is EXECUTING but ledger is new — inconsistent state");
                }

                if ("CONFIRMED".equals(request.status())) {
                    // Normal path: transition CONFIRMED → EXECUTING
                    CrmIntegrationStore.TransitionResult execTr = store.transitionStatus(
                            event.tenantId(), event.integrationRequestId(),
                            expectedVersion, Set.of("CONFIRMED"), "EXECUTING");
                    if (!execTr.success()) {
                        throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                                "CONFIRMED → EXECUTING transition conflict for request "
                                + event.integrationRequestId());
                    }
                    request = execTr.request();
                }

                // Transition decision CONFIRMED → EXECUTING
                CrmIntegrationStore.DecisionRecord decision = store.findDecisionById(
                        event.tenantId(), event.integrationRequestId(), decisionId)
                        .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                                "Decision not found: " + decisionId));

                if ("CONFIRMED".equals(decision.decisionStatus())) {
                    boolean decOk = store.transitionDecision(event.tenantId(), decisionId,
                            decision.version(), Set.of("CONFIRMED"), "EXECUTING", null, null);
                    if (!decOk) {
                        throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                                "Decision CONFIRMED → EXECUTING transition conflict");
                    }
                    decision = store.findDecisionById(event.tenantId(), event.integrationRequestId(), decisionId)
                            .orElseThrow();
                }

                // Transition ledger PENDING → EXECUTING (or stay EXECUTING on recovery)
                if ("PENDING".equals(led.executionStatus())) {
                    boolean ledOk = store.transitionExecutionLedger(
                            event.tenantId(), led.id(), led.version(),
                            Set.of("PENDING"), "EXECUTING", null, null, null);
                    if (!ledOk) {
                        throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                                "Ledger PENDING → EXECUTING transition conflict");
                    }
                    led = store.findExecutionLedger(event.tenantId(), decisionId).orElseThrow();
                }

                final CrmIntegrationStore.StoredRequest finalRequest = request;
                final CrmIntegrationStore.DecisionRecord finalDecision = decision;
                final CrmIntegrationStore.CommandExecutionLedger finalLedger = led;
                return new Object[]{finalRequest, finalDecision, finalLedger};
            });

            if (txResult == null) {
                // Event was completed (skipped) — nothing more to do
                return;
            }

            requestAfterExec = (CrmIntegrationStore.StoredRequest) txResult[0];
            decisionAfterExec = (CrmIntegrationStore.DecisionRecord) txResult[1];
            ledger = (CrmIntegrationStore.CommandExecutionLedger) txResult[2];
        } catch (IntegrationException e) {
            log.error("Transaction A failed for event {}: {}", event.id(), e.errorCode());
            // Outbox stays CLAIMED — will be recovered by next worker scan
            return;
        }

        // ===== No shared transaction: execute CRM command =====
        // The command port uses decisionId as idempotency key internally.
        // If the command was already executed (crash recovery), the port
        // returns the original result instead of re-executing.
        ConfirmedRecommendationCommandPort.ConfirmedRecommendation recommendation =
                new ConfirmedRecommendationCommandPort.ConfirmedRecommendation(
                        event.tenantId(), actorId, event.integrationRequestId(),
                        actionCode,
                        requestAfterExec.sourceEntityType(), requestAfterExec.sourceEntityId(),
                        requestAfterExec.sourceEntityVersion(), correlationId, decisionId);

        ConfirmedRecommendationCommandPort.CommandExecutionResult result;
        try {
            result = commandPort.execute(recommendation);
        } catch (Exception e) {
            log.error("CRM command execution failed for decision {}", decisionId, e);
            result = new ConfirmedRecommendationCommandPort.CommandExecutionResult(
                    false, actionCode, null,
                    IntegrationErrorCode.UNKNOWN_ERROR.name());
        }
        final ConfirmedRecommendationCommandPort.CommandExecutionResult finalResult = result;

        // ===== Transaction B: persist outcome + finalize =====
        try {
            txTemplate.execute(status -> {
                String finalStatus = finalResult.success() ? "EXECUTED" : "EXECUTION_REJECTED";
                String errorCode = finalResult.success() ? null : finalResult.errorCode();

                // Transition request EXECUTING → final
                CrmIntegrationStore.TransitionResult finalTr = store.transitionStatus(
                        event.tenantId(), event.integrationRequestId(),
                        requestAfterExec.version(), Set.of("EXECUTING"), finalStatus);
                if (!finalTr.success()) {
                    throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "EXECUTING → " + finalStatus + " transition conflict for request "
                            + event.integrationRequestId());
                }

                // Transition decision EXECUTING → final
                boolean decOk = store.transitionDecision(event.tenantId(), decisionId,
                        decisionAfterExec.version(),
                        Set.of("EXECUTING"), finalStatus,
                        finalResult.commandReference(), errorCode);
                if (!decOk) {
                    throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "Decision EXECUTING → " + finalStatus + " transition conflict");
                }

                // Transition ledger EXECUTING → final
                ObjectNode ledgerResult = mapper.createObjectNode();
                ledgerResult.put("success", finalResult.success());
                if (finalResult.commandReference() != null) {
                    ledgerResult.put("commandReference", finalResult.commandReference());
                }
                if (finalResult.commandType() != null) {
                    ledgerResult.put("commandType", finalResult.commandType());
                }

                boolean ledOk = store.transitionExecutionLedger(
                        event.tenantId(), ledger.id(), ledger.version(),
                        Set.of("EXECUTING"), finalStatus,
                        finalResult.commandReference(), errorCode, ledgerResult);
                if (!ledOk) {
                    throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "Ledger EXECUTING → " + finalStatus + " transition conflict");
                }

                // Complete outbox event (validates claim ownership, clears claim fields)
                store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                        event.claimToken(), event.claimedBy());
                return null;
            });

            log.info("Confirmed recommendation executed: request={}, decision={}, status={}",
                    event.integrationRequestId(), decisionId,
                    finalResult.success() ? "EXECUTED" : "EXECUTION_REJECTED");
        } catch (IntegrationException e) {
            log.error("Transaction B failed for event {}: {} — outbox NOT completed",
                    event.id(), e.errorCode(), e);
            // Outbox stays CLAIMED — will be recovered. On recovery, the ledger
            // will show EXECUTING and the command port will return the original
            // result (idempotent by decisionId).
        }
    }

    /**
     * Mark a ledger as UNKNOWN_OUTCOME when the command result cannot be
     * established after exhaustive retry. Called by an external recovery
     * process (not the scheduled worker) when the outbox event has been
     * CLAIMED for too long without finalization.
     */
    public void markUnknownOutcome(UUID tenantId, UUID decisionId) {
        CrmIntegrationStore.CommandExecutionLedger ledger = store.findExecutionLedger(tenantId, decisionId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Ledger not found for decision: " + decisionId));

        if (!"EXECUTING".equals(ledger.executionStatus())) {
            return; // Already finalized
        }

        txTemplate.execute(status -> {
            boolean ledOk = store.transitionExecutionLedger(
                    tenantId, ledger.id(), ledger.version(),
                    Set.of("EXECUTING"), "UNKNOWN_OUTCOME",
                    null, "COMMAND_RESULT_UNESTABLISHABLE", null);
            if (!ledOk) {
                throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                        "Ledger EXECUTING → UNKNOWN_OUTCOME transition conflict");
            }

            // Transition request EXECUTING → EXECUTION_REJECTED
            CrmIntegrationStore.StoredRequest request = store.find(tenantId, ledger.integrationRequestId())
                    .orElseThrow();
            store.transitionStatus(tenantId, ledger.integrationRequestId(),
                    request.version(), Set.of("EXECUTING"), "EXECUTION_REJECTED");

            // Transition decision EXECUTING → EXECUTION_REJECTED
            CrmIntegrationStore.DecisionRecord decision = store.findDecisionById(
                    tenantId, ledger.integrationRequestId(), decisionId)
                    .orElseThrow();
            store.transitionDecision(tenantId, decisionId, decision.version(),
                    Set.of("EXECUTING"), "EXECUTION_REJECTED",
                    null, "UNKNOWN_OUTCOME");
            return null;
        });
    }

    private String extractActionCode(JsonNode resultPayload) {
        if (resultPayload == null) return null;
        if (resultPayload.has("actionCode")) {
            return resultPayload.get("actionCode").asText(null);
        }
        return null;
    }
}
