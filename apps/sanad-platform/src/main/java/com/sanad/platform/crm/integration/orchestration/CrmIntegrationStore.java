package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tenant-scoped durable request/result store with database-enforced idempotency.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>{@link #transitionStatus} — status-only transition, preserves result_payload</li>
 *   <li>{@link #transitionWithResult} — writes result_payload exactly once</li>
 *   <li>{@link #claimNextOutboxEvent} — atomic CTE-based claim with claim_token</li>
 *   <li>{@link #completeOutboxEvent} / {@link #failOutboxEvent} — claim ownership verified</li>
 *   <li>{@link #createDecision} / {@link #findDecision} / {@link #findDecisionById}</li>
 * </ul>
 */
@Component
public class CrmIntegrationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "EXECUTED", "EXECUTION_REJECTED", "REJECTED",
            "POLICY_DENIED", "UNSAFE_OUTPUT", "TIMED_OUT",
            "UNAVAILABLE", "CANCELLED", "EXPIRED");

    public CrmIntegrationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record CreateResult(StoredRequest request, CreationDisposition disposition) {}
    public enum CreationDisposition { CREATED_NEW, RETURNED_EXISTING }
    public record TransitionResult(boolean success, StoredRequest request) {}

    // ============================================================
    // Integration Requests — CREATE
    // ============================================================

    public CreateResult create(IntegrationEnvelope envelope, String integrationType, JsonNode payload) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO crm_integration_requests " +
                            "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                            "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                            "source_entity_version, required_capability, data_classification, payload, status, " +
                            "requested_at, expires_at, created_at, updated_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                    id, envelope.tenantId(), envelope.actorId(), integrationType,
                    envelope.contractName(), envelope.contractVersion(), envelope.correlationId(),
                    envelope.causationId(), envelope.idempotencyKey(), envelope.sourceEntityType(),
                    envelope.sourceEntityId(), envelope.sourceEntityVersion(), envelope.requiredCapability(),
                    envelope.dataClassification(), json(payload), Timestamp.from(envelope.requestedAt()),
                    Timestamp.from(envelope.expiresAt()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
            return new CreateResult(find(envelope.tenantId(), id).orElseThrow(), CreationDisposition.CREATED_NEW);
        } catch (DuplicateKeyException duplicate) {
            StoredRequest existing = findByIdempotency(envelope.tenantId(), integrationType, envelope.idempotencyKey())
                    .orElseThrow(() -> duplicate);
            return new CreateResult(existing, CreationDisposition.RETURNED_EXISTING);
        }
    }

    // ============================================================
    // Integration Requests — TRANSITIONS
    // ============================================================

    /**
     * Status-only transition: does NOT modify result_payload, external_reference, or error_code.
     * Use for: PENDING→DISPATCHED, RECOMMENDATION_AVAILABLE→CONFIRMED, CONFIRMED→EXECUTING, etc.
     */
    public TransitionResult transitionStatus(UUID tenantId, UUID requestId, long expectedVersion,
                                              Set<String> allowedFrom, String targetStatus) {
        List<String> terminalList = new ArrayList<>(TERMINAL_STATES);
        String terminalMarkers = terminalList.stream().map(s -> "?").collect(Collectors.joining(","));
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_requests SET status=?, " +
                "completed_at=CASE WHEN ? IN (" + terminalMarkers + ") " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus); params.add(targetStatus); params.addAll(terminalList);
        params.add(tenantId); params.add(requestId); params.add(expectedVersion);
        if (allowedFrom != null && !allowedFrom.isEmpty()) {
            List<String> allowedList = new ArrayList<>(allowedFrom);
            sql.append(" AND status IN (").append(allowedList.stream().map(s->"?").collect(Collectors.joining(","))).append(")");
            params.addAll(allowedList);
        }
        int updated = jdbc.update(sql.toString(), params.toArray());
        if (updated != 1) return new TransitionResult(false, null);
        return new TransitionResult(true, find(tenantId, requestId).orElse(null));
    }

    /**
     * Transition with result: writes result_payload, external_reference, error_code exactly once.
     * Use only when receiving AI/Workflow response. Does NOT convert null to {}.
     */
    public TransitionResult transitionWithResult(UUID tenantId, UUID requestId, long expectedVersion,
                                                  Set<String> allowedFrom, String targetStatus,
                                                  UUID externalReference, JsonNode result,
                                                  String errorCode) {
        List<String> terminalList = new ArrayList<>(TERMINAL_STATES);
        String terminalMarkers = terminalList.stream().map(s -> "?").collect(Collectors.joining(","));
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_requests SET status=?, external_reference=?, " +
                "result_payload=" + (result == null ? "result_payload" : "CAST(? AS jsonb)") + ", " +
                "error_code=" + (errorCode == null ? "error_code" : "?") + ", " +
                "completed_at=CASE WHEN ? IN (" + terminalMarkers + ") " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus); params.add(externalReference);
        if (result != null) params.add(json(result));
        if (errorCode != null) params.add(errorCode);
        params.add(targetStatus); params.addAll(terminalList);
        params.add(tenantId); params.add(requestId); params.add(expectedVersion);
        if (allowedFrom != null && !allowedFrom.isEmpty()) {
            List<String> allowedList = new ArrayList<>(allowedFrom);
            sql.append(" AND status IN (").append(allowedList.stream().map(s->"?").collect(Collectors.joining(","))).append(")");
            params.addAll(allowedList);
        }
        int updated = jdbc.update(sql.toString(), params.toArray());
        if (updated != 1) return new TransitionResult(false, null);
        return new TransitionResult(true, find(tenantId, requestId).orElse(null));
    }

    // ============================================================
    // Integration Requests — QUERIES
    // ============================================================

    public Optional<StoredRequest> find(UUID tenantId, UUID id) {
        return jdbc.query("SELECT id, tenant_id, actor_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, contract_name, contract_version, " +
                        "causation_id, data_classification, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> mapRow(rs), tenantId, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, actor_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, contract_name, contract_version, " +
                        "causation_id, data_classification, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> mapRow(rs), tenantId, type, key).stream().findFirst();
    }

    // ============================================================
    // Transactional Outbox — CREATE
    // ============================================================

    public void createOutboxEvent(UUID tenantId, UUID requestId, String integrationType,
                                   String idempotencyKey, JsonNode payload) {
        jdbc.update("INSERT INTO crm_integration_outbox " +
                        "(tenant_id, integration_request_id, integration_type, dispatch_status, " +
                        "attempt_count, max_attempts, next_attempt_at, idempotency_key, payload, " +
                        "created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'PENDING', 0, 5, CURRENT_TIMESTAMP, ?, CAST(? AS jsonb), " +
                        "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                tenantId, requestId, integrationType, idempotencyKey, json(payload));
    }

    // ============================================================
    // Transactional Outbox — ATOMIC CLAIM (CTE)
    // ============================================================

    /**
     * Atomic claim using CTE: SELECT...FOR UPDATE SKIP LOCKED + UPDATE in one statement.
     * Returns event with new version and claim_token. Also recovers expired claims.
     */
    public Optional<OutboxEvent> claimNextOutboxEvent(String workerId, int claimTimeoutSeconds) {
        UUID claimToken = UUID.randomUUID();
        List<OutboxEvent> events = jdbc.query(
                "WITH candidate AS (" +
                "  SELECT id FROM crm_integration_outbox " +
                "  WHERE (dispatch_status IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= CURRENT_TIMESTAMP) " +
                "     OR (dispatch_status = 'CLAIMED' AND claim_expires_at <= CURRENT_TIMESTAMP) " +
                "  ORDER BY next_attempt_at, created_at " +
                "  FOR UPDATE SKIP LOCKED LIMIT 1" +
                ") " +
                "UPDATE crm_integration_outbox AS event " +
                "SET dispatch_status='CLAIMED', claimed_by=?, claim_token=?, " +
                "    claimed_at=CURRENT_TIMESTAMP, " +
                "    claim_expires_at=CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), " +
                "    attempt_count=attempt_count+1, " +
                "    updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "FROM candidate WHERE event.id = candidate.id " +
                "RETURNING event.id, event.tenant_id, event.integration_request_id, " +
                "          event.integration_type, event.dispatch_status, " +
                "          event.attempt_count, event.max_attempts, " +
                "          event.idempotency_key, event.payload, event.version, " +
                "          event.claim_token, event.claim_expires_at",
                (rs, row) -> new OutboxEvent(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        rs.getString("integration_type"), rs.getString("dispatch_status"),
                        rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                        rs.getString("idempotency_key"), readJson(rs.getString("payload")),
                        rs.getLong("version"),
                        (UUID) rs.getObject("claim_token"),
                        rs.getTimestamp("claim_expires_at") != null
                                ? rs.getTimestamp("claim_expires_at").toInstant() : null),
                workerId, claimToken, claimTimeoutSeconds);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    // ============================================================
    // Transactional Outbox — COMPLETE / FAIL (with claim ownership verification)
    // ============================================================

    public void completeOutboxEvent(UUID tenantId, UUID outboxId, long expectedVersion,
                                     UUID claimToken, String claimedBy) {
        int updated = jdbc.update(
                "UPDATE crm_integration_outbox SET dispatch_status='COMPLETED', completed_at=CURRENT_TIMESTAMP, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=? AND claim_token=? AND claimed_by=? " +
                        "AND dispatch_status='CLAIMED' AND claim_expires_at > CURRENT_TIMESTAMP",
                tenantId, outboxId, expectedVersion, claimToken, claimedBy);
        if (updated != 1) throw new IllegalStateException("Outbox claim lost, expired, or token mismatch");
    }

    public void failOutboxEvent(UUID tenantId, UUID outboxId, long expectedVersion,
                                 UUID claimToken, String claimedBy,
                                 String errorCode, boolean retryable) {
        String sql;
        var params = new ArrayList<Object>();
        if (retryable) {
            sql = "UPDATE crm_integration_outbox SET dispatch_status='RETRY_WAIT', " +
                    "last_error_code=?, " +
                    "next_attempt_at=CURRENT_TIMESTAMP + (POWER(2, LEAST(attempt_count, 6)) * INTERVAL '1 second'), " +
                    "claimed_at=NULL, claimed_by=NULL, claim_token=NULL, claim_expires_at=NULL, " +
                    "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                    "WHERE tenant_id=? AND id=? AND version=? AND claim_token=? AND claimed_by=? " +
                    "AND dispatch_status='CLAIMED' AND claim_expires_at > CURRENT_TIMESTAMP";
        } else {
            sql = "UPDATE crm_integration_outbox SET dispatch_status='DEAD_LETTER', " +
                    "last_error_code=?, completed_at=CURRENT_TIMESTAMP, " +
                    "claimed_at=NULL, claimed_by=NULL, claim_token=NULL, claim_expires_at=NULL, " +
                    "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                    "WHERE tenant_id=? AND id=? AND version=? AND claim_token=? AND claimed_by=? " +
                    "AND dispatch_status='CLAIMED' AND claim_expires_at > CURRENT_TIMESTAMP";
        }
        params.add(errorCode);
        params.add(tenantId); params.add(outboxId); params.add(expectedVersion);
        params.add(claimToken); params.add(claimedBy);
        int updated = jdbc.update(sql, params.toArray());
        if (updated != 1) throw new IllegalStateException("Outbox claim lost, expired, or token mismatch");
    }

    // ============================================================
    // Decisions (Human Confirmation Idempotency)
    // ============================================================

    public record DecisionResult(DecisionRecord record, boolean created) {}

    public DecisionResult createDecision(UUID tenantId, UUID requestId, UUID actorId,
                                          String decision, String idempotencyKey,
                                          String fingerprint, long expectedEntityVersion,
                                          String correlationId) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO crm_integration_decisions " +
                            "(id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                            "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                            "created_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, 0)",
                    id, tenantId, requestId, actorId, decision, idempotencyKey,
                    fingerprint, expectedEntityVersion, correlationId);
            return new DecisionResult(findDecision(tenantId, requestId, idempotencyKey).orElseThrow(), true);
        } catch (DuplicateKeyException duplicate) {
            DecisionRecord existing = findDecision(tenantId, requestId, idempotencyKey).orElseThrow();
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency key reused with different request fingerprint");
            }
            return new DecisionResult(existing, false);
        }
    }

    public boolean transitionDecision(UUID tenantId, UUID decisionId, long expectedVersion,
                                       Set<String> allowedFrom, String targetStatus,
                                       String commandReference, String errorCode) {
        // Terminal decision states: REJECTED, EXECUTED, EXECUTION_REJECTED, CONFLICT
        // Non-terminal: PENDING, CONFIRMED, EXECUTING (completed_at stays NULL)
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_decisions SET decision_status=?, " +
                        "command_reference=" + (commandReference == null ? "command_reference" : "?") + ", " +
                        "error_code=" + (errorCode == null ? "error_code" : "?") + ", " +
                        "completed_at=CASE WHEN ? IN ('REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT') " +
                        "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus);
        if (commandReference != null) params.add(commandReference);
        if (errorCode != null) params.add(errorCode);
        params.add(targetStatus);
        params.add(tenantId); params.add(decisionId); params.add(expectedVersion);
        if (allowedFrom != null && !allowedFrom.isEmpty()) {
            List<String> allowedList = new ArrayList<>(allowedFrom);
            sql.append(" AND decision_status IN (").append(allowedList.stream().map(s->"?").collect(Collectors.joining(","))).append(")");
            params.addAll(allowedList);
        }
        return jdbc.update(sql.toString(), params.toArray()) == 1;
    }

    public Optional<DecisionRecord> findDecision(UUID tenantId, UUID requestId, String idempotencyKey) {
        return jdbc.query("SELECT id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                        "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                        "command_reference, error_code, created_at, completed_at, version " +
                        "FROM crm_integration_decisions WHERE tenant_id=? AND integration_request_id=? AND idempotency_key=?",
                (rs, row) -> new DecisionRecord(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        (UUID) rs.getObject("actor_id"),
                        rs.getString("decision"), rs.getString("idempotency_key"),
                        rs.getString("request_fingerprint"), rs.getLong("expected_entity_version"),
                        rs.getString("correlation_id"), rs.getString("decision_status"),
                        rs.getString("command_reference"), rs.getString("error_code"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                        rs.getLong("version")),
                tenantId, requestId, idempotencyKey).stream().findFirst();
    }

    public Optional<DecisionRecord> findDecisionById(UUID tenantId, UUID integrationRequestId, UUID decisionId) {
        return jdbc.query("SELECT id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                        "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                        "command_reference, error_code, created_at, completed_at, version " +
                        "FROM crm_integration_decisions WHERE tenant_id=? AND integration_request_id=? AND id=?",
                (rs, row) -> new DecisionRecord(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        (UUID) rs.getObject("actor_id"),
                        rs.getString("decision"), rs.getString("idempotency_key"),
                        rs.getString("request_fingerprint"), rs.getLong("expected_entity_version"),
                        rs.getString("correlation_id"), rs.getString("decision_status"),
                        rs.getString("command_reference"), rs.getString("error_code"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                        rs.getLong("version")),
                tenantId, integrationRequestId, decisionId).stream().findFirst();
    }

    // ============================================================
    // Records & Exceptions
    // ============================================================

    public record StoredRequest(
            UUID id, UUID tenantId, UUID actorId, String integrationType, String status,
            UUID externalReference, String correlationId, String idempotencyKey,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String requiredCapability,
            String contractName, String contractVersion,
            String causationId, String dataClassification,
            JsonNode payload, JsonNode resultPayload,
            Instant requestedAt, Instant expiresAt, String errorCode,
            long version) {}

    public record OutboxEvent(
            UUID id, UUID tenantId, UUID integrationRequestId,
            String integrationType, String dispatchStatus,
            int attemptCount, int maxAttempts,
            String idempotencyKey, JsonNode payload, long version,
            UUID claimToken, Instant claimExpiresAt) {}

    public record DecisionRecord(
            UUID id, UUID tenantId, UUID integrationRequestId, UUID actorId,
            String decision, String idempotencyKey, String requestFingerprint,
            long expectedEntityVersion, String correlationId, String decisionStatus,
            String commandReference, String errorCode,
            Instant createdAt, Instant completedAt, long version) {}

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private StoredRequest mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredRequest(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("actor_id"),
                rs.getString("integration_type"), rs.getString("status"),
                (UUID) rs.getObject("external_reference"), rs.getString("correlation_id"),
                rs.getString("idempotency_key"), rs.getString("source_entity_type"),
                (UUID) rs.getObject("source_entity_id"), rs.getLong("source_entity_version"),
                rs.getString("required_capability"),
                rs.getString("contract_name"), rs.getString("contract_version"),
                rs.getString("causation_id"), rs.getString("data_classification"),
                readJson(rs.getString("payload")), readJson(rs.getString("result_payload")),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("error_code"), rs.getLong("version"));
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return mapper.readTree(value); } catch (Exception e) { return null; }
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid integration payload", e); }
    }
}
