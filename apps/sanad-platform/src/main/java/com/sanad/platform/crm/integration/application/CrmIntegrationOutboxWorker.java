package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.orchestration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbox worker that claims pending events and dispatches to external services.
 *
 * Uses FOR UPDATE SKIP LOCKED for atomic claim.
 * Supports claim expiry recovery, bounded retry, and dead-letter.
 * Uses original Idempotency-Key for downstream dedup.
 */
@Component
public class CrmIntegrationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(CrmIntegrationOutboxWorker.class);

    private final CrmIntegrationStore store;
    private final AiGatewayPort ai;
    private final ObjectMapper mapper;

    private final String workerId;
    private final int claimTimeoutSeconds;

    private static final Set<String> NON_RETRYABLE_ERRORS = Set.of(
            "AI_POLICY_DENIED", "AI_UNSAFE_OUTPUT", "INVALID_CONTRACT",
            "INVALID_SIGNATURE", "UNAUTHORIZED_SERVICE", "INVALID_TENANT",
            "WORKFLOW_POLICY_DENIED");

    public CrmIntegrationOutboxWorker(
            CrmIntegrationStore store,
            AiGatewayPort ai,
            ObjectMapper mapper,
            @Value("${sanad.integration.worker-id:worker-1}") String workerId,
            @Value("${sanad.integration.claim-timeout-seconds:60}") int claimTimeoutSeconds) {
        this.store = store;
        this.ai = ai;
        this.mapper = mapper;
        this.workerId = workerId;
        this.claimTimeoutSeconds = Math.max(10, Math.min(claimTimeoutSeconds, 300));
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void processOutboxEvents() {
        while (true) {
            var claimed = store.claimNextOutboxEvent(workerId, claimTimeoutSeconds);
            if (claimed.isEmpty()) break;
            try {
                processSingleEvent(claimed.get());
            } catch (Exception e) {
                log.error("Worker error processing outbox event", e);
            }
        }
    }

    @Transactional
    void processSingleEvent(CrmIntegrationStore.OutboxEvent event) {
        log.info("Processing outbox event: tenant={}, request={}, attempt={}/{}",
                event.tenantId(), event.integrationRequestId(),
                event.attemptCount(), event.maxAttempts());

        // Load the integration request
        CrmIntegrationStore.StoredRequest request = store.find(event.tenantId(), event.integrationRequestId())
                .orElseThrow(() -> new IllegalStateException("Integration request not found for outbox event"));

        // Transition request to DISPATCHED
        store.transition(event.tenantId(), event.integrationRequestId(), request.version(),
                Set.of("PENDING"), "DISPATCHED", null, null, null);

        // Reload after transition
        request = store.find(event.tenantId(), event.integrationRequestId()).orElseThrow();

        try {
            // Dispatch to external service based on type
            JsonNode result;
            String targetStatus;
            String safeErrorCode = null;

            if ("AI".equals(event.integrationType())) {
                AiGatewayPort.AiResult aiResult = dispatchAi(request, event);
                result = mapper.valueToTree(aiResult);
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
                    safeErrorCode = mapToSafeErrorCode(aiResult);
                }
            } else {
                throw new IllegalStateException("Unknown integration type: " + event.integrationType());
            }

            // Store result via atomic transition
            store.transition(event.tenantId(), event.integrationRequestId(), request.version(),
                    Set.of("DISPATCHED"), targetStatus, null, result, safeErrorCode);

            // Complete outbox event
            store.completeOutboxEvent(event.tenantId(), event.id(), event.version());

            log.info("Outbox event completed: request={}, status={}", event.integrationRequestId(), targetStatus);

        } catch (Exception e) {
            String errorCode = e.getMessage() != null ? e.getMessage().substring(0, Math.min(120, e.getMessage().length())) : "UNKNOWN_ERROR";
            boolean retryable = !NON_RETRYABLE_ERRORS.contains(errorCode) && event.attemptCount() < event.maxAttempts();
            store.failOutboxEvent(event.tenantId(), event.id(), event.version(), errorCode, retryable);

            if (!retryable) {
                // Mark request as UNAVAILABLE when dead-lettered
                store.transition(event.tenantId(), event.integrationRequestId(), request.version(),
                        Set.of("DISPATCHED"), "UNAVAILABLE", null, null, errorCode);
            }

            log.warn("Outbox event failed: request={}, error={}, retryable={}", event.integrationRequestId(), errorCode, retryable);
        }
    }

    private AiGatewayPort.AiResult dispatchAi(CrmIntegrationStore.StoredRequest request,
                                               CrmIntegrationStore.OutboxEvent event) {
        // Reconstruct envelope from stored request
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.ai", "1.0",
                request.tenantId(), request.actorId() != null ? request.actorId() : UUID.randomUUID(),
                request.correlationId(), request.correlationId(),
                request.idempotencyKey(), request.sourceEntityType(),
                request.sourceEntityId(), request.sourceEntityVersion(),
                request.requestedAt(), request.expiresAt(),
                java.util.Locale.ENGLISH, request.requiredCapability(), "INTERNAL");

        // Parse capability from payload
        String capStr = event.payload() != null && event.payload().has("capability")
                ? event.payload().get("capability").asText() : "CUSTOMER_SUMMARY";
        AiGatewayPort.Capability capability = AiGatewayPort.Capability.valueOf(capStr);

        return ai.request(envelope, capability, event.payload());
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

    private String mapToSafeErrorCode(AiGatewayPort.AiResult result) {
        return switch (result.status()) {
            case UNAVAILABLE -> "AI_GATEWAY_UNAVAILABLE";
            case TIMED_OUT -> "AI_GATEWAY_TIMEOUT";
            case POLICY_DENIED -> "AI_POLICY_DENIED";
            case UNSAFE_OUTPUT -> "AI_UNSAFE_OUTPUT";
            default -> "AI_UNKNOWN_ERROR";
        };
    }
}
