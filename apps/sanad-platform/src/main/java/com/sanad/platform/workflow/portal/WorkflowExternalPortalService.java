package com.sanad.platform.workflow.portal;

import com.sanad.platform.workflow.application.WorkflowExternalActionService;
import com.sanad.platform.workflow.application.WorkflowJourneyService;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Secure External Action Portal service (GATES R2.11/R2.12 / AD-8).
 *
 * <p>The opaque one-time token (R1 model: 32-byte SecureRandom, SHA-256
 * stored, returned exactly once) IS the capability for the bounded portal
 * surface. Every request re-reads the authoritative ExternalActionRequest
 * and validates: tenant binding, participant binding, status, expiry,
 * revocation, requested action, single-use idempotency. Rate limits are
 * enforced from the durable access log. OTP challenges are short-lived,
 * attempt-limited, participant-bound, and never stored or logged in
 * plaintext.</p>
 *
 * <p>Response semantics: response evidence is recorded and journaled
 * (EXTERNAL_RESPONSE); PORTAL_RESPONSE_ADVANCES_GRAPH=NO (AD-8) — the
 * internal governed path remains responsible for graph advancement, and
 * external participants are never converted into User/Employee.</p>
 */
@Service
public class WorkflowExternalPortalService {

    /** Portal-facing failure carrying a stable machine-readable code. */
    public static class PortalDeniedException extends IllegalStateException {
        public PortalDeniedException(String code) {
            super(code);
        }
    }

    private static final int MAX_TOKEN_ACCESS_PER_HOUR = 10;
    private static final int MAX_IP_ACCESS_PER_HOUR = 120;
    private static final int OTP_TTL_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WorkflowExternalActionService actionService;
    private final WorkflowJourneyService journeyService;
    private final TenantRlsTransactionContext tenantContext;
    private final JdbcTemplate jdbc;
    private final boolean otpEnabled;

    public WorkflowExternalPortalService(
            WorkflowExternalActionService actionService,
            WorkflowJourneyService journeyService,
            TenantRlsTransactionContext tenantContext,
            JdbcTemplate jdbc,
            @Value("${sanad.workflow.portal.otp-enabled:false}") boolean otpEnabled) {
        this.actionService = actionService;
        this.journeyService = journeyService;
        this.tenantContext = tenantContext;
        this.jdbc = jdbc;
        this.otpEnabled = otpEnabled;
    }

    public record PortalView(UUID actionId, String actionType, String status,
                             Instant tokenExpiresAt, Instant respondedAt,
                             String participantType, String displayReference,
                             boolean otpRequired,
                             Map<String, Object> responsePayload) {
    }

    public record PortalResponse(UUID actionId, String status,
                                 Instant respondedAt,
                                 Map<String, Object> responsePayload) {
    }

    /** Token-scoped view; marks the action VIEWED on first sight. */
    @Transactional
    public PortalView view(String opaqueToken, String remoteFingerprint) {
        WorkflowExternalActionService.ExternalAction action =
                resolveAction(opaqueToken, remoteFingerprint, "VIEW");
        UUID tenantId = action.tenantId();
        tenantContext.applyForCurrentTransaction(tenantId);
        checkExpiry(action);
        if ("PENDING".equals(action.status())) {
            action = actionService.markViewed(tenantId, action.id());
        }
        logAccess(tenantId, action.id(), "VIEW", "ALLOWED", remoteFingerprint, null);
        Map<String, Object> participant = participantInfo(tenantId, action.participantId());
        Map<String, Object> response = existingResponse(tenantId, action.id());
        boolean otpRequired = isOtpRequired(tenantId, action.id());
        return new PortalView(action.id(), action.actionType(), action.status(),
                action.tokenExpiresAt(), action.respondedAt(),
                (String) participant.get("participant_type"),
                (String) participant.get("display_reference"),
                otpRequired, response);
    }

