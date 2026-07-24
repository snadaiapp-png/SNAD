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
 *   <li>{@link #transitionWithResult} — writes result_payload exactly once
 *       (SQL-level guard: AND result_payload IS NULL)</li>
 *   <li>{@link #claimNextOutboxEvent} — atomic CTE-based claim with claim_token</li>
 *   <li>{@link #completeOutboxEvent} / {@link #failOutboxEvent} — claim ownership verified,
 *       all claim fields cleared on completion</li>
 *   <li>{@link #createDecision} / {@link #findDecision} / {@link #findDecisionById}</li>
 * </ul>
 *
 * <p>Envelopes are reconstructed from stored columns only. No fallback values
 * are guessed by callers — missing required fields surface as
 * {@link IntegrationErrorCode#INVALID_CONTRACT} at the application layer.</p>
 */
@Component
public class CrmIntegrationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "EXECUTED", "EXECUTION_REJECTED", "REJECTED",
            "POLICY_DENIED", "UNSAFE_OUTPUT", "TIMED_OUT",
            "UNAVAILABLE", "CANCELLED", "EXPIRED");

    /** Decision statuses considered terminal (completed_at must be NOT NULL). */
    public static final Set<String> TERMINAL_DECISION_STATES = Set.of(
            "REJECTED", "EXECUTED", "EXECUTION_REJECTED", "CONFLICT");

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
                            "source_entity_version, required_capability, data_classification, requested_locale, " +
                            "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                    id, envelope.tenantId(), envelope.actorId(), integrationType,
                    envelope.contractName(), envelope.contractVersion(), envelope.correlationId(),
                    envelope.causationId(), envelope.idempotencyKey(), envelope.sourceEntityType(),
                    envelope.sourceEntityId(), envelope.sourceEntityVersion(), envelope.requiredCapability(),
                    envelope.dataClassification(),
                    envelope.locale() == null ? null : envelope.locale().toLanguageTag(),
                    json(payload), Timestamp.from(envelope.requestedAt()),
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
     *
     * <p>Atomic If-Match: caller passes {@code expectedVersion} — the UPDATE only
     * affects a row whose version matches. If 0 rows updated, the caller observes
     * a transition conflict and can surface {@link IntegrationErrorCode#STATE_TRANSITION_FAILED}
     * or {@link IntegrationErrorCode#INTEGRATION_VERSION_MISMATCH}.</p>
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
     *
     * <p><strong>SQL-level immutability:</strong> the UPDATE includes
     * {@code AND result_payload IS NULL} so concurrent workers or retry paths
     * cannot overwrite an existing AI result. The second writer observes 0 rows
     * affected and the caller surfaces a transition conflict.</p>
     *
     * <p>Use only when receiving AI/Workflow response. Does NOT convert null to {}.</p>
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
                "WHERE tenant_id=? AND id=? AND version=? " +
                "AND result_payload IS NULL");
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
                        "causation_id, data_classification, requested_locale, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> mapRow(rs), tenantId, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, actor_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, contract_name, contract_version, " +
                        "causation_id, data_classification, requested_locale, payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> mapRow(rs), tenantId, type, key).stream().findFirst();
    }

    // ============================================================
    // Transactional Outbox — CREATE
    // ============================================================

    public void createOutboxEvent(UUID tenantId, UUID requestId, String integrationType,
                                   String eventType, String idempotencyKey, JsonNode payload) {
        jdbc.update("INSERT INTO crm_integration_outbox " +
                        "(tenant_id, integration_request_id, integration_type, event_type, dispatch_status, " +
                        "attempt_count, max_attempts, next_attempt_at, idempotency_key, payload, " +
                        "created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, 'PENDING', 0, 5, CURRENT_TIMESTAMP, ?, CAST(? AS jsonb), " +
                        "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                tenantId, requestId, integrationType,
                eventType == null ? "AI_REQUEST_DISPATCH" : eventType,
                idempotencyKey, json(payload));
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
                "          event.integration_type, event.event_type, event.dispatch_status, " +
                "          event.attempt_count, event.max_attempts, " +
                "          event.idempotency_key, event.payload, event.version, " +
                "          event.claim_token, event.claim_expires_at, event.claimed_by",
                (rs, row) -> new OutboxEvent(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        rs.getString("integration_type"),
                        rs.getString("event_type") == null ? "AI_REQUEST_DISPATCH" : rs.getString("event_type"),
                        rs.getString("dispatch_status"),
                        rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                        rs.getString("idempotency_key"), readJson(rs.getString("payload")),
                        rs.getLong("version"),
                        (UUID) rs.getObject("claim_token"),
                        rs.getTimestamp("claim_expires_at") != null
                                ? rs.getTimestamp("claim_expires_at").toInstant() : null,
                        rs.getString("claimed_by")),
                workerId, claimToken, claimTimeoutSeconds);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    /**
     * Atomic claim using CTE with event-type filtering.
     *
     * <p>Only events whose {@code event_type} is in {@code acceptedEventTypes}
     * are claimed. This ensures the AI worker never claims a
     * {@code CONFIRMED_COMMAND_EXECUTION} event and the command worker
     * never claims an {@code AI_REQUEST_DISPATCH} event. An event is never
     * left in CLAIMED by the wrong worker.</p>
     *
     * <p>If {@code acceptedEventTypes} is null or empty, falls back to the
     * unfiltered claim (backwards-compatible).</p>
     */
    public Optional<OutboxEvent> claimNextOutboxEvent(String workerId, int claimTimeoutSeconds,
                                                       Set<String> acceptedEventTypes) {
        if (acceptedEventTypes == null || acceptedEventTypes.isEmpty()) {
            return claimNextOutboxEvent(workerId, claimTimeoutSeconds);
        }
        UUID claimToken = UUID.randomUUID();
        List<String> typeList = new ArrayList<>(acceptedEventTypes);
        String typeMarkers = typeList.stream().map(s -> "?").collect(Collectors.joining(","));

        List<OutboxEvent> events = jdbc.query(
                "WITH candidate AS (" +
                "  SELECT id FROM crm_integration_outbox " +
                "  WHERE event_type IN (" + typeMarkers + ") " +
                "    AND ((dispatch_status IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= CURRENT_TIMESTAMP) " +
                "     OR (dispatch_status = 'CLAIMED' AND claim_expires_at <= CURRENT_TIMESTAMP)) " +
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
                "          event.integration_type, event.event_type, event.dispatch_status, " +
                "          event.attempt_count, event.max_attempts, " +
                "          event.idempotency_key, event.payload, event.version, " +
                "          event.claim_token, event.claim_expires_at, event.claimed_by",
                (rs, row) -> new OutboxEvent(
                        (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        (UUID) rs.getObject("integration_request_id"),
                        rs.getString("integration_type"),
                        rs.getString("event_type") == null ? "AI_REQUEST_DISPATCH" : rs.getString("event_type"),
                        rs.getString("dispatch_status"),
                        rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                        rs.getString("idempotency_key"), readJson(rs.getString("payload")),
                        rs.getLong("version"),
                        (UUID) rs.getObject("claim_token"),
                        rs.getTimestamp("claim_expires_at") != null
                                ? rs.getTimestamp("claim_expires_at").toInstant() : null,
                        rs.getString("claimed_by")),
                prependArgs(typeList, workerId, claimToken, claimTimeoutSeconds));
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    private Object[] prependArgs(List<String> typeList, String workerId,
                                  UUID claimToken, int claimTimeoutSeconds) {
        Object[] args = new Object[typeList.size() + 3];
        for (int i = 0; i < typeList.size(); i++) args[i] = typeList.get(i);
        args[typeList.size()] = workerId;
        args[typeList.size() + 1] = claimToken;
        args[typeList.size() + 2] = claimTimeoutSeconds;
        return args;
    }

    // ============================================================
    // Transactional Outbox — COMPLETE / FAIL (with claim ownership verification)
    // ============================================================

    /**
     * Complete an outbox event — clears ALL claim fields atomically.
     *
     * <p>Required claim ownership: the caller must hold the matching
     * {@code claim_token}, {@code claimed_by}, an unexpired claim, and the
     * expected row version. If any of these conditions fail, 0 rows are updated
     * and an {@link IntegrationException} ({@code OUTBOX_CLAIM_LOST}) is thrown.</p>
     */
    public void completeOutboxEvent(UUID tenantId, UUID outboxId, long expectedVersion,
                                     UUID claimToken, String claimedBy) {
        int updated = jdbc.update(
                "UPDATE crm_integration_outbox SET dispatch_status = 'COMPLETED', " +
                        "completed_at = CURRENT_TIMESTAMP, " +
                        "claimed_at = NULL, claimed_by = NULL, " +
                        "claim_token = NULL, claim_expires_at = NULL, " +
                        "updated_at = CURRENT_TIMESTAMP, version = version + 1 " +
                        "WHERE tenant_id = ? AND id = ? AND version = ? " +
                        "AND claim_token = ? AND claimed_by = ? " +
                        "AND dispatch_status = 'CLAIMED' " +
                        "AND claim_expires_at > CURRENT_TIMESTAMP",
                tenantId, outboxId, expectedVersion, claimToken, claimedBy);
        if (updated != 1) {
            throw new IntegrationException(IntegrationErrorCode.OUTBOX_CLAIM_LOST,
                    "Outbox claim lost, expired, or token mismatch (outboxId=" + outboxId + ")");
        }
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
        if (updated != 1) {
            throw new IntegrationException(IntegrationErrorCode.OUTBOX_CLAIM_LOST,
                    "Outbox claim lost, expired, or token mismatch (outboxId=" + outboxId + ")");
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
                throw new IntegrationException(IntegrationErrorCode.IDEMPOTENCY_KEY_REUSED,
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
    // Command Execution Ledger
    // ============================================================

    /**
     * Create a ledger row for a confirmed-command execution. Idempotent:
     * if a row already exists for the same (tenantId, decisionId), returns
     * the existing row without creating a new one.
     *
     * <p><strong>Implementation note:</strong> uses {@code INSERT ... ON
     * CONFLICT DO NOTHING} (PostgreSQL) so a duplicate does not abort the
     * surrounding transaction. After the upsert, the row is read back
     * unconditionally.</p>
     *
     * <p>The ledger row is created in PENDING state before the CRM command
     * is invoked. On crash recovery, the worker reads the ledger to
     * determine whether the command was already executed.</p>
     */
    public LedgerResult createExecutionLedger(UUID tenantId, UUID decisionId,
                                                UUID integrationRequestId, UUID actorId,
                                                String actionCode, String idempotencyKey,
                                                UUID claimToken) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(
                "INSERT INTO crm_integration_command_executions " +
                        "(id, tenant_id, decision_id, integration_request_id, action_code, " +
                        "execution_status, idempotency_key, attempt_count, claim_token, " +
                        "started_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, 0, ?, CURRENT_TIMESTAMP, " +
                        "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0) " +
                        "ON CONFLICT (tenant_id, decision_id) DO NOTHING",
                id, tenantId, decisionId, integrationRequestId, actionCode,
                idempotencyKey, claimToken);
        boolean created = inserted > 0;
        return new LedgerResult(findExecutionLedger(tenantId, decisionId).orElseThrow(), created);
    }

    public record LedgerResult(CommandExecutionLedger ledger, boolean created) {}

    /**
     * Transition a ledger row to a new status. Returns true if exactly one
     * row was updated (verifies affected row count).
     */
    public boolean transitionExecutionLedger(UUID tenantId, UUID ledgerId, long expectedVersion,
                                               Set<String> allowedFrom, String targetStatus,
                                               String commandReference, String errorCode,
                                               JsonNode resultPayload) {
        // Terminal execution states: EXECUTED, EXECUTION_REJECTED, UNKNOWN_OUTCOME
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_command_executions SET execution_status=?, " +
                        "command_reference=" + (commandReference == null ? "command_reference" : "?") + ", " +
                        "error_code=" + (errorCode == null ? "error_code" : "?") + ", " +
                        "result_payload=" + (resultPayload == null ? "result_payload" : "CAST(? AS jsonb)") + ", " +
                        "completed_at=CASE WHEN ? IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME') " +
                        "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=?");
        var params = new ArrayList<Object>();
        params.add(targetStatus);
        if (commandReference != null) params.add(commandReference);
        if (errorCode != null) params.add(errorCode);
        if (resultPayload != null) params.add(json(resultPayload));
        params.add(targetStatus);
        params.add(tenantId); params.add(ledgerId); params.add(expectedVersion);
        if (allowedFrom != null && !allowedFrom.isEmpty()) {
            List<String> allowedList = new ArrayList<>(allowedFrom);
            sql.append(" AND execution_status IN (").append(allowedList.stream().map(s->"?").collect(Collectors.joining(","))).append(")");
            params.addAll(allowedList);
        }
        return jdbc.update(sql.toString(), params.toArray()) == 1;
    }

    /**
     * Increment the attempt_count on a ledger row. Used when a worker
     * retries after a crash. Returns true if exactly one row was updated.
     */
    public boolean incrementLedgerAttempt(UUID tenantId, UUID ledgerId, long expectedVersion,
                                            UUID claimToken) {
        int updated = jdbc.update(
                "UPDATE crm_integration_command_executions SET attempt_count=attempt_count+1, " +
                        "claim_token=?, started_at=CURRENT_TIMESTAMP, " +
                        "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                        "WHERE tenant_id=? AND id=? AND version=?",
                claimToken, tenantId, ledgerId, expectedVersion);
        return updated == 1;
    }

    public Optional<CommandExecutionLedger> findExecutionLedger(UUID tenantId, UUID decisionId) {
        return jdbc.query("SELECT id, tenant_id, decision_id, integration_request_id, " +
                        "action_code, execution_status, idempotency_key, attempt_count, " +
                        "command_reference, result_payload, error_code, claim_token, " +
                        "started_at, completed_at, created_at, updated_at, version " +
                        "FROM crm_integration_command_executions " +
                        "WHERE tenant_id=? AND decision_id=?",
                (rs, row) -> mapLedgerRow(rs), tenantId, decisionId).stream().findFirst();
    }

    public Optional<CommandExecutionLedger> findExecutionLedgerById(UUID tenantId, UUID ledgerId) {
        return jdbc.query("SELECT id, tenant_id, decision_id, integration_request_id, " +
                        "action_code, execution_status, idempotency_key, attempt_count, " +
                        "command_reference, result_payload, error_code, claim_token, " +
                        "started_at, completed_at, created_at, updated_at, version " +
                        "FROM crm_integration_command_executions " +
                        "WHERE tenant_id=? AND id=?",
                (rs, row) -> mapLedgerRow(rs), tenantId, ledgerId).stream().findFirst();
    }

    private CommandExecutionLedger mapLedgerRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CommandExecutionLedger(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("decision_id"), (UUID) rs.getObject("integration_request_id"),
                rs.getString("action_code"), rs.getString("execution_status"),
                rs.getString("idempotency_key"), rs.getInt("attempt_count"),
                rs.getString("command_reference"), readJson(rs.getString("result_payload")),
                rs.getString("error_code"), (UUID) rs.getObject("claim_token"),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
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
            String causationId, String dataClassification, String requestedLocale,
            JsonNode payload, JsonNode resultPayload,
            Instant requestedAt, Instant expiresAt, String errorCode,
            long version) {}

    public record OutboxEvent(
            UUID id, UUID tenantId, UUID integrationRequestId,
            String integrationType, String eventType, String dispatchStatus,
            int attemptCount, int maxAttempts,
            String idempotencyKey, JsonNode payload, long version,
            UUID claimToken, Instant claimExpiresAt, String claimedBy) {}

    public record DecisionRecord(
            UUID id, UUID tenantId, UUID integrationRequestId, UUID actorId,
            String decision, String idempotencyKey, String requestFingerprint,
            long expectedEntityVersion, String correlationId, String decisionStatus,
            String commandReference, String errorCode,
            Instant createdAt, Instant completedAt, long version) {}

    /**
     * Durable ledger row for a confirmed-command execution. One row per
     * decision. Used for crash recovery: the worker reads the ledger to
     * determine whether the CRM command was already executed.
     */
    public record CommandExecutionLedger(
            UUID id, UUID tenantId, UUID decisionId, UUID integrationRequestId,
            String actionCode, String executionStatus, String idempotencyKey,
            int attemptCount, String commandReference, JsonNode resultPayload,
            String errorCode, UUID claimToken,
            Instant startedAt, Instant completedAt,
            Instant createdAt, Instant updatedAt, long version) {}

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
                rs.getString("requested_locale"),
                readJson(rs.getString("payload")), readJson(rs.getString("result_payload")),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("error_code"), rs.getLong("version"));
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(value);
            // H2 in PostgreSQL mode wraps JSON values in a JSON string scalar
            // (e.g. returns "\"{\\\"key\\\":...}\"" instead of "{...}").
            // PostgreSQL returns the raw JSON object. To handle both, if the
            // parsed node is a text node (JSON string), parse its contents as
            // JSON again.
            if (node != null && node.isTextual()) {
                String inner = node.asText();
                if (inner != null && !inner.isBlank() && (inner.startsWith("{") || inner.startsWith("["))) {
                    return mapper.readTree(inner);
                }
            }
            return node;
        } catch (Exception e) { return null; }
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid integration payload", e); }
    }
}
