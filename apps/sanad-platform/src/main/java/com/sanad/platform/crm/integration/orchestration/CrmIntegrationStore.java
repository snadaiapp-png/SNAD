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
 */
@Component
public class CrmIntegrationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Terminal states where completed_at is set. CONFIRMED is NOT terminal. */
    public static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "EXECUTED", "EXECUTION_REJECTED", "REJECTED",
            "POLICY_DENIED", "UNSAFE_OUTPUT", "TIMED_OUT",
            "UNAVAILABLE", "CANCELLED", "EXPIRED");

    /** Intermediate (non-terminal) states where completed_at stays NULL. */
    public static final Set<String> INTERMEDIATE_STATES = Set.of(
            "PENDING", "DISPATCHED", "ACCEPTED", "RUNNING",
            "RECOMMENDATION_AVAILABLE", "CONFIRMED", "EXECUTING");

    public CrmIntegrationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record CreateResult(StoredRequest request, CreationDisposition disposition) {}
    public enum CreationDisposition { CREATED_NEW, RETURNED_EXISTING }

    public record TransitionResult(boolean success, StoredRequest request) {}

    /**
     * Create a new integration request or return existing if idempotency key matches.
     */
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

    /**
     * Atomic conditional state transition with optimistic locking.
     * Fails (returns success=false) if version mismatch or current status not allowed.
     * Sets completed_at only for terminal states (NOT for CONFIRMED, EXECUTING, etc.).
     */
    public TransitionResult transition(UUID tenantId, UUID requestId, long expectedVersion,
                                        Set<String> allowedFrom, String targetStatus,
                                        UUID externalReference, JsonNode result,
                                        String errorCode) {
        // Build terminal-state placeholders deterministically
        List<String> terminalList = new ArrayList<>(TERMINAL_STATES);
        String terminalPlaceholders = terminalList.stream()
                .map(s -> "?")
                .collect(Collectors.joining(","));

        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_requests SET status=?, external_reference=?, " +
                "result_payload=CAST(? AS jsonb), error_code=?, " +
                "completed_at=CASE WHEN ? IN (" + terminalPlaceholders + ") " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND id=? AND version=?");

        var params = new ArrayList<Object>();
        params.add(targetStatus);
        params.add(externalReference);
        params.add(json(result));
        params.add(errorCode);
        params.add(targetStatus);
        params.addAll(terminalList);
        params.add(tenantId);
        params.add(requestId);
        params.add(expectedVersion);

        if (allowedFrom != null && !allowedFrom.isEmpty()) {
            List<String> allowedList = new ArrayList<>(allowedFrom);
            String allowedPlaceholders = allowedList.stream()
                    .map(s -> "?")
                    .collect(Collectors.joining(","));
            sql.append(" AND status IN (").append(allowedPlaceholders).append(")");
            params.addAll(allowedList);
        }

        int updated = jdbc.update(sql.toString(), params.toArray());
        if (updated != 1) return new TransitionResult(false, null);
        return new TransitionResult(true, find(tenantId, requestId).orElse(null));
    }

    public Optional<StoredRequest> find(UUID tenantId, UUID id) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, " +
                        "payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> mapRow(rs),
                tenantId, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, " +
                        "payload, result_payload, " +
                        "requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> mapRow(rs),
                tenantId, type, key).stream().findFirst();
    }

    private StoredRequest mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredRequest(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("integration_type"),
                rs.getString("status"),
                (UUID) rs.getObject("external_reference"),
                rs.getString("correlation_id"),
                rs.getString("idempotency_key"),
                rs.getString("source_entity_type"),
                (UUID) rs.getObject("source_entity_id"),
                rs.getLong("source_entity_version"),
                rs.getString("required_capability"),
                readJson(rs.getString("payload")),
                readJson(rs.getString("result_payload")),
                rs.getTimestamp("requested_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getString("error_code"),
                rs.getLong("version"));
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return mapper.readTree(value); }
        catch (Exception e) { return null; }
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid integration payload", error); }
    }

    public record StoredRequest(
            UUID id, UUID tenantId, String integrationType, String status,
            UUID externalReference, String correlationId, String idempotencyKey,
            String sourceEntityType, UUID sourceEntityId, long sourceEntityVersion,
            String requiredCapability,
            JsonNode payload, JsonNode resultPayload,
            Instant requestedAt, Instant expiresAt, String errorCode,
            long version) {}
}
