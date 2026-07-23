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
import java.util.UUID;

/**
 * Application service for CRM integration requests.
 *
 * Enforces:
 * - Idempotency: RETURNED_EXISTING never redispatches (uses CreateResult.disposition)
 * - State machine: RECOMMENDATION_AVAILABLE for actionable AI, COMPLETED for read-only
 * - Human confirmation: CONFIRMED/REJECTED only from RECOMMENDATION_AVAILABLE
 * - Optimistic locking: conditionalUpdate with version check
 * - Data minimization: backend builds projection, never trusts client payload
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

        // Create with explicit disposition
        CrmIntegrationStore.CreateResult createResult = store.create(envelope, "AI", minimizedPayload);

        // RETURNED_EXISTING: never redispatch — return immediately
        if (createResult.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return createResult.request();
        }

        // CREATED_NEW: dispatch to AI Gateway
        AiGatewayPort.AiResult result = ai.request(envelope, capability, minimizedPayload);

        // Map to state machine:
        // - Actionable results (humanConfirmationRequired=true) → RECOMMENDATION_AVAILABLE
        // - Non-actionable results (AVAILABLE/PARTIAL) → COMPLETED (read-only)
        // - Error results → terminal error states
        String status;
        String errorCode = null;
        if (result.status() == AiGatewayPort.Status.AVAILABLE || result.status() == AiGatewayPort.Status.PARTIAL) {
            if (result.humanConfirmationRequired()) {
                status = "RECOMMENDATION_AVAILABLE";
            } else {
                status = "COMPLETED";
            }
        } else {
            status = switch (result.status()) {
                case TIMED_OUT -> "TIMED_OUT";
                case POLICY_DENIED -> "POLICY_DENIED";
                case UNSAFE_OUTPUT -> "UNSAFE_OUTPUT";
                case UNAVAILABLE -> "UNAVAILABLE";
                default -> "UNAVAILABLE";
            };
            errorCode = result.explanation();
        }

        JsonNode resultJson = mapper.valueToTree(result);
        store.complete(tenantId, createResult.request().id(), status, null, resultJson, errorCode);

        return store.find(tenantId, createResult.request().id()).orElseThrow();
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm an AI recommendation.
     * Only allowed from RECOMMENDATION_AVAILABLE state.
     * Uses conditionalUpdate for atomic state transition with optimistic locking.
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
            store.conditionalUpdate(tenantId, requestId, existing.version(),
                    "EXPIRED", null, null, "STALE_RECOMMENDATION", "RECOMMENDATION_AVAILABLE");
            throw new IllegalStateException("Recommendation has expired");
        }

        // Atomic state transition: RECOMMENDATION_AVAILABLE → CONFIRMED
        // This is optimistic-locking safe — concurrent confirms will fail
        boolean updated = store.conditionalUpdate(tenantId, requestId, existing.version(),
                "CONFIRMED", null,
                mapper.valueToTree(java.util.Map.of(
                        "confirmedBy", actorId.toString(),
                        "confirmedAt", Instant.now().toString(),
                        "expectedEntityVersion", expectedEntityVersion)),
                null, "RECOMMENDATION_AVAILABLE");

        if (!updated) {
            // Another concurrent confirm/reject won — return current state
            return store.find(tenantId, requestId).orElseThrow();
        }

        return store.find(tenantId, requestId).orElseThrow();
    }

    /**
     * Reject an AI recommendation.
     * Only allowed from RECOMMENDATION_AVAILABLE state.
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

        boolean updated = store.conditionalUpdate(tenantId, requestId, existing.version(),
                "REJECTED", null,
                mapper.valueToTree(java.util.Map.of(
                        "rejectedBy", actorId.toString(),
                        "rejectedAt", Instant.now().toString(),
                        "reason", reason != null ? reason : "User rejected")),
                null, "RECOMMENDATION_AVAILABLE");

        if (!updated) {
            return store.find(tenantId, requestId).orElseThrow();
        }

        return store.find(tenantId, requestId).orElseThrow();
    }

    // ============================================================
    // Helpers
    // ============================================================

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
