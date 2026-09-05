package com.sanad.platform.hr.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * A claimed HR domain event handed to registered in-process consumers by the
 * {@link HrOutboxWorker} (WS4 Task 6).
 *
 * <p>Delivery semantics are AT_LEAST_ONCE; consumers MUST be idempotent. The
 * payload is the already-redacted JSON persisted in {@code hr_domain_event_outbox}
 * (raw restricted payloads cannot exist there — DB CHECK + central redaction
 * guard enforce it at publish time).</p>
 */
public record HrOutboxEvent(
        UUID eventId,
        UUID tenantId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        UUID organizationId,
        UUID actorUserId,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String idempotencyKey,
        String dataClassification,
        String payload) {
}
