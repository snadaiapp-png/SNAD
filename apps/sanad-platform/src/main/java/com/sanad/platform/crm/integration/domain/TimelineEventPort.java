package com.sanad.platform.crm.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Port for writing timeline events during mutations.
 *
 * <p>Must be called within the same transaction as the mutation.
 *
 * <p>This interface has EXACTLY ONE abstract method — the legacy 9-arg
 * {@code record(...)} SAM. It remains a functional interface so existing
 * lambda assignments continue to compile:
 *
 * <pre>{@code
 * TimelineEventPort port =
 *     (tenant, type, id, event, summary,
 *      source, sourceId, actor, at) -> { };
 * }</pre>
 *
 * <p>The structured path adds a {@code default} method that delegates only
 * legacy-compatible fields to the abstract method. Implementations may
 * override the default to persist structured metadata, correlation id,
 * causation id, and schema version.
 */
public interface TimelineEventPort {

    void record(UUID tenantId, String subjectType, UUID subjectId,
                String eventType, String summary, String sourceType, UUID sourceId,
                UUID actorId, Instant occurredAt);

    /**
     * Structured timeline event envelope.
     *
     * <p>Carries the legacy 9 fields plus the structured SAM extension
     * columns added by V20260822_1 (summary_key, metadata_json,
     * correlation_id, causation_id, schema_version).
     *
     * <p>Required non-null fields: {@code tenantId, subjectType, subjectId,
     * eventType, summary, sourceType, sourceId, actorId, occurredAt,
     * correlationId}.
     *
     * <p>{@code schemaVersion} must be {@code >= 1}.
     *
     * <p>{@code causationId} may be null. {@code summaryKey} may be null.
     * {@code metadata} may be null (the JDBC adapter persists SQL NULL).
     */
    record StructuredTimelineEvent(
            UUID tenantId,
            String subjectType,
            UUID subjectId,
            String eventType,
            String summaryKey,
            String summary,
            String sourceType,
            UUID sourceId,
            UUID actorId,
            Instant occurredAt,
            String correlationId,
            String causationId,
            int schemaVersion,
            JsonNode metadata) {

        public StructuredTimelineEvent {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(subjectType, "subjectType must not be null");
            Objects.requireNonNull(subjectId, "subjectId must not be null");
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(sourceType, "sourceType must not be null");
            Objects.requireNonNull(sourceId, "sourceId must not be null");
            Objects.requireNonNull(actorId, "actorId must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
            if (schemaVersion < 1) {
                throw new IllegalArgumentException(
                        "schemaVersion must be >= 1, got " + schemaVersion);
            }
            // causationId may be null
            // summaryKey may be null
            // metadata may be null
        }
    }

    /**
     * Default structured entry point. Delegates only the legacy-compatible
     * fields to the abstract {@link #record(UUID, String, UUID, String,
     * String, String, UUID, UUID, Instant)} method. summaryKey / metadata /
     * correlationId / causationId / schemaVersion are silently dropped.
     *
     * <p>Implementations that persist structured metadata MUST override this
     * method.
     */
    default void record(StructuredTimelineEvent event) {
        record(event.tenantId(), event.subjectType(), event.subjectId(),
                event.eventType(), event.summary(),
                event.sourceType(), event.sourceId(),
                event.actorId(), event.occurredAt());
    }
}
