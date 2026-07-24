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
 * Uses transactional outbox pattern:
 *   1. Persist request + outbox event in one transaction, commit, return HTTP 202
 *   2. Worker claims outbox event, calls external service, stores result
 *
 * NO external service calls happen inside requestAiInsight() transaction.
 */
@Service
public class CrmIntegrationUseCases {

    private final CrmIntegrationStore store;
    private final ObjectMapper mapper;

    public CrmIntegrationUseCases(CrmIntegrationStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    /**
     * Request an AI insight. Creates request + outbox event atomically.
     * Does NOT call AI Gateway — that happens in the outbox worker.
     * Returns HTTP 202 with status=PENDING.
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

        // Persist request + outbox event atomically — NO external call here
        CrmIntegrationStore.CreateResult createResult = store.create(envelope, "AI", minimizedPayload);

        // RETURNED_EXISTING: never redispatch
        if (createResult.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return createResult.request();
        }

        // Create outbox event atomically with request
        store.createOutboxEvent(tenantId, createResult.request().id(), "AI",
                idempotencyKey, minimizedPayload);

        // Return PENDING — worker will dispatch asynchronously
        return createResult.request();
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm an AI recommendation with decision idempotency.
     * AI result_payload is NOT modified — decision stored separately.
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

        // Create decision record (idempotent) — fingerprint WITHOUT correlationId
        String fingerprint = computeFingerprint(requestId, "CONFIRM", expectedEntityVersion);
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
        // Uses status-only transition (no result_payload modification)
        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "CONFIRMED",
                null, null, null);

        if (!tr.success()) {
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IllegalStateException("State transition conflict — concurrent confirm/reject");
        }

        // Mark decision as confirmed (non-terminal — completed_at stays NULL)
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

        // Fingerprint includes normalized reason (NOT correlationId)
        String normalizedReason = reason != null ? reason.trim() : "User rejected";
        String fingerprint = computeFingerprint(requestId, "REJECT",
                existing.sourceEntityVersion(), normalizedReason);
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

        // Status-only transition — does NOT modify result_payload
        CrmIntegrationStore.TransitionResult tr = store.transition(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "REJECTED",
                null, null, null);

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

    /**
     * Stable fingerprint WITHOUT correlationId (correlationId changes between retries).
     * For REJECT, includes normalized reason.
     */
    private String computeFingerprint(UUID requestId, String decision, long entityVersion) {
        return computeFingerprint(requestId, decision, entityVersion, null);
    }

    private String computeFingerprint(UUID requestId, String decision, long entityVersion, String normalizedReason) {
        try {
            String input = requestId + "|" + decision + "|" + entityVersion;
            if (normalizedReason != null) input += "|" + normalizedReason;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute fingerprint", e);
        }
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
