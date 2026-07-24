package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.orchestration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbox worker with proper transaction boundary separation.
 *
 * Transaction A: claim event (short, no HTTP)
 * No transaction: external HTTP call
 * Transaction B: validate claim, persist result, transition request, finalize outbox
 *
 * Uses claim_token for ownership verification — stale workers cannot complete.
 */
@Component
public class CrmIntegrationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(CrmIntegrationOutboxWorker.class);

    private final CrmIntegrationStore store;
    private final AiGatewayPort ai;
    private final ObjectMapper mapper;
    private final TransactionTemplate txTemplate;

    private final String workerId;
    private final int claimTimeoutSeconds;

    public CrmIntegrationOutboxWorker(
            CrmIntegrationStore store,
            AiGatewayPort ai,
            ObjectMapper mapper,
            TransactionTemplate txTemplate,
            @Value("${sanad.integration.worker-id:worker-1}") String workerId,
            @Value("${sanad.integration.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.store = store;
        this.ai = ai;
        this.mapper = mapper;
        this.txTemplate = txTemplate;
        this.workerId = workerId;
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void processOutboxEvents() {
        while (true) {
            // Transaction A: claim
            var claimed = txTemplate.execute(status ->
                    store.claimNextOutboxEvent(workerId, claimTimeoutSeconds));
            if (claimed == null || claimed.isEmpty()) break;
            try {
                processSingleEvent(claimed.get());
            } catch (Exception e) {
                log.error("Worker error processing outbox event", e);
            }
        }
    }

    /**
     * No @Transactional here — HTTP call must happen outside DB transaction.
     * Transaction B is explicit via txTemplate.
     */
    void processSingleEvent(CrmIntegrationStore.OutboxEvent event) {
        log.info("Processing outbox event: tenant={}, request={}, attempt={}/{}",
                event.tenantId(), event.integrationRequestId(),
                event.attemptCount(), event.maxAttempts());

        // Load integration request (no transaction needed for read)
        final CrmIntegrationStore.StoredRequest initialRequest = store.find(event.tenantId(), event.integrationRequestId())
                .orElseThrow(() -> new IllegalStateException("Integration request not found"));

        // Transaction A2: transition to DISPATCHED
        txTemplate.execute(status -> {
            store.transitionStatus(event.tenantId(), event.integrationRequestId(),
                    initialRequest.version(), Set.of("PENDING"), "DISPATCHED");
            return null;
        });

        // Reload after transition
        final CrmIntegrationStore.StoredRequest request = store.find(event.tenantId(), event.integrationRequestId()).orElseThrow();

        // NO DB TRANSACTION: External HTTP call
        DispatchResult dispatchResult;
        try {
            dispatchResult = dispatchExternal(request, event);
        } catch (Exception e) {
            log.error("External dispatch failed for request={}", event.integrationRequestId(), e);
            // Transaction B: handle failure
            txTemplate.execute(status -> {
                handleFailure(event, IntegrationErrorCode.UNKNOWN_ERROR);
                return null;
            });
            return;
        }

        // Transaction B: persist result + transition + finalize outbox (atomic)
        txTemplate.execute(status -> {
            handleSuccess(event, request, dispatchResult);
            return null;
        });
    }

    private DispatchResult dispatchExternal(CrmIntegrationStore.StoredRequest request,
                                             CrmIntegrationStore.OutboxEvent event) {
        if ("AI".equals(event.integrationType())) {
            return dispatchAi(request, event);
        }
        throw new IllegalStateException("Unknown integration type: " + event.integrationType());
    }

    private DispatchResult dispatchAi(CrmIntegrationStore.StoredRequest request,
                                       CrmIntegrationStore.OutboxEvent event) {
        // Reconstruct envelope from STORED values (not guessed)
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                request.contractName() != null ? request.contractName() : "crm.ai",
                request.contractVersion() != null ? request.contractVersion() : "1.0",
                request.tenantId(),
                request.actorId(),
                request.correlationId(),
                request.causationId() != null ? request.causationId() : request.correlationId(),
                request.idempotencyKey(),
                request.sourceEntityType(),
                request.sourceEntityId(),
                request.sourceEntityVersion(),
                request.requestedAt(),
                request.expiresAt(),
                java.util.Locale.ENGLISH,
                request.requiredCapability(),
                request.dataClassification() != null ? request.dataClassification() : "INTERNAL");

        String capStr = event.payload() != null && event.payload().has("capability")
                ? event.payload().get("capability").asText() : "CUSTOMER_SUMMARY";
        AiGatewayPort.Capability capability = AiGatewayPort.Capability.valueOf(capStr);

        AiGatewayPort.AiResult aiResult = ai.request(envelope, capability, event.payload());

        String targetStatus;
        IntegrationErrorCode errorCode = null;
        if (aiResult.status() == AiGatewayPort.Status.AVAILABLE
                || aiResult.status() == AiGatewayPort.Status.PARTIAL) {
            targetStatus = isActionableRecommendation(aiResult) ? "RECOMMENDATION_AVAILABLE" : "COMPLETED";
        } else {
            targetStatus = switch (aiResult.status()) {
                case TIMED_OUT -> "TIMED_OUT";
                case POLICY_DENIED -> "POLICY_DENIED";
                case UNSAFE_OUTPUT -> "UNSAFE_OUTPUT";
                case UNAVAILABLE -> "UNAVAILABLE";
                default -> "UNAVAILABLE";
            };
            errorCode = mapToErrorCode(aiResult);
        }

        return new DispatchResult(targetStatus, errorCode, mapper.valueToTree(aiResult));
    }

    private void handleSuccess(CrmIntegrationStore.OutboxEvent event,
                                CrmIntegrationStore.StoredRequest request,
                                DispatchResult result) {
        // Transition request with result (writes result_payload exactly once)
        store.transitionWithResult(
                event.tenantId(), event.integrationRequestId(), request.version(),
                Set.of("DISPATCHED"), result.targetStatus(),
                null, result.resultJson(),
                result.errorCode() != null ? result.errorCode().name() : null);

        // Complete outbox event (validates claim_token ownership)
        store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), workerId);

        log.info("Outbox event completed: request={}, status={}",
                event.integrationRequestId(), result.targetStatus());
    }

    private void handleFailure(CrmIntegrationStore.OutboxEvent event,
                                IntegrationErrorCode errorCode) {
        boolean retryable = errorCode.isRetryable() && event.attemptCount() < event.maxAttempts();
        store.failOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), workerId,
                errorCode.name(), retryable);

        if (!retryable) {
            // Mark request as UNAVAILABLE when dead-lettered
            CrmIntegrationStore.StoredRequest req = store.find(event.tenantId(),
                    event.integrationRequestId()).orElseThrow();
            store.transitionStatus(event.tenantId(), event.integrationRequestId(),
                    req.version(), Set.of("DISPATCHED"), "UNAVAILABLE");
        }
        log.warn("Outbox event failed: request={}, error={}, retryable={}",
                event.integrationRequestId(), errorCode, retryable);
    }

    private boolean isActionableRecommendation(AiGatewayPort.AiResult result) {
        return result.actionable()
                && result.humanConfirmationRequired()
                && result.actionCode() != null && !result.actionCode().isBlank()
                && result.generatedAt() != null
                && result.expiresAt() != null && result.expiresAt().isAfter(Instant.now())
                && result.policyVersion() != null
                && result.modelVersion() != null;
    }

    private IntegrationErrorCode mapToErrorCode(AiGatewayPort.AiResult result) {
        return switch (result.status()) {
            case UNAVAILABLE -> IntegrationErrorCode.AI_GATEWAY_UNAVAILABLE;
            case TIMED_OUT -> IntegrationErrorCode.AI_GATEWAY_TIMEOUT;
            case POLICY_DENIED -> IntegrationErrorCode.AI_POLICY_DENIED;
            case UNSAFE_OUTPUT -> IntegrationErrorCode.AI_UNSAFE_OUTPUT;
            default -> IntegrationErrorCode.UNKNOWN_ERROR;
        };
    }

    private record DispatchResult(String targetStatus, IntegrationErrorCode errorCode, JsonNode resultJson) {}
}
