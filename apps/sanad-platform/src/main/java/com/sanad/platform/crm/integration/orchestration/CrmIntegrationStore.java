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
 * <p>All state transitions use {@link #transition} which enforces
 * optimistic locking and allowed-current-status checks atomically.
 * There is no public unconditional {@code complete()} method.</p>
 *
 * <p>Also manages the transactional outbox ({@code crm_integration_outbox})
 * and decision records ({@code crm_integration_decisions}).</p>
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
    // Integration Requests
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

    public TransitionResult transition(UUID tenantId, UUID requestId, long expectedVersion,
                                        Set<String> allowedFrom, String targetStatus,
                                        UUID externalReference, JsonNode result,
                                        String errorCode) {
        List<String> terminalList = new ArrayList<>(TERMINAL_STATES);
        String terminalMarkers = terminalList.stream().map(s -> "?").collect(Collectors.joining(","));
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_requests SET status=?, external_reference=?, " +
                "result_payload=CAST(? AS jsonb), error_code=?, " +
                "completed_at=CASE WHEN ? IN (" + terminalMarkers + ") " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus); params.add(externalReference); params.add(json(result));
        params.add(errorCode); params.add(targetStatus); params.addAll(terminalList);
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

    public Optional<StoredRequest> find(UUID tenantId, UUID id) {
        return jdbc.query("SELECT id, tenant_id, actor_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> mapRow(rs), tenantId, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, actor_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> mapRow(rs), tenantId, type, key).stream().findFirst();
    }

    // ============================================================
    // Transactional Outbox
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

    public Optional<OutboxEvent> claimNextOutboxEvent(String workerId, int claimTimeoutSeconds) {
        // Use FOR UPDATE SKIP LOCKED for atomic claim
        List<OutboxEvent> events = jdbc.query(
                "SELECT id, tenant_id, integration_request_id, integration_type, dispatch_status, " +
                        "attempt_count, max_attempts, idempotency_key, payload, version " +
                        "FROM crm_integration_outbox " +
                        "WHERE dispatch_status IN ('PENDING', 'RETRY_WAIT') " +
                        "AND next_attempt_at <= CURRENT_TIMESTAMP " +
                        "ORDER BY next_attempt_at " +
                        "LIMIT 1 " +
                        "FOR UPDATE SKIP LOCKED",
                (rs, row) -> new OutboxEvent(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        rs.getString("integration_type"), rs.getString("dispatch_status"),
                        rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                        rs.getString("idempotency_key"), readJson(rs.getString("payload")),
                        rs.getLong("version")));

        if (events.isEmpty()) return Optional.empty();
        OutboxEvent event = events.get(0);

        // Mark as claimed
        int updated = jdbc.update(
                "UPDATE crm_integration_outbox SET dispatch_status='CLAIMED', claimed_by=?, " +
                        "claimed_at=CURRENT_TIMESTAMP, claim_expires_at=CURRENT_TIMESTAMP + INTERVAL '1 minute', " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=? AND dispatch_status IN ('PENDING','RETRY_WAIT')",
                workerId, event.tenantId(), event.id(), event.version());
        if (updated != 1) return Optional.empty(); // Lost race
        return Optional.of(event);
    }

    public void completeOutboxEvent(UUID tenantId, UUID outboxId, long expectedVersion) {
        int updated = jdbc.update(
                "UPDATE crm_integration_outbox SET dispatch_status='COMPLETED', completed_at=CURRENT_TIMESTAMP, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=? AND dispatch_status='CLAIMED'",
                tenantId, outboxId, expectedVersion);
        if (updated != 1) throw new IllegalStateException("Outbox event claim lost or already completed");
    }

    public void failOutboxEvent(UUID tenantId, UUID outboxId, long expectedVersion,
                                 String errorCode, boolean retryable) {
        if (retryable) {
            jdbc.update(
                    "UPDATE crm_integration_outbox SET dispatch_status='RETRY_WAIT', " +
                            "attempt_count=attempt_count+1, last_error_code=?, " +
                            "next_attempt_at=CURRENT_TIMESTAMP + (POWER(2, LEAST(attempt_count, 6)) * INTERVAL '1 second'), " +
                            "claimed_at=NULL, claimed_by=NULL, claim_expires_at=NULL, " +
                            "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                            "WHERE tenant_id=? AND id=? AND version=? AND dispatch_status='CLAIMED'",
                    errorCode, tenantId, outboxId, expectedVersion);
        } else {
            jdbc.update(
                    "UPDATE crm_integration_outbox SET dispatch_status='DEAD_LETTER', " +
                            "last_error_code=?, completed_at=CURRENT_TIMESTAMP, " +
                            "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                            "WHERE tenant_id=? AND id=? AND version=? AND dispatch_status='CLAIMED'",
                    errorCode, tenantId, outboxId, expectedVersion);
        }
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
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_decisions SET decision_status=?, " +
                        "command_reference=?, error_code=?, " +
                        "completed_at=CASE WHEN ? IN ('CONFIRMED','REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT') " +
                        "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus); params.add(commandReference); params.add(errorCode); params.add(targetStatus);
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

    // ============================================================
    // Records & Exceptions
    // ============================================================

    public record StoredRequest(
            UUID id, UUID tenantId, UUID actorId, String integrationType, String status,
            UUID externalReference, String correlationId, String idempotencyKey,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String requiredCapability,
            JsonNode payload, JsonNode resultPayload,
            Instant requestedAt, Instant expiresAt, String errorCode,
            long version) {}

    public record OutboxEvent(
            UUID id, UUID tenantId, UUID integrationRequestId,
            String integrationType, String dispatchStatus,
            int attemptCount, int maxAttempts,
            String idempotencyKey, JsonNode payload, long version) {}

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
