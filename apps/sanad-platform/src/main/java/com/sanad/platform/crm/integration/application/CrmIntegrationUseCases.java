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
 * Uses transactional outbox: request + outbox created atomically, no external call.
 * Decision idempotency: checks decision BEFORE validating request state.
 * AI result immutability: confirm/reject use transitionStatus (no result_payload modification).
 * Entity validation: moved to application layer, not controller.
 */
@Service
public class CrmIntegrationUseCases {

    private final CrmIntegrationStore store;
    private final CrmEntitySnapshotPort entitySnapshotPort;
    private final ObjectMapper mapper;

    public CrmIntegrationUseCases(CrmIntegrationStore store,
                                  CrmEntitySnapshotPort entitySnapshotPort,
                                  ObjectMapper mapper) {
        this.store = store;
        this.entitySnapshotPort = entitySnapshotPort;
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

        CrmIntegrationStore.CreateResult createResult = store.create(envelope, "AI", minimizedPayload);

        if (createResult.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return createResult.request();
        }

        store.createOutboxEvent(tenantId, createResult.request().id(), "AI",
                idempotencyKey, minimizedPayload);

        return createResult.request();
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm recommendation. Decision is checked BEFORE state validation.
     * Entity validation uses request's stored entity, NOT client-supplied.
     * Uses transitionStatus (preserves AI result_payload).
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion) {

        // Step 1: Check decision BEFORE validating request state
        String fingerprint = computeFingerprint(requestId, "CONFIRM", expectedEntityVersion);
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, actorId, "CONFIRM", idempotencyKey,
                fingerprint, expectedEntityVersion, correlationId);

        if (!dr.created()) {
            // Existing decision — return based on its status (works after any terminal state)
            return handleExistingDecision(tenantId, requestId, dr.record());
        }

        // Step 2: Validate request state (only for new decisions)
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
            store.transitionStatus(tenantId, requestId, existing.version(),
                    Set.of("RECOMMENDATION_AVAILABLE"), "EXPIRED");
            throw new IllegalStateException("Recommendation has expired");
        }

        // Step 3: Live entity validation using REQUEST's stored entity (not client's)
        validateEntityVersion(tenantId, existing, expectedEntityVersion);

        // Step 4: Status-only transition (preserves AI result_payload)
        CrmIntegrationStore.TransitionResult tr = store.transitionStatus(
                tenantId, requestId, existing.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "CONFIRMED");

        if (!tr.success()) {
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IllegalStateException("State transition conflict — concurrent confirm/reject");
        }

        store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                Set.of("PENDING"), "CONFIRMED", null, null);

        return tr.request();
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest rejectRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, String reason) {

        // Step 1: Check decision BEFORE state validation
        String normalizedReason = reason != null ? reason.trim() : "User rejected";
        CrmIntegrationStore.StoredRequest request = store.find(tenantId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("Integration request not found"));
        String fingerprint = computeFingerprint(requestId, "REJECT",
                request.sourceEntityVersion(), normalizedReason);
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, actorId, "REJECT", idempotencyKey,
                fingerprint, request.sourceEntityVersion(), correlationId);

        if (!dr.created()) {
            return handleExistingDecision(tenantId, requestId, dr.record());
        }

        // Step 2: Validate state
        if (!"AI".equals(request.integrationType())) {
            throw new IllegalStateException("Reject is only available for AI integration requests");
        }
        if (!"RECOMMENDATION_AVAILABLE".equals(request.status())) {
            throw new IllegalStateException(
                    "Cannot reject: current status is " + request.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }

        // Step 3: Status-only transition (preserves AI result_payload)
        CrmIntegrationStore.TransitionResult tr = store.transitionStatus(
                tenantId, requestId, request.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "REJECTED");

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

    private CrmIntegrationStore.StoredRequest handleExistingDecision(
            UUID tenantId, UUID requestId, CrmIntegrationStore.DecisionRecord decision) {
        String status = decision.decisionStatus();
        if ("CONFIRMED".equals(status) || "EXECUTING".equals(status)
                || "EXECUTED".equals(status)) {
            return store.find(tenantId, requestId).orElseThrow();
        }
        if ("REJECTED".equals(status)) {
            throw new IllegalStateException("Recommendation was already rejected");
        }
        if ("EXECUTION_REJECTED".equals(status)) {
            throw new IllegalStateException("Recommendation execution was rejected");
        }
        if ("CONFLICT".equals(status)) {
            throw new CrmIntegrationStore.IdempotencyConflictException(
                    "Previous decision ended in CONFLICT state");
        }
        throw new IllegalStateException("Decision already in progress: " + status);
    }

    private void validateEntityVersion(UUID tenantId, CrmIntegrationStore.StoredRequest request,
                                        long expectedEntityVersion) {
        CrmEntitySnapshotPort.CrmEntitySnapshot snapshot = entitySnapshotPort.load(
                tenantId, request.sourceEntityType(), request.sourceEntityId());

        if (snapshot == null) {
            throw new IllegalStateException("Entity not found: " + request.sourceEntityType()
                    + "/" + request.sourceEntityId());
        }
        if (!snapshot.tenantId().equals(tenantId)) {
            throw new IllegalStateException("Entity tenant mismatch");
        }
        if (snapshot.currentVersion() != request.sourceEntityVersion()) {
            throw new IllegalStateException("STALE_RECOMMENDATION: entity version changed since recommendation");
        }
        if (snapshot.currentVersion() != expectedEntityVersion) {
            throw new IllegalStateException("STALE_RECOMMENDATION: expected version mismatch");
        }
        if (!snapshot.active()) {
            throw new IllegalStateException("Entity is not active");
        }
    }

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
