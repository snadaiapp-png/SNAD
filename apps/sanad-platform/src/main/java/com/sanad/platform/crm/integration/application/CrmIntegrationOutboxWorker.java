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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Outbox worker with proper transaction boundary separation.
 *
 * <p><strong>Transaction model:</strong></p>
 * <ol>
 *   <li>Transaction A: claim event (short, no HTTP).</li>
 *   <li>Transaction A2: transition request PENDING → DISPATCHED (atomic, with version check).</li>
 *   <li>No transaction: external HTTP call.</li>
 *   <li>Transaction B: validate claim ownership, persist result via
 *       {@link CrmIntegrationStore#transitionWithResult} (atomic — fails if
 *       result_payload already exists), then finalize outbox event
 *       (validates claim_token ownership). All in ONE transaction so a transition
 *       conflict rolls back the outbox completion.</li>
 * </ol>
 *
 * <p><strong>Result immutability:</strong> {@code transitionWithResult} includes
 * SQL-level {@code AND result_payload IS NULL} — concurrent workers or retry paths
 * cannot overwrite an existing AI result.</p>
 *
 * <p><strong>Envelope reconstruction:</strong> the envelope is rebuilt exclusively
 * from stored columns ({@code contract_name, contract_version, causation_id,
 * data_classification, requested_locale, actor_id, ...}). Missing required fields
 * surface as {@link IntegrationErrorCode#INVALID_CONTRACT} rather than guessed
 * defaults.</p>
 *
 * <p><strong>Dispatch status guard:</strong> before issuing the external call,
 * the worker verifies the request is in {@code PENDING} (or {@code DISPATCHED}
 * when recovering a previously claimed event). Requests in terminal or
 * already-confirmed states are not dispatched.</p>
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

    /** Event types this worker is allowed to claim — AI dispatch only. */
    public static final java.util.Set<String> ACCEPTED_EVENT_TYPES = java.util.Set.of("AI_REQUEST_DISPATCH");

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void processOutboxEvents() {
        while (true) {
            // Transaction A: claim — AI worker claims ONLY AI_REQUEST_DISPATCH events.
            // Never claims CONFIRMED_COMMAND_EXECUTION events (owned by ConfirmedRecommendationExecutor).
            var claimed = txTemplate.execute(status ->
                    store.claimNextOutboxEvent(workerId, claimTimeoutSeconds, ACCEPTED_EVENT_TYPES));
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
        log.info("Processing outbox event: tenant={}, request={}, type={}, attempt={}/{}",
                event.tenantId(), event.integrationRequestId(),
                event.eventType(), event.attemptCount(), event.maxAttempts());

        // Load integration request (no transaction needed for read)
        final CrmIntegrationStore.StoredRequest initialRequest = store.find(event.tenantId(), event.integrationRequestId())
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found for claimed outbox event"));

        // Dispatch status guard — only proceed if request is in a dispatchable state.
        // PENDING is the normal first-dispatch path; DISPATCHED is the recovery path
        // for a previously claimed event whose worker died mid-flight.
        if (!isDispatchableStatus(initialRequest.status())) {
            log.warn("Skipping dispatch for request {} — status {} is not dispatchable",
                    event.integrationRequestId(), initialRequest.status());
            // Finalize outbox without external call — the request is already in a
            // terminal or non-dispatchable state, so the outbox event is completed
            // to prevent further retries.
            txTemplate.execute(status -> {
                store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                        event.claimToken(), event.claimedBy());
                return null;
            });
            return;
        }

        // Transaction A2: transition to DISPATCHED (atomic — only if still PENDING)
        // If recovery (already DISPATCHED), skip this transition.
        if ("PENDING".equals(initialRequest.status())) {
            txTemplate.execute(status -> {
                CrmIntegrationStore.TransitionResult tr = store.transitionStatus(
                        event.tenantId(), event.integrationRequestId(),
                        initialRequest.version(), Set.of("PENDING"), "DISPATCHED");
                if (!tr.success()) {
                    throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                            "PENDING → DISPATCHED transition conflict for request "
                            + event.integrationRequestId());
                }
                return null;
            });
        }

        // Reload after transition
        final CrmIntegrationStore.StoredRequest request = store.find(event.tenantId(), event.integrationRequestId()).orElseThrow();

        // NO DB TRANSACTION: External HTTP call
        DispatchResult dispatchResult;
        try {
            dispatchResult = dispatchExternal(request, event);
        } catch (IntegrationException e) {
            log.error("External dispatch failed for request={}: {}",
                    event.integrationRequestId(), e.errorCode());
            txTemplate.execute(status -> {
                handleFailure(event, e.errorCode());
                return null;
            });
            return;
        } catch (Exception e) {
            log.error("External dispatch failed for request={}", event.integrationRequestId(), e);
            txTemplate.execute(status -> {
                handleFailure(event, classifyException(e));
                return null;
            });
            return;
        }

        // Transaction B: persist result + transition + finalize outbox (atomic)
        // If transitionWithResult fails (e.g. result_payload already exists from
        // a concurrent worker), the entire transaction rolls back — outbox stays
        // CLAIMED or recoverable, never COMPLETED.
        try {
            txTemplate.execute(status -> {
                handleSuccess(event, request, dispatchResult);
                return null;
            });
        } catch (IntegrationException e) {
            log.error("Finalization failed for request={}: {} — outbox NOT completed",
                    event.integrationRequestId(), e.errorCode(), e);
            // Outbox remains CLAIMED — will be recovered by next worker scan
            // once claim_expires_at passes.
        }
    }

    private boolean isDispatchableStatus(String status) {
        return "PENDING".equals(status) || "DISPATCHED".equals(status);
    }

    private DispatchResult dispatchExternal(CrmIntegrationStore.StoredRequest request,
                                             CrmIntegrationStore.OutboxEvent event) {
        if ("AI".equals(event.integrationType())) {
            return dispatchAi(request, event);
        }
        throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                "Unknown integration type: " + event.integrationType());
    }

    private DispatchResult dispatchAi(CrmIntegrationStore.StoredRequest request,
                                       CrmIntegrationStore.OutboxEvent event) {
        // Reconstruct envelope EXCLUSIVELY from stored values — no fallbacks.
        // Missing required fields surface as INVALID_CONTRACT.
        if (request.contractName() == null || request.contractName().isBlank()
                || request.contractVersion() == null || request.contractVersion().isBlank()
                || request.causationId() == null || request.causationId().isBlank()
                || request.dataClassification() == null || request.dataClassification().isBlank()
                || request.requiredCapability() == null || request.requiredCapability().isBlank()
                || request.requestedLocale() == null || request.requestedLocale().isBlank()) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Stored integration request is missing required envelope fields");
        }

        Locale locale = parseLocale(request.requestedLocale());

        IntegrationEnvelope envelope = new IntegrationEnvelope(
                request.contractName(),
                request.contractVersion(),
                request.tenantId(),
                request.actorId(),
                request.correlationId(),
                request.causationId(),
                request.idempotencyKey(),
                request.sourceEntityType(),
                request.sourceEntityId(),
                request.sourceEntityVersion(),
                request.requestedAt(),
                request.expiresAt(),
                locale,
                request.requiredCapability(),
                request.dataClassification());

        String capStr = event.payload() != null && event.payload().has("capability")
                ? event.payload().get("capability").asText() : null;
        if (capStr == null || capStr.isBlank()) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "AI outbox event payload missing capability");
        }
        AiGatewayPort.Capability capability;
        try {
            capability = AiGatewayPort.Capability.valueOf(capStr);
        } catch (IllegalArgumentException e) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Unknown AI capability: " + capStr);
        }

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

    /**
     * Persist AI result + finalize outbox — all in the caller's transaction.
     *
     * <p>If {@code transitionWithResult} fails (because result_payload already
     * exists — concurrent worker wrote first), this method throws
     * {@link IntegrationException} of type {@code STATE_TRANSITION_FAILED},
     * which rolls back the entire transaction. The outbox event stays CLAIMED
     * (or recoverable) and is NEVER marked COMPLETED with a missing result.</p>
     */
    private void handleSuccess(CrmIntegrationStore.OutboxEvent event,
                                CrmIntegrationStore.StoredRequest request,
                                DispatchResult result) {
        CrmIntegrationStore.TransitionResult tr = store.transitionWithResult(
                event.tenantId(), event.integrationRequestId(), request.version(),
                Set.of("DISPATCHED"), result.targetStatus(),
                null, result.resultJson(),
                result.errorCode() != null ? result.errorCode().name() : null);

        if (!tr.success()) {
            // Transition failed — either version mismatch (concurrent worker) or
            // result_payload already exists (immutable write protection).
            throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "transitionWithResult failed for request " + event.integrationRequestId()
                    + " — concurrent worker likely wrote result first; outbox NOT completed");
        }

        // Complete outbox event (validates claim_token ownership, clears claim fields)
        store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), event.claimedBy());

        log.info("Outbox event completed: request={}, status={}",
                event.integrationRequestId(), result.targetStatus());
    }

    private void handleFailure(CrmIntegrationStore.OutboxEvent event,
                                IntegrationErrorCode errorCode) {
        boolean retryable = errorCode.isRetryable() && event.attemptCount() < event.maxAttempts();
        store.failOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), event.claimedBy(),
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

    /**
     * Classify raw exceptions into typed {@link IntegrationErrorCode}.
     * Never stores raw exception text in the database.
     */
    private IntegrationErrorCode classifyException(Exception e) {
        if (e instanceof IntegrationException ie) return ie.errorCode();
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.http.HttpTimeoutException) {
            return IntegrationErrorCode.AI_GATEWAY_TIMEOUT;
        }
        if (cause instanceof java.net.ConnectException
                || cause instanceof java.net.UnknownHostException
                || cause instanceof java.io.IOException) {
            return IntegrationErrorCode.AI_GATEWAY_UNAVAILABLE;
        }
        if (cause instanceof java.security.SignatureException) {
            return IntegrationErrorCode.INVALID_SIGNATURE;
        }
        if (cause instanceof java.util.NoSuchElementException
                || cause instanceof IllegalArgumentException) {
            return IntegrationErrorCode.INVALID_CONTRACT;
        }
        return IntegrationErrorCode.UNKNOWN_ERROR;
    }

    private Locale parseLocale(String tag) {
        if (tag == null || tag.isBlank()) return Locale.ENGLISH;
        try {
            return Locale.forLanguageTag(tag);
        } catch (Exception e) {
            return Locale.ENGLISH;
        }
    }

    private record DispatchResult(String targetStatus, IntegrationErrorCode errorCode, JsonNode resultJson) {}
}
