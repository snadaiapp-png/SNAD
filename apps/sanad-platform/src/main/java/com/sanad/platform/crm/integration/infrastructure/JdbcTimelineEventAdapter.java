package com.sanad.platform.crm.integration.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * JDBC adapter for {@link TimelineEventPort}.
 *
 * <p>Legacy {@code record(...)} behaviour is byte-for-byte preserved:
 * the same INSERT statement, the same parameter set, the same column
 * projection. Legacy callers continue to write rows that omit the
 * structured SAM extension columns (summary_key, metadata_json,
 * correlation_id, causation_id are SQL NULL; schema_version defaults to 1
 * via the V20260822_1 column DEFAULT).
 *
 * <p>The structured path overrides {@link #record(TimelineEventPort.StructuredTimelineEvent)}
 * and persists all V20260822_1 extension columns. metadata is serialised
 * through {@link ObjectMapper#writeValueAsString(Object)}; serialisation
 * failures surface as {@link IllegalStateException}.
 *
 * <p>This adapter is a persistence concern ONLY. It MUST NOT mutate the
 * tenant GUC or read {@link org.springframework.security.core.context.SecurityContextHolder}.
 * Tenant scoping is enforced by RLS on {@code crm_timeline_events} and
 * the caller is responsible for setting the GUC inside the active
 * transaction (see {@code TenantRlsTransactionContext}).
 */
@Component
public class JdbcTimelineEventAdapter implements TimelineEventPort {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcTimelineEventAdapter(NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(UUID tenantId, String subjectType, UUID subjectId,
                       String eventType, String summary, String sourceType, UUID sourceId,
                       UUID actorId, Instant occurredAt) {
        jdbc.update(
                "INSERT INTO crm_timeline_events (id, tenant_id, subject_type, subject_id, event_type, " +
                "summary, source_type, source_id, occurred_at, created_by) " +
                "VALUES (:id, :tenantId, :subjectType, :subjectId, :eventType, :summary, " +
                ":sourceType, :sourceId, :occurredAt, :createdBy)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("tenantId", tenantId)
                        .addValue("subjectType", subjectType)
                        .addValue("subjectId", subjectId)
                        .addValue("eventType", eventType)
                        .addValue("summary", summary)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
                        .addValue("occurredAt", Timestamp.from(occurredAt))
                        .addValue("createdBy", actorId));
    }

    @Override
    public void record(StructuredTimelineEvent event) {
        String metadataJson = serialiseMetadata(event.metadata());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", event.tenantId())
                .addValue("subjectType", event.subjectType())
                .addValue("subjectId", event.subjectId())
                .addValue("eventType", event.eventType())
                .addValue("summary", event.summary())
                .addValue("sourceType", event.sourceType())
                .addValue("sourceId", event.sourceId())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                .addValue("createdBy", event.actorId())
                .addValue("summaryKey", event.summaryKey())
                .addValue("metadataJson", metadataJson, Types.VARCHAR)
                .addValue("correlationId", event.correlationId())
                .addValue("causationId", event.causationId())
                .addValue("schemaVersion", event.schemaVersion());
        jdbc.update(
                "INSERT INTO crm_timeline_events (id, tenant_id, subject_type, subject_id, event_type, " +
                "summary, source_type, source_id, occurred_at, created_by, " +
                "summary_key, metadata_json, correlation_id, causation_id, schema_version) " +
                "VALUES (:id, :tenantId, :subjectType, :subjectId, :eventType, :summary, " +
                ":sourceType, :sourceId, :occurredAt, :createdBy, " +
                ":summaryKey, :metadataJson, :correlationId, :causationId, :schemaVersion)",
                params);
    }

    private String serialiseMetadata(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize CRM timeline metadata", e);
        }
    }
}
