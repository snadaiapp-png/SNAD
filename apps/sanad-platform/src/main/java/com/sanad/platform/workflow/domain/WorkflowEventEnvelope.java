package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow event envelope (design decision X3). Carries the durable identity
 * required for at-least-once delivery with idempotent consumption; duplicate
 * delivery of the same eventId/trigger/definition can never create a second
 * instance.
 */
public record WorkflowEventEnvelope(
        UUID eventId,
        String eventType,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        int schemaVersion,
        String payloadJson
) {
    public WorkflowEventEnvelope {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
    }
}
