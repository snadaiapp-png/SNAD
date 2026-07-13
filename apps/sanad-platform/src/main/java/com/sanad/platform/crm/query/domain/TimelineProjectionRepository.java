package com.sanad.platform.crm.query.domain;

import java.util.List;
import java.util.UUID;

/**
 * Read-only repository port for timeline projection queries.
 * Lives in crm.query — never performs writes.
 */
public interface TimelineProjectionRepository {
    List<TimelineEvent> findBySubject(UUID tenantId, String subjectType, UUID subjectId, int limit);

    record TimelineEvent(UUID id, String subjectType, UUID subjectId, String eventType,
            String summary, String sourceType, UUID sourceId,
            java.time.Instant occurredAt, UUID createdBy) {}
}
