package com.sanad.platform.crm.integration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.orchestration.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for CRM integration requests.
 *
 * Enforces:
 * - Idempotency: RETURNED_EXISTING never redispatches
 * - Durable dispatch: persist + commit, then dispatch, then store result
 * - State machine: RECOMMENDATION_AVAILABLE for actionable, COMPLETED for read-only
 * - Human confirmation: CONFIRMED/REJECTED only from RECOMMENDATION_AVAILABLE via transition()
 * - Entity version validation
 * - Safe error codes
 */
@Service
public class CrmIntegrationUseCases {

    private final CrmIntegrationStore store;
    private final AiGatewayPort ai;
    private final ObjectMapper mapper;

    private static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "EXECUTED", "REJECTED", "POLICY_DENIED", "UNSAFE_OUTPUT",
            "TIMED_OUT", "UNAVAILABLE", "CANCELLED", "EXPIRED", "EXECUTION_REJECTED");

    public CrmIntegrationUseCases(CrmIntegrationStore store, AiGatewayPort ai, ObjectMapper mapper) {
        this.store = store;
        this.ai = ai;
        this.mapper = mapper;
    }

    /**
     * Request an AI insight using durable dispatch pattern:
     * 1. Persist request (PENDING) and commit transaction
     * 2. Dispatch to AI Gateway (outside transaction)
     * 3. Store result via atomic transition()
     */
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

        // Step 2: Dispatch to AI Gateway (outside any enclosing transaction)
        AiGatewayPort.AiResult result = ai.request(envelope, capability, minimizedPayload);

        // Step 3: Store result via atomic transition
        String targetStatus;
        String safeErrorCode = null;

        if (result.status() == AiGatewayPort.Status.AVAILABLE
                || result.status() == AiGatewayPort.Status.PARTIAL) {
            // Check if actionable recommendation
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

        if (!tr.success()) {
            // Transition failed — another worker may have completed it
            return store.find(tenantId, createResult.request().id()).orElseThrow();
        }

        return tr.request();
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm an AI recommendation.
     * Only allowed from RECOMMENDATION_AVAILABLE state.
     * Validates entity version match.
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion) {

        CrmIntegrationStore.StoredRequest existing = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        // Must be AI type
        if (!"AI".equals(existing.integrationType())) {
            throw new IllegalStateException("Confirm is only available for AI integration requests");
        }

        // Must be in RECOMMENDATION_AVAILABLE state
        if (!"RECOMMENDATION_AVAILABLE".equals(existing.status())) {
            throw new IllegalStateException(
                    "Cannot confirm: current status is " + existing.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }

        // Stale recommendation rejection
        if (Instant.now().isAfter(existing.expiresAt())) {
            store.transition(tenantId, requestId, existing.version(),
                    Set.of("RECOMMENDATION_AVAILABLE"), "EXPIRED",
                    null, null, "STALE_RECOMMENDATION");
            throw new IllegalStateException("Recommendation has expired");
        }

        // Validate entity version matches recommendation source
        if (existing.sourceEntityVersion() != expectedEntityVersion) {
            throw new IllegalStateException(
                    "Entity version mismatch: expected=" + expectedEntityVersion +
                    " actual=" + existing.sourceEntityVersion() +
                    " — recommendation is stale");
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
                        "idempotencyKey", idempotencyKey)),
                null);

        if (!tr.success()) {
            // Concurrent confirm/reject won
            CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId).orElseThrow();
            if ("REJECTED".equals(current.status())) {
                throw new IllegalStateException("Recommendation was already rejected");
            }
            if ("CONFIRMED".equals(current.status())) {
                return current; // Already confirmed — idempotent
            }
            throw new IllegalStateException("State transition conflict: " + current.status());
        }

        return tr.request();
    }

    /**
     * Reject an AI recommendation.
     */
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

        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "REJECTED",
                null,
                mapper.valueToTree(java.util.Map.of(
                        "rejectedBy", actorId.toString(),
                        "rejectedAt", Instant.now().toString(),
                        "reason", reason != null ? reason : "User rejected",
                        "idempotencyKey", idempotencyKey)),
                null);

        if (!tr.success()) {
            CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId).orElseThrow();
            if ("CONFIRMED".equals(current.status())) {
                throw new IllegalStateException("Recommendation was already confirmed");
            }
            if ("REJECTED".equals(current.status())) {
                return current; // Already rejected — idempotent
            }
            throw new IllegalStateException("State transition conflict: " + current.status());
        }

        return tr.request();
    }

    // ============================================================
    // Helpers
    // ============================================================

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
