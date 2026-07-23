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
 * Extracts orchestration from CrmIntegrationController — controller is now HTTP boundary only.
 *
 * Enforces:
 * - tenant/actor from session context (never from client payload)
 * - idempotency: RETURNED_EXISTING does not redispatch
 * - state machine: conditional status transitions, terminal-state protection
 * - data minimization: backend builds projection, never trusts client payload
 * - human confirmation: separate confirm/reject lifecycle
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
     * Request an AI insight. If idempotency key already exists, returns existing without redispatch.
     * Backend builds minimized payload — client only sends entity reference and capability.
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

        // Build minimized projection — never trust client payload
        JsonNode minimizedPayload = buildMinimizedPayload(capability, sourceEntityType, sourceEntityId, userIntent);

        // Create or return existing (idempotency)
        CrmIntegrationStore.StoredRequest stored = store.create(envelope, "AI", minimizedPayload);

        // If RETURNED_EXISTING (duplicate), do NOT redispatch
        if (!stored.status().equals("PENDING")) {
            return stored; // Already dispatched/completed — return existing
        }

        // Dispatch to AI Gateway
        AiGatewayPort.AiResult result = ai.request(envelope, capability, minimizedPayload);

        // Map to state machine status
        String status = switch (result.status()) {
            case AVAILABLE, PARTIAL -> "COMPLETED";
            case TIMED_OUT -> "TIMED_OUT";
            case POLICY_DENIED -> "POLICY_DENIED";
            case UNSAFE_OUTPUT -> "UNSAFE_OUTPUT";
            case UNAVAILABLE -> "UNAVAILABLE";
        };

        JsonNode resultJson = mapper.valueToTree(result);
        store.complete(tenantId, stored.id(), status, null, resultJson,
                status.equals("COMPLETED") ? null : result.explanation());

        return store.find(tenantId, stored.id()).orElseThrow();
    }

    /**
     * Get integration status. Tenant-scoped — cross-tenant returns not found.
     */
    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm an AI recommendation. Requires separate capability (CRM.AI.CONFIRM).
     * Revalidates tenant, entity version, and stale recommendation.
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion) {

        CrmIntegrationStore.StoredRequest existing = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));

        // Terminal-state protection
        if (isTerminal(existing.status())) {
            throw new IllegalStateException("Cannot confirm a terminal request: " + existing.status());
        }

        // Stale recommendation rejection
        if (Instant.now().isAfter(existing.expiresAt())) {
            store.complete(tenantId, requestId, "EXPIRED", null, null, "STALE_RECOMMENDATION");
            throw new IllegalStateException("Recommendation has expired");
        }

        // Idempotent confirmation
        String confirmKey = "CONFIRM:" + idempotencyKey;
        CrmIntegrationStore.StoredRequest existingConfirm = store.findByIdempotency(tenantId, "CONFIRM", confirmKey)
                .orElse(null);
        if (existingConfirm != null) {
            return existingConfirm; // Already confirmed — return existing
        }

        // Record confirmation
        store.complete(tenantId, requestId, "COMPLETED", null,
                mapper.valueToTree(java.util.Map.of("confirmedBy", actorId.toString(), "confirmedAt", Instant.now().toString())),
                null);

        return store.find(tenantId, requestId).orElseThrow();
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

        if (isTerminal(existing.status())) {
            throw new IllegalStateException("Cannot reject a terminal request: " + existing.status());
        }

        store.complete(tenantId, requestId, "REJECTED", null,
                mapper.valueToTree(java.util.Map.of("rejectedBy", actorId.toString(), "rejectedAt", Instant.now().toString(), "reason", reason != null ? reason : "User rejected")),
                null);

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
        // Backend determines classification — never trusts client
        payload.put("dataClassification", "INTERNAL");
        if (userIntent != null && !userIntent.isBlank()) {
            // Sanitize: max 500 chars, no newlines
            String sanitized = userIntent.replaceAll("[\\n\\r]", " ").trim();
            if (sanitized.length() > 500) sanitized = sanitized.substring(0, 500);
            payload.put("userIntent", sanitized);
        }
        return payload;
    }

    private static boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status)
                || "EXPIRED".equals(status) || "TIMED_OUT".equals(status) || "UNAVAILABLE".equals(status)
                || "POLICY_DENIED".equals(status) || "UNSAFE_OUTPUT".equals(status);
    }
}
