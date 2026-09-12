package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * R1 GATES R1.16/R1.17/R1.18 — ExternalActionRequest foundation.
 *
 * <p>Separate from WorkflowWorkItem: internal work items remain
 * employee/security-domain work; an external action is a BOUNDED request to a
 * non-SNAD participant referenced from an authoritative source module —
 * never a fake User, never a fake Employee.</p>
 *
 * <p>Security foundation (R2 adds delivery/portal): opaque non-guessable
 * token (256-bit SecureRandom) returned exactly once and stored ONLY as a
 * SHA-256 hash; participant/tenant/instance/step binding; configurable expiry;
 * revocation; idempotent creation and idempotent single-shot response;
 * explicit lifecycle (PENDING→VIEWED→RESPONDED, or EXPIRED/REVOKED/CANCELLED).
 * EXPIRED is never treated as APPROVED/COMPLETED. No real WhatsApp/email is
 * sent in R1.</p>
 */
@Service
public class WorkflowExternalActionService {

    public static class ExternalActionStateException extends IllegalStateException {
        public ExternalActionStateException(String code, String detail) {
            super(code + ": " + detail);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowExternalActionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ===== participants =====

    public record Participant(UUID id, UUID tenantId, String participantType, String sourceModule,
                              String sourceEntityType, UUID sourceEntityId, String displayReference,
                              String communicationReference) {}

    @Transactional
    public Participant registerParticipant(UUID tenantId, String participantType, String sourceModule,
                                           String sourceEntityType, UUID sourceEntityId,
                                           String displayReference, String communicationReference) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (participantType == null || sourceModule == null || sourceModule.isBlank()
                || sourceEntityType == null || sourceEntityType.isBlank() || sourceEntityId == null) {
            // EXTERNAL_PARTICIPANT_SOURCE_REFERENCE_REQUIRED
            throw new IllegalArgumentException(
                    "EXTERNAL_PARTICIPANT_SOURCE_REFERENCE_REQUIRED: participantType, sourceModule, "
                            + "sourceEntityType and sourceEntityId are all required");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_external_participants (id, tenant_id, participant_type,
                    source_module, source_entity_type, source_entity_id, display_reference,
                    communication_reference, created_at)
                VALUES (?,?,?,?,?,?,?,?,NOW())
                """, id, tenantId, participantType.trim().toUpperCase(), sourceModule.trim().toUpperCase(),
                sourceEntityType, sourceEntityId, displayReference, communicationReference);
        return new Participant(id, tenantId, participantType.trim().toUpperCase(),
                sourceModule.trim().toUpperCase(), sourceEntityType, sourceEntityId,
                displayReference, communicationReference);
    }

    @Transactional(readOnly = true)
    public Optional<Participant> findParticipant(UUID tenantId, UUID participantId) {
        return jdbc.query("""
                SELECT id, tenant_id, participant_type, source_module, source_entity_type,
                       source_entity_id, display_reference, communication_reference
                  FROM workflow_external_participants WHERE tenant_id = ? AND id = ?
                """, rs -> {
            if (!rs.next()) return Optional.<Participant>empty();
            return Optional.of(new Participant(rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class), rs.getString("participant_type"),
                    rs.getString("source_module"), rs.getString("source_entity_type"),
                    rs.getObject("source_entity_id", UUID.class), rs.getString("display_reference"),
                    rs.getString("communication_reference")));
        }, tenantId, participantId);
    }

    // ===== external actions =====

    public record ExternalAction(UUID id, UUID tenantId, UUID workflowInstanceId,
                                 UUID workflowStepInstanceId, UUID participantId, String actionType,
                                 String status, Instant tokenExpiresAt, Instant respondedAt) {}

    public record CreatedExternalAction(ExternalAction action, String opaqueToken) {}

    @Transactional
    public CreatedExternalAction create(UUID tenantId, UUID workflowInstanceId,
                                        UUID workflowStepInstanceId, UUID participantId,
                                        String actionType, Instant expiresAt, String idempotencyKey,
                                        Map<String, Object> allowedActions) {
        if (tenantId == null || workflowInstanceId == null || participantId == null
                || actionType == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantId, workflowInstanceId, participantId, actionType and idempotencyKey are required");
        }
        findParticipant(tenantId, participantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WRONG_PARTICIPANT_DENIED: participant not found in tenant"));
        String opaque = newToken();
        String hash = sha256Hex(opaque);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO workflow_external_actions (id, tenant_id, workflow_instance_id,
                    workflow_step_instance_id, participant_id, action_type, status, token_hash,
                    token_expires_at, allowed_actions, idempotency_key, created_at, updated_at)
                SELECT ?,?,?,?,?,?,'PENDING',?,?,?::jsonb,?,NOW(),NOW()
                """, id, tenantId, workflowInstanceId, workflowStepInstanceId, participantId,
                actionType.trim().toUpperCase(), hash, expiresAt == null ? null : Timestamp.from(expiresAt),
                toJson(allowedActions), idempotencyKey);
        if (inserted == 0) throw new IllegalStateException("external action insert failed");
        return new CreatedExternalAction(toAction(id, tenantId), opaque);
    }

