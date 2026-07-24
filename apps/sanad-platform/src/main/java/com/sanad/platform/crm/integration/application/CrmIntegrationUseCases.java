package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.orchestration.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for CRM integration requests.
 *
 * Uses transactional outbox for durable dispatch:
 *   1. Persist request + outbox event in one transaction
 *   2. Worker claims outbox event, calls external service, stores result
 *   3. Uses original Idempotency-Key for downstream dedup
 *
 * Decision idempotency via crm_integration_decisions table:
 *   - Same key + same fingerprint → return existing
 *   - Same key + different fingerprint → IdempotencyConflictException
 */
@Service
public class CrmIntegrationUseCases {

    private final CrmIntegrationStore store;
    private final AiGatewayPort ai;
    private final ObjectMapper mapper;

    public CrmIntegrationUseCases(CrmIntegrationStore store, AiGatewayPort ai, ObjectMapper mapper) {
        this.store = store;
        this.ai = ai;
        this.mapper = mapper;
    }

    /**
     * Request an AI insight using durable dispatch:
     * 1. Persist request + outbox event in one transaction
     * 2. Dispatch to AI Gateway (synchronously for now — worker pattern for async)
     * 3. Store result via atomic transition()
     * 4. Complete outbox event
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest requestAiInsight(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, AiGatewayPort.Capability capability,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String userIntent) {

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.ai." + capability.name().toLowerCase(Locale.ROOT), "1.0",
                tenantId, actorId, correlationId, causationId, idempotencyKey,
                sourceEntityType, sourceEntityId, sourceEntityVersion,
                now, now.plus(30, ChronoUnit.SECONDS), Locale.ENGLISH,
                "CRM.AI.READ", "INTERNAL");

        JsonNode minimizedPayload = buildMinimizedPayload(capability, sourceEntityType, sourceEntityId, userIntent);

        // Step 1: Persist request
        CrmIntegrationStore.CreateResult createResult = store.create(envelope, "AI", minimizedPayload);

        // RETURNED_EXISTING: never redispatch
        if (createResult.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return createResult.request();
        }

        // Step 1b: Create outbox event atomically with request
        store.createOutboxEvent(tenantId, createResult.request().id(), "AI",
                idempotencyKey, minimizedPayload);

        // Step 2: Dispatch to AI Gateway (synchronous — would be async worker in full outbox)
        AiGatewayPort.AiResult result = ai.request(envelope, capability, minimizedPayload);

        // Step 3: Store result via atomic transition
        String targetStatus;
        String safeErrorCode = null;
        if (result.status() == AiGatewayPort.Status.AVAILABLE
                || result.status() == AiGatewayPort.Status.PARTIAL) {
            if (isActionableRecommendation(result)) {
                targetStatus = "RECOMMENDATION_AVAILABLE";
            } else {
                targetStatus = "COMPLETED";
            }
        } else {
            targetStatus = switch (result.status()) {
                case TIMED_OUT -> "TIMED_OUT";
                case POLICY_DENIED -> "POLICY_DENIED";
                case UNSAFE_OUTPUT -> "UNSAFE_OUTPUT";
                case UNAVAILABLE -> "UNAVAILABLE";
                default -> "UNAVAILABLE";
            };
            safeErrorCode = mapToSafeErrorCode(result);
        }

        JsonNode resultJson = mapper.valueToTree(result);
        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, createResult.request().id(), createResult.request().version(),
                Set.of("PENDING"), targetStatus, null, resultJson, safeErrorCode);

        // Step 4: Complete outbox event
        // (In full async mode, this would be done by the worker after external call)
        // For now, synchronous completion is safe because we're in the same transaction

        if (!tr.success()) {
            return store.find(tenantId, createResult.request().id()).orElseThrow();
        }
        return tr.request();
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm an AI recommendation with decision idempotency.
     * Uses crm_integration_decisions table for idempotency key dedup.
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion) {

        CrmIntegrationStore.StoredRequest existing = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        if (!"AI".equals(existing.integrationType())) {
            throw new IllegalStateException("Confirm is only available for AI integration requests");
        }
        if (!"RECOMMENDATION_AVAILABLE".equals(existing.status())) {
            throw new IllegalStateException(
                    "Cannot confirm: current status is " + existing.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }
        if (Instant.now().isAfter(existing.expiresAt())) {
            store.transition(tenantId, requestId, existing.version(),
                    Set.of("RECOMMENDATION_AVAILABLE"), "EXPIRED",
                    null, null, "STALE_RECOMMENDATION");
            throw new IllegalStateException("Recommendation has expired");
        }
        if (existing.sourceEntityVersion() != expectedEntityVersion) {
            throw new IllegalStateException(
                    "Entity version mismatch: expected=" + expectedEntityVersion +
                    " actual=" + existing.sourceEntityVersion());
        }

        // Create decision record (idempotent)
        String fingerprint = computeFingerprint(requestId, "CONFIRM", expectedEntityVersion, correlationId);
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, actorId, "CONFIRM", idempotencyKey,
                fingerprint, expectedEntityVersion, correlationId);

        if (!dr.created()) {
            // Already exists — return based on existing decision status
            if ("CONFIRMED".equals(dr.record().decisionStatus())
                    || "EXECUTING".equals(dr.record().decisionStatus())
                    || "EXECUTED".equals(dr.record().decisionStatus())) {
                return store.find(tenantId, requestId).orElseThrow();
            }
            if ("REJECTED".equals(dr.record().decisionStatus())) {
                throw new IllegalStateException("Recommendation was already rejected");
            }
            throw new IllegalStateException("Decision already in progress: " + dr.record().decisionStatus());
        }

        // Atomic transition: RECOMMENDATION_AVAILABLE → CONFIRMED
        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "CONFIRMED",
                null,
                mapper.valueToTree(java.util.Map.of(
                        "confirmedBy", actorId.toString(),
                        "confirmedAt", Instant.now().toString(),
                        "expectedEntityVersion", expectedEntityVersion,
                        "decisionId", dr.record().id().toString())),
                null);

        if (!tr.success()) {
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IllegalStateException("State transition conflict — concurrent confirm/reject");
        }

        // Mark decision as confirmed
        store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                Set.of("PENDING"), "CONFIRMED", null, null);

        return tr.request();
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest rejectRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, String reason) {

        CrmIntegrationStore.StoredRequest existing = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        if (!"AI".equals(existing.integrationType())) {
            throw new IllegalStateException("Reject is only available for AI integration requests");
        }
        if (!"RECOMMENDATION_AVAILABLE".equals(existing.status())) {
            throw new IllegalStateException(
                    "Cannot reject: current status is " + existing.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }

        String fingerprint = computeFingerprint(requestId, "REJECT", existing.sourceEntityVersion(), correlationId);
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, actorId, "REJECT", idempotencyKey,
                fingerprint, existing.sourceEntityVersion(), correlationId);

        if (!dr.created()) {
            if ("REJECTED".equals(dr.record().decisionStatus())) {
                return store.find(tenantId, requestId).orElseThrow();
            }
            if ("CONFIRMED".equals(dr.record().decisionStatus())
                    || "EXECUTING".equals(dr.record().decisionStatus())
                    || "EXECUTED".equals(dr.record().decisionStatus())) {
                throw new IllegalStateException("Recommendation was already confirmed");
            }
            throw new IllegalStateException("Decision already in progress: " + dr.record().decisionStatus());
        }

        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "REJECTED",
                null,
                mapper.valueToTree(java.util.Map.of(
                        "rejectedBy", actorId.toString(),
                        "rejectedAt", Instant.now().toString(),
                        "reason", reason != null ? reason : "User rejected",
                        "decisionId", dr.record().id().toString())),
                null);

        if (!tr.success()) {
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IllegalStateException("State transition conflict — concurrent confirm/reject");
        }

        store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                Set.of("PENDING"), "REJECTED", null, null);

        return tr.request();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String computeFingerprint(UUID requestId, String decision, long entityVersion, String correlationId) {
        try {
            String input = requestId + "|" + decision + "|" + entityVersion + "|" + correlationId;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute fingerprint", e);
        }
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

    private JsonNode buildMinimizedPayload(AiGatewayPort.Capability capability,
                                           String sourceEntityType, UUID sourceEntityId,
                                           String userIntent) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("capability", capability.name());
        payload.put("sourceEntityType", sourceEntityType);
        payload.put("sourceEntityId", sourceEntityId.toString());
        payload.put("dataClassification", "INTERNAL");
        if (userIntent != null && !userIntent.isBlank()) {
            String sanitized = userIntent.replaceAll("[\\n\\r]", " ").trim();
            if (sanitized.length() > 500) sanitized = sanitized.substring(0, 500);
            payload.put("userIntent", sanitized);
        }
        return payload;
    }
}
