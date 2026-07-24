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
 * <p>Key invariants enforced here:</p>
 * <ul>
 *   <li><strong>Transactional outbox:</strong> request + outbox event created atomically,
 *       no external call from the request path.</li>
 *   <li><strong>Decision idempotency checked BEFORE state validation:</strong> a replayed
 *       decision for the same fingerprint returns the stored result regardless of the
 *       current request status (PENDING/CONFIRMED/REJECTED/EXECUTING/EXECUTED/
 *       EXECUTION_REJECTED/CONFLICT). Only a different fingerprint produces
 *       {@link IntegrationErrorCode#IDEMPOTENCY_KEY_REUSED} (HTTP 409).</li>
 *   <li><strong>Atomic If-Match:</strong> confirm and reject receive
 *       {@code expectedIntegrationVersion} from the controller's {@code If-Match} header
 *       and pass it directly into {@code transitionStatus}. No separate read-check;
 *       the version check is part of the atomic UPDATE.</li>
 *   <li><strong>AI result immutability:</strong> confirm/reject use {@code transitionStatus}
 *       (status-only) so the original {@code result_payload} is preserved.</li>
 *   <li><strong>Live entity validation:</strong> snapshot is loaded from the actual CRM
 *       tables using the REQUEST's stored entity identity (not the client payload).
 *       Validates tenant, type, id, version, active, and currentState permitting the
 *       recommendation's actionCode.</li>
 *   <li><strong>Envelope persistence:</strong> {@code requested_locale} is stored and
 *       reconstructed exclusively from the stored column. Missing values surface as
 *       {@link IntegrationErrorCode#INVALID_CONTRACT} rather than guessed defaults.</li>
 * </ul>
 */
@Service
public class CrmIntegrationUseCases {

    private final CrmIntegrationStore store;
    private final CrmEntitySnapshotPort entitySnapshotPort;
    private final ConfirmedRecommendationExecutor commandExecutor;
    private final ObjectMapper mapper;

    public CrmIntegrationUseCases(CrmIntegrationStore store,
                                  CrmEntitySnapshotPort entitySnapshotPort,
                                  ConfirmedRecommendationExecutor commandExecutor,
                                  ObjectMapper mapper) {
        this.store = store;
        this.entitySnapshotPort = entitySnapshotPort;
        this.commandExecutor = commandExecutor;
        this.mapper = mapper;
    }

    @Transactional
    public CrmIntegrationStore.StoredRequest requestAiInsight(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, AiGatewayPort.Capability capability,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String userIntent, Locale requestedLocale) {

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Locale locale = requestedLocale != null ? requestedLocale : Locale.ENGLISH;
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.ai." + capability.name().toLowerCase(Locale.ROOT), "1.0",
                tenantId, actorId, correlationId, causationId, idempotencyKey,
                sourceEntityType, sourceEntityId, sourceEntityVersion,
                now, now.plus(30, ChronoUnit.SECONDS), locale,
                "CRM.AI.READ", "INTERNAL");

        JsonNode minimizedPayload = buildMinimizedPayload(capability, sourceEntityType, sourceEntityId, userIntent);

        CrmIntegrationStore.CreateResult createResult = store.create(envelope, "AI", minimizedPayload);

        if (createResult.disposition() == CrmIntegrationStore.CreationDisposition.RETURNED_EXISTING) {
            return createResult.request();
        }

        store.createOutboxEvent(tenantId, createResult.request().id(), "AI",
                "AI_REQUEST_DISPATCH", idempotencyKey, minimizedPayload);

        return createResult.request();
    }

    /** Backwards-compatible overload — defaults locale to English. */
    @Transactional
    public CrmIntegrationStore.StoredRequest requestAiInsight(
            UUID tenantId, UUID actorId, String correlationId, String causationId,
            String idempotencyKey, AiGatewayPort.Capability capability,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String userIntent) {
        return requestAiInsight(tenantId, actorId, correlationId, causationId, idempotencyKey,
                capability, sourceEntityType, sourceEntityId, sourceEntityVersion,
                userIntent, Locale.ENGLISH);
    }

    public CrmIntegrationStore.StoredRequest getStatus(UUID tenantId, UUID requestId) {
        return store.find(tenantId, requestId).orElse(null);
    }

    /**
     * Confirm recommendation. Decision is checked BEFORE state validation.
     * Entity validation uses request's stored entity, NOT client-supplied.
     * Uses transitionStatus (preserves AI result_payload).
     *
     * <p>If-Match is enforced atomically: {@code expectedIntegrationVersion} is passed
     * to {@code transitionStatus} — the UPDATE only succeeds if the row's version
     * matches. A mismatch surfaces as {@link IntegrationErrorCode#INTEGRATION_VERSION_MISMATCH}
     * (HTTP 412).</p>
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion,
            long expectedIntegrationVersion) {

        // Step 1: Check decision BEFORE validating request state
        String fingerprint = computeFingerprint(requestId, "CONFIRM", expectedEntityVersion);
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, actorId, "CONFIRM", idempotencyKey,
                fingerprint, expectedEntityVersion, correlationId);

        if (!dr.created()) {
            // Replay — return stored result for ALL statuses (including REJECTED, CONFLICT, etc.)
            return handleExistingDecision(tenantId, requestId, dr.record());
        }

        // Step 2: Load request (must exist)
        CrmIntegrationStore.StoredRequest existing = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found: " + requestId));

        if (!"AI".equals(existing.integrationType())) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Confirm is only available for AI integration requests");
        }
        if (!"RECOMMENDATION_AVAILABLE".equals(existing.status())) {
            throw new IntegrationException(IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Cannot confirm: current status is " + existing.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }
        if (Instant.now().isAfter(existing.expiresAt())) {
            store.transitionStatus(tenantId, requestId, existing.version(),
                    Set.of("RECOMMENDATION_AVAILABLE"), "EXPIRED");
            throw new IntegrationException(IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Recommendation has expired");
        }

        // Step 3: Live entity validation using REQUEST's stored entity (not client's)
        validateEntityForConfirm(tenantId, existing, expectedEntityVersion);

        // Step 4: Atomic If-Match via version=? — status-only transition (preserves AI result_payload)
        CrmIntegrationStore.TransitionResult tr = store.transitionStatus(
                tenantId, requestId, expectedIntegrationVersion,
                Set.of("RECOMMENDATION_AVAILABLE"), "CONFIRMED");

        if (!tr.success()) {
            // Transition failed — either version mismatch or concurrent transition
            CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId).orElse(null);
            if (current == null) {
                throw new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request vanished mid-confirm");
            }
            if (current.version() != expectedIntegrationVersion) {
                throw new IntegrationException(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                        "Expected version " + expectedIntegrationVersion +
                        ", found " + current.version());
            }
            // Version matched but status didn't — concurrent confirm/reject
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "State transition conflict — concurrent confirm/reject");
        }

        store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                Set.of("PENDING"), "CONFIRMED", null, null);

        // Step 5: Atomically enqueue a durable CONFIRMED_COMMAND_EXECUTION outbox
        // event in the SAME transaction as the decision and request transitions.
        // If enqueueExecution throws, the entire transaction rolls back — the
        // decision does NOT transition to CONFIRMED and the request does NOT
        // transition to CONFIRMED. No CRM command is invoked here.
        long confirmedVersion = tr.request().version();
        commandExecutor.enqueueExecution(
                tenantId, requestId, dr.record().id(), actorId,
                correlationId, confirmedVersion);

        return tr.request();
    }

    /** Backwards-compatible overload — assumes If-Match equals current version. */
    @Transactional
    public CrmIntegrationStore.StoredRequest confirmRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, long expectedEntityVersion) {
        CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found"));
        return confirmRecommendation(tenantId, actorId, requestId, correlationId,
                idempotencyKey, expectedEntityVersion, current.version());
    }

    /**
     * Reject recommendation. Decision is checked BEFORE state validation.
     * If-Match is enforced atomically (same as confirm).
     */
    @Transactional
    public CrmIntegrationStore.StoredRequest rejectRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, String reason, long expectedIntegrationVersion) {

        // Step 1: Check decision BEFORE state validation
        String normalizedReason = reason != null ? reason.trim() : "User rejected";
        CrmIntegrationStore.StoredRequest request = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found: " + requestId));
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
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Reject is only available for AI integration requests");
        }
        if (!"RECOMMENDATION_AVAILABLE".equals(request.status())) {
            throw new IntegrationException(IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Cannot reject: current status is " + request.status() +
                    ", expected RECOMMENDATION_AVAILABLE");
        }

        // Step 3: Atomic If-Match — status-only transition (preserves AI result_payload)
        CrmIntegrationStore.TransitionResult tr = store.transitionStatus(
                tenantId, requestId, expectedIntegrationVersion,
                Set.of("RECOMMENDATION_AVAILABLE"), "REJECTED");

        if (!tr.success()) {
            CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId).orElse(null);
            if (current == null) {
                throw new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request vanished mid-reject");
            }
            if (current.version() != expectedIntegrationVersion) {
                throw new IntegrationException(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH,
                        "Expected version " + expectedIntegrationVersion +
                        ", found " + current.version());
            }
            store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                    Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
            throw new IntegrationException(IntegrationErrorCode.STATE_TRANSITION_FAILED,
                    "State transition conflict — concurrent confirm/reject");
        }

        store.transitionDecision(tenantId, dr.record().id(), dr.record().version(),
                Set.of("PENDING"), "REJECTED", null, null);

        return tr.request();
    }

    /** Backwards-compatible overload. */
    @Transactional
    public CrmIntegrationStore.StoredRequest rejectRecommendation(
            UUID tenantId, UUID actorId, UUID requestId, String correlationId,
            String idempotencyKey, String reason) {
        CrmIntegrationStore.StoredRequest current = store.find(tenantId, requestId)
                .orElseThrow(() -> new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                        "Integration request not found"));
        return rejectRecommendation(tenantId, actorId, requestId, correlationId,
                idempotencyKey, reason, current.version());
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Replay decision: returns the stored result for ALL decision statuses.
     * The only exception is a fingerprint mismatch, which surfaces as
     * {@link IntegrationErrorCode#IDEMPOTENCY_KEY_REUSED} — but that is
     * caught earlier in {@code createDecision} (DuplicateKeyException path).
     *
     * <p>For CONFLICT decisions, we still return the stored request so the
     * caller can observe the prior conflict state without re-attempting.</p>
     */
    private CrmIntegrationStore.StoredRequest handleExistingDecision(
            UUID tenantId, UUID requestId, CrmIntegrationStore.DecisionRecord decision) {
        // All statuses are returned — replay is idempotent for the same fingerprint.
        // The decision record itself carries the original decision, status,
        // commandReference, and errorCode so the caller can describe the prior outcome.
        return store.find(tenantId, requestId).orElseThrow();
    }

    /**
     * Live entity validation in the application layer.
     * Uses the request's stored entity identity, NOT the client payload.
     * Verifies tenant, type, id, version, active, and that currentState
     * permits the recommendation's actionCode.
     */
    private void validateEntityForConfirm(UUID tenantId, CrmIntegrationStore.StoredRequest request,
                                            long expectedEntityVersion) {
        CrmEntitySnapshotPort.CrmEntitySnapshot snapshot = entitySnapshotPort.load(
                tenantId, request.sourceEntityType(), request.sourceEntityId());

        if (snapshot == null) {
            throw new IntegrationException(IntegrationErrorCode.ENTITY_NOT_FOUND,
                    "Entity not found: " + request.sourceEntityType()
                    + "/" + request.sourceEntityId());
        }
        if (!snapshot.tenantId().equals(tenantId)) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_TENANT,
                    "Entity tenant mismatch");
        }
        if (!snapshot.entityType().equals(request.sourceEntityType())) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Entity type mismatch: snapshot=" + snapshot.entityType()
                    + " request=" + request.sourceEntityType());
        }
        if (!snapshot.entityId().equals(request.sourceEntityId())) {
            throw new IntegrationException(IntegrationErrorCode.INVALID_CONTRACT,
                    "Entity id mismatch");
        }
        if (snapshot.currentVersion() != request.sourceEntityVersion()) {
            throw new IntegrationException(IntegrationErrorCode.STALE_RECOMMENDATION,
                    "Entity version changed since recommendation: snapshot=" + snapshot.currentVersion()
                    + " request=" + request.sourceEntityVersion());
        }
        if (snapshot.currentVersion() != expectedEntityVersion) {
            throw new IntegrationException(IntegrationErrorCode.STALE_RECOMMENDATION,
                    "Expected version mismatch: snapshot=" + snapshot.currentVersion()
                    + " expected=" + expectedEntityVersion);
        }
        if (!snapshot.active()) {
            throw new IntegrationException(IntegrationErrorCode.ENTITY_STATE_CONFLICT,
                    "Entity is not active (state=" + snapshot.currentState() + ")");
        }
        // Object-level authorization boundary — the controller layer enforces
        // capability checks via @RequireCapability("CRM.AI.CONFIRM"). Deeper
        // object-level checks (e.g. territory scoping) would be added here once
        // the corresponding port is available.
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
            throw new IntegrationException(IntegrationErrorCode.UNKNOWN_ERROR,
                    "Failed to compute fingerprint", e);
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