    /**
     * Single-shot token-scoped response (GATE R2.12): re-reads the
     * authoritative action, validates everything server-side, records the
     * response idempotently, and appends Journey evidence.
     */
    @Transactional
    public PortalResponse respond(String opaqueToken, String requestedAction,
                                  Map<String, Object> responsePayload,
                                  String remoteFingerprint, String otp) {
        WorkflowExternalActionService.ExternalAction action =
                resolveAction(opaqueToken, remoteFingerprint, "RESPOND");
        UUID tenantId = action.tenantId();
        tenantContext.applyForCurrentTransaction(tenantId);
        checkExpiry(action);
        if ("RESPONDED".equals(action.status())) {
            // Idempotent replay of a valid response — no second advance.
            Map<String, Object> prior = existingResponse(tenantId, action.id());
            return new PortalResponse(action.id(), action.status(),
                    action.respondedAt(), prior);
        }
        String requested = requestedAction == null ? ""
                : requestedAction.trim().toUpperCase();
        if (!allowedActions(tenantId, action.id()).contains(requested)
                && !requested.equals(action.actionType())) {
            logAccess(tenantId, action.id(), "RESPOND", "DENIED",
                    remoteFingerprint, "ACTION_NOT_ALLOWED");
            throw new PortalDeniedException("ACTION_NOT_ALLOWED");
        }
        if (isOtpRequired(tenantId, action.id())) {
            verifyOtpForTenantContext(tenantId, action.id(), action.participantId(), otp);
        }
        WorkflowExternalActionService.ExternalAction responded =
                actionService.respond(tenantId, action.id(), action.participantId(),
                        responsePayload == null ? Map.of() : responsePayload);
        logAccess(tenantId, action.id(), "RESPOND", "ALLOWED", remoteFingerprint, null);
        journeyService.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                "EXTERNAL_RESPONSE", action.workflowInstanceId(), null, null,
                action.id(), null, null, null, null, null, null, null,
                "EXTERNAL", null, null, action.participantId(),
                action.status(), responded.status(),
                "External action response recorded: " + requested,
                action.id(), action.id(),
                "external-response:" + action.id(),
                Map.of("actionType", requested)));
        return new PortalResponse(responded.id(), responded.status(),
                responded.respondedAt(), existingResponse(tenantId, action.id()));
    }

    /**
     * Issues an OTP challenge for the action (when otp policy requires).
     * The plaintext code is returned exactly once to be communicated over
     * the out-of-band channel; only its SHA-256 hash is stored.
     */
    @Transactional
    public String issueOtp(String opaqueToken, String remoteFingerprint) {
        WorkflowExternalActionService.ExternalAction action =
                resolveAction(opaqueToken, remoteFingerprint, "OTP_ISSUE");
        UUID tenantId = action.tenantId();
        tenantContext.applyForCurrentTransaction(tenantId);
        if (!otpEnabled) {
            throw new PortalDeniedException("OTP_DISABLED");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("""
                INSERT INTO workflow_portal_otp_challenges (
                    id, tenant_id, external_action_id, participant_id, otp_hash,
                    expires_at, attempt_count, max_attempts, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 5, NOW())
                """, UUID.randomUUID(), tenantId, action.id(), action.participantId(),
                sha256Hex(code),
                Timestamp.from(Instant.now().plusSeconds(OTP_TTL_SECONDS)));
        logAccess(tenantId, action.id(), "OTP_ISSUE", "ALLOWED", remoteFingerprint, null);
        return code;
    }

    private void verifyOtpForTenantContext(UUID tenantId, UUID actionId,
                                           UUID participantId, String otp) {
        if (!otpEnabled) {
            throw new PortalDeniedException("OTP_DISABLED");
        }
        if (otp == null || otp.isBlank()) {
            throw new PortalDeniedException("OTP_REQUIRED");
        }
        List<Map<String, Object>> challenges = jdbc.queryForList("""
                SELECT id, otp_hash, expires_at, attempt_count, max_attempts, consumed_at
                  FROM workflow_portal_otp_challenges
                 WHERE tenant_id = ? AND external_action_id = ? AND participant_id = ?
                 ORDER BY created_at DESC LIMIT 1
                """, tenantId, actionId, participantId);
        if (challenges.isEmpty()) {
            throw new PortalDeniedException("OTP_REQUIRED");
        }
        Map<String, Object> challenge = challenges.get(0);
        if (challenge.get("consumed_at") != null) {
            throw new PortalDeniedException("OTP_REQUIRED");
        }
        int attempts = ((Number) challenge.get("attempt_count")).intValue();
        int maxAttempts = ((Number) challenge.get("max_attempts")).intValue();
        Timestamp expiresAt = (Timestamp) challenge.get("expires_at");
        if (attempts >= maxAttempts
                || expiresAt.toInstant().isBefore(Instant.now())) {
            throw new PortalDeniedException("OTP_EXPIRED");
        }
        boolean matches = sha256Hex(otp.trim())
                .equalsIgnoreCase(String.valueOf(challenge.get("otp_hash")));
        jdbc.update("""
                UPDATE workflow_portal_otp_challenges
                   SET attempt_count = attempt_count + 1,
                       consumed_at = CASE WHEN ? THEN NOW() ELSE consumed_at END
                 WHERE tenant_id = ? AND id = ?
                """, matches, tenantId, challenge.get("id"));
        if (!matches) {
            throw new PortalDeniedException("OTP_INVALID");
        }
    }

    // ===== internals =====

    private WorkflowExternalActionService.ExternalAction resolveAction(
            String opaqueToken, String remoteFingerprint, String accessType) {
        if (opaqueToken == null || opaqueToken.isBlank()) {
            throw new PortalDeniedException("INVALID_TOKEN");
        }
        WorkflowExternalActionService.ExternalAction action =
                actionService.findByToken(opaqueToken)
                        .orElseThrow(() -> new PortalDeniedException("INVALID_TOKEN"));
        // Rate limits BEFORE any state effect (fail-closed, durable counters).
        Long tokenHits = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workflow_portal_access_log
                 WHERE external_action_id = ? AND created_at > NOW() - INTERVAL '1 hour'
                """, Long.class, action.id());
        if (tokenHits != null && tokenHits >= MAX_TOKEN_ACCESS_PER_HOUR) {
            logAccess(action.tenantId(), action.id(), accessType, "RATE_LIMITED",
                    remoteFingerprint, "TOKEN_LIMIT");
            throw new PortalDeniedException("RATE_LIMITED");
        }
        if (remoteFingerprint != null && !remoteFingerprint.isBlank()) {
            Long ipHits = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM workflow_portal_access_log
                     WHERE remote_fingerprint = ? AND created_at > NOW() - INTERVAL '1 hour'
                    """, Long.class, remoteFingerprint);
            if (ipHits != null && ipHits >= MAX_IP_ACCESS_PER_HOUR) {
                logAccess(action.tenantId(), action.id(), accessType, "RATE_LIMITED",
                        remoteFingerprint, "IP_LIMIT");
                throw new PortalDeniedException("RATE_LIMITED");
            }
        }
        if ("REVOKED".equals(action.status()) || "CANCELLED".equals(action.status())) {
            logAccess(action.tenantId(), action.id(), accessType, "REVOKED",
                    remoteFingerprint, action.status());
            throw new PortalDeniedException("REVOKED_TOKEN");
        }
        if ("EXPIRED".equals(action.status())) {
            logAccess(action.tenantId(), action.id(), accessType, "EXPIRED",
                    remoteFingerprint, null);
            throw new PortalDeniedException("EXPIRED_TOKEN");
        }
        return action;
    }

    private void checkExpiry(WorkflowExternalActionService.ExternalAction action) {
        if (action.tokenExpiresAt() != null
                && action.tokenExpiresAt().isBefore(Instant.now())) {
            actionService.transition(action.tenantId(), action.id(), "EXPIRED", Map.of());
            throw new PortalDeniedException("EXPIRED_TOKEN");
        }
    }

    private void logAccess(UUID tenantId, UUID actionId, String accessType,
                           String outcome, String fingerprint, String detail) {
        jdbc.update("""
                INSERT INTO workflow_portal_access_log (
                    tenant_id, external_action_id, access_type, outcome,
                    remote_fingerprint, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, tenantId, actionId, accessType, outcome,
                fingerprint == null ? null
                        : WorkflowExternalPortalService.sha256Hex(fingerprint),
                detail);
    }

    private Map<String, Object> participantInfo(UUID tenantId, UUID participantId) {
        return jdbc.queryForMap("""
                SELECT participant_type, display_reference
                  FROM workflow_external_participants
                 WHERE tenant_id = ? AND id = ?
                """, tenantId, participantId);
    }

    private Map<String, Object> existingResponse(UUID tenantId, UUID actionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT response_payload::text AS payload_text
                  FROM workflow_external_actions
                 WHERE tenant_id = ? AND id = ? AND status = 'RESPONDED'
                """, tenantId, actionId);
        if (rows.isEmpty() || rows.get(0).get("payload_text") == null) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue((String) rows.get(0).get("payload_text"), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean isOtpRequired(UUID tenantId, UUID actionId) {
        Boolean required = jdbc.queryForObject("""
                SELECT otp_required FROM workflow_external_actions
                 WHERE tenant_id = ? AND id = ?
                """, Boolean.class, tenantId, actionId);
        return Boolean.TRUE.equals(required);
    }

    private List<String> allowedActions(UUID tenantId, UUID actionId) {
        List<String> raw = jdbc.queryForList("""
                SELECT COALESCE(allowed_actions::text, '[]')
                  FROM workflow_external_actions
                 WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, actionId);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw.isEmpty() ? "[]" : raw.get(0),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String sha256Hex(String value) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