    /** Opaque-token validation (R1.18): hash lookup binds request→action. */
    @Transactional(readOnly = true)
    public Optional<ExternalAction> findByToken(String opaqueToken) {
        if (opaqueToken == null || opaqueToken.isBlank()) return Optional.empty();
        String hash = sha256Hex(opaqueToken);
        UUID id = jdbc.query("""
                SELECT id, tenant_id FROM workflow_external_actions WHERE token_hash = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, hash);
        if (id == null) return Optional.empty();
        return jdbc.query("""
                SELECT id, tenant_id, workflow_instance_id, workflow_step_instance_id, participant_id,
                       action_type, status, token_expires_at, responded_at
                  FROM workflow_external_actions WHERE id = ?
                """, rs -> {
            if (!rs.next()) return Optional.<ExternalAction>empty();
            return Optional.of(new ExternalAction(rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class), rs.getObject("workflow_instance_id", UUID.class),
                    rs.getObject("workflow_step_instance_id", UUID.class),
                    rs.getObject("participant_id", UUID.class), rs.getString("action_type"),
                    rs.getString("status"), toInstant(rs.getTimestamp("token_expires_at")),
                    toInstant(rs.getTimestamp("responded_at"))));
        }, id);
    }

    @Transactional
    public ExternalAction markViewed(UUID tenantId, UUID actionId) {
        return transition(tenantId, actionId, "VIEWED", Map.of());
    }

    /**
     * Single-shot response (EXTERNAL_RESPONSE_ADVANCES_GRAPH_ONCE): a replay
     * returns the already-RESPONDED action without re-advancing anything.
     */
    @Transactional
    public ExternalAction respond(UUID tenantId, UUID actionId, UUID participantId,
                                  Map<String, Object> responsePayload) {
        ExternalAction action = load(tenantId, actionId)
                .orElseThrow(() -> new ExternalActionStateException("EXTERNAL_ACTION_NOT_FOUND", actionId.toString()));
        if ("RESPONDED".equals(action.status())) {
            return action; // idempotent replay — no second advance
        }
        if (!action.participantId().equals(participantId)) {
            throw new ExternalActionStateException("WRONG_PARTICIPANT_DENIED", actionId.toString());
        }
        if ("EXPIRED".equals(action.status())) {
            throw new ExternalActionStateException("EXPIRED_RESPONSE_DENIED", actionId.toString());
        }
        if ("REVOKED".equals(action.status()) || "CANCELLED".equals(action.status())) {
            throw new ExternalActionStateException("REVOKED_RESPONSE_DENIED", actionId.toString());
        }
        if (action.tokenExpiresAt() != null && action.tokenExpiresAt().isBefore(Instant.now())) {
            jdbc.update("UPDATE workflow_external_actions SET status='EXPIRED', updated_at=NOW() "
                    + "WHERE tenant_id=? AND id=?", tenantId, actionId);
            throw new ExternalActionStateException("EXPIRED_RESPONSE_DENIED", actionId.toString());
        }
        jdbc.update("""
                UPDATE workflow_external_actions
                   SET status='RESPONDED', response_payload=?::jsonb, responded_at=NOW(), updated_at=NOW()
                 WHERE tenant_id=? AND id=? AND status IN ('PENDING','VIEWED')
                """, toJson(responsePayload), tenantId, actionId);
        return load(tenantId, actionId).orElseThrow();
    }

    @Transactional
    public ExternalAction transition(UUID tenantId, UUID actionId, String targetStatus,
                                     Map<String, Object> payload) {
        ExternalAction action = load(tenantId, actionId)
                .orElseThrow(() -> new ExternalActionStateException("EXTERNAL_ACTION_NOT_FOUND", actionId.toString()));
        String to = targetStatus.trim().toUpperCase();
        if ("RESPONDED".equals(action.status())) {
            return action; // terminal — idempotent
        }
        if ("EXPIRED".equals(to) && !"PENDING".equals(action.status()) && !"VIEWED".equals(action.status())) {
            return action;
        }
        if ("REVOKED".equals(to) && !"PENDING".equals(action.status()) && !"VIEWED".equals(action.status())) {
            throw new ExternalActionStateException("REVOKED_RESPONSE_DENIED", actionId.toString());
        }
        jdbc.update("""
                UPDATE workflow_external_actions SET status=?::varchar, updated_at=NOW()
                 WHERE tenant_id=? AND id=? AND status IN ('PENDING','VIEWED')
                """, to, tenantId, actionId);
        return load(tenantId, actionId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<ExternalAction> load(UUID tenantId, UUID actionId) {
        return jdbc.query("""
                SELECT id, tenant_id, workflow_instance_id, workflow_step_instance_id, participant_id,
                       action_type, status, token_expires_at, responded_at
                  FROM workflow_external_actions WHERE tenant_id = ? AND id = ?
                """, rs -> {
            if (!rs.next()) return Optional.<ExternalAction>empty();
            return Optional.of(new ExternalAction(rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class), rs.getObject("workflow_instance_id", UUID.class),
                    rs.getObject("workflow_step_instance_id", UUID.class),
                    rs.getObject("participant_id", UUID.class), rs.getString("action_type"),
                    rs.getString("status"), toInstant(rs.getTimestamp("token_expires_at")),
                    toInstant(rs.getTimestamp("responded_at"))));
        }, tenantId, actionId);
    }

    private ExternalAction toAction(UUID id, UUID tenantId) {
        return load(tenantId, id).orElseThrow();
    }

    private static Instant toInstant(Timestamp t) { return t == null ? null : t.toInstant(); }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { throw new IllegalArgumentException("invalid json payload", e); }
    }
}
