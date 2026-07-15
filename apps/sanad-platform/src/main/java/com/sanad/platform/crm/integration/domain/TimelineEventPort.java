package com.sanad.platform.crm.integration.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for writing timeline events during mutations.
 * Must be called within the same transaction as the mutation.
 */
public interface TimelineEventPort {
    void record(UUID tenantId, String subjectType, UUID subjectId,
                String eventType, String summary, String sourceType, UUID sourceId,
                UUID actorId, Instant occurredAt);
}
