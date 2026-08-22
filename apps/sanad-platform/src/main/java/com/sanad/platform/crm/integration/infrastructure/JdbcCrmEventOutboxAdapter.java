package com.sanad.platform.crm.integration.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC adapter for {@link CrmEventOutboxPort}.
 *
 * <p>Persists to the {@code crm_event_outbox} table created by
 * V20260822_1 + V20260822_4 (alignment).
 *
 * <p>The adapter is a persistence concern ONLY. It MUST NOT:
 * <ul>
 *   <li>Mutate the {@code app.tenant_id} GUC.</li>
 *   <li>Read {@link org.springframework.security.core.context.SecurityContextHolder}.</li>
 *   <li>Use {@code SELECT *}.</li>
 *   <li>Drop the supplied {@code tenantId} from any SQL predicate.</li>
 * </ul>
 *
 * <p>Tests establish transaction-local GUC explicitly. Wrong tenant GUC
 * must never defeat RLS — every tenant-scoped read/update includes the
 * explicit {@code tenant_id=:tenantId} predicate, and the FORCE RLS
 * policy independently filters on {@code current_setting('app.tenant_id')}.
 */
@Component
public class JdbcCrmEventOutboxAdapter implements CrmEventOutboxPort {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcCrmEventOutboxAdapter(NamedParameterJdbcTemplate jdbc,
                                      ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(CrmEventEnvelope event) {
        Objects.requireNonNull(event, "event must not be null");
        String payloadJson = serialisePayload(event.payload());
        // updated_at is NOT NULL in V1 — initialise to createdAt for
        // deterministic initial persistence.
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("tenantId", event.tenantId())
                .addValue("eventType", event.eventType())
                .addValue("payloadJson", payloadJson, Types.VARCHAR)
                .addValue("correlationId", event.correlationId())
                .addValue("causationId", event.causationId())
                .addValue("schemaVersion", event.schemaVersion())
                .addValue("aggregateType", event.aggregateType())
                .addValue("aggregateId", event.aggregateId())
                .addValue("status", "PENDING")
                .addValue("attemptCount", 0)
                .addValue("availableAt", Timestamp.from(event.availableAt()))
                .addValue("createdAt", Timestamp.from(event.createdAt()))
                .addValue("updatedAt", Timestamp.from(event.createdAt()));
        jdbc.update(
                "INSERT INTO crm_event_outbox " +
                "(id, tenant_id, event_type, payload_json, correlation_id, causation_id, " +
                " schema_version, aggregate_type, aggregate_id, status, attempt_count, " +
                " available_at, created_at, updated_at) " +
                "VALUES (:id, :tenantId, :eventType, :payloadJson, :correlationId, :causationId, " +
                " :schemaVersion, :aggregateType, :aggregateId, :status, :attemptCount, " +
                " :availableAt, :createdAt, :updatedAt)",
                params);
    }

    @Override
    public List<CrmEventEnvelope> claimDue(UUID tenantId, Instant now, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be in [1, " + MAX_LIMIT + "], got " + limit);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit);
        // SELECT ids of due rows, lock them for update so concurrent workers
        // skip past locked rows.
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM crm_event_outbox " +
                "WHERE tenant_id = :tenantId " +
                "  AND status IN ('PENDING','FAILED') " +
                "  AND available_at <= :now " +
                "ORDER BY available_at ASC, created_at ASC, id ASC " +
                "FOR UPDATE SKIP LOCKED " +
                "LIMIT :limit",
                params,
                UUID.class);
        if (ids.isEmpty()) {
            return List.of();
        }
        // Transition the selected rows to PROCESSING atomically.
        // The order of returned envelopes MUST match the SELECT order.
        MapSqlParameterSource updateParams = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ids", ids)
                .addValue("now", Timestamp.from(now));
        jdbc.update(
                "UPDATE crm_event_outbox " +
                "SET status = 'PROCESSING', " +
                "    claimed_at = :now, " +
                "    updated_at = :now " +
                "WHERE tenant_id = :tenantId " +
                "  AND id IN (:ids) " +
                "  AND status IN ('PENDING','FAILED')",
                updateParams);
        // Read back the claimed rows in the SAME order as the SELECT.
        MapSqlParameterSource readParams = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ids", ids);
        // We can't rely on IN list ordering, so build the ordered result
        // by mapping each id to its envelope.
        List<CrmEventEnvelope> unordered = jdbc.query(
                "SELECT id, tenant_id, event_type, schema_version, aggregate_type, " +
                "       aggregate_id, correlation_id, causation_id, payload_json, " +
                "       available_at, created_at " +
                "FROM crm_event_outbox " +
                "WHERE tenant_id = :tenantId AND id IN (:ids)",
                readParams,
                (rs, rowNum) -> mapEnvelope(rs));
        // Preserve deterministic order from the SELECT.
        java.util.Map<UUID, CrmEventEnvelope> byId = new java.util.HashMap<>();
        for (CrmEventEnvelope e : unordered) {
            byId.put(e.id(), e);
        }
        List<CrmEventEnvelope> ordered = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            CrmEventEnvelope e = byId.get(id);
            if (e != null) {
                ordered.add(e);
            }
        }
        return ordered;
    }

    @Override
    public boolean markPublished(UUID tenantId, UUID eventId, Instant publishedAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("eventId", eventId)
                .addValue("publishedAt", Timestamp.from(publishedAt));
        int updated = jdbc.update(
                "UPDATE crm_event_outbox " +
                "SET status = 'PUBLISHED', " +
                "    published_at = :publishedAt, " +
                "    updated_at = :publishedAt " +
                "WHERE tenant_id = :tenantId " +
                "  AND id = :eventId " +
                "  AND status = 'PROCESSING'",
                params);
        return updated == 1;
    }

    @Override
    public boolean markFailed(UUID tenantId, UUID eventId, Instant nextAttemptAt, String error) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        // Truncate error in Java — do NOT rely on DB truncation.
        String boundedError = boundError(error);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("eventId", eventId)
                .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .addValue("lastError", boundedError, Types.VARCHAR);
        int updated = jdbc.update(
                "UPDATE crm_event_outbox " +
                "SET status = 'FAILED', " +
                "    attempt_count = attempt_count + 1, " +
                "    claimed_at = NULL, " +
                "    available_at = :nextAttemptAt, " +
                "    last_error = :lastError, " +
                "    updated_at = CURRENT_TIMESTAMP " +
                "WHERE tenant_id = :tenantId " +
                "  AND id = :eventId " +
                "  AND status = 'PROCESSING'",
                params);
        return updated == 1;
    }

    // ---------- helpers ----------

    private CrmEventEnvelope mapEnvelope(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        String eventType = rs.getString("event_type");
        int schemaVersion = rs.getInt("schema_version");
        String aggregateType = rs.getString("aggregate_type");
        UUID aggregateId = rs.getObject("aggregate_id", UUID.class);
        String correlationId = rs.getString("correlation_id");
        String causationId = rs.getString("causation_id");
        String payloadJson = rs.getString("payload_json");
        java.sql.Timestamp availableAt = rs.getTimestamp("available_at");
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        JsonNode payload = parsePayload(payloadJson);
        return new CrmEventEnvelope(
                id,
                tenantId,
                eventType,
                schemaVersion,
                aggregateType,
                aggregateId,
                correlationId,
                causationId,
                payload,
                availableAt.toInstant(),
                createdAt.toInstant());
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null) {
            throw new IllegalStateException(
                    "stored crm_event_outbox.payload_json is NULL — row is corrupt");
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to deserialize crm_event_outbox.payload_json: " + e.getMessage(), e);
        }
    }

    private String serialisePayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialize crm_event_outbox payload", e);
        }
    }

    private String boundError(String error) {
        if (error == null) {
            return null;
        }
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
