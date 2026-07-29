package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a customer is added to or removed from a segment.
 */
public record CustomerSegmentChangedEvent(
        UUID tenantId,
        UUID accountId,
        UUID segmentId,
        String segmentCode,
        String changeType,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    public static final String CHANGE_ADDED = "ADDED";
    public static final String CHANGE_REMOVED = "REMOVED";

    @Override
    public String eventType() {
        return "crm.intelligence.segment.changed";
    }
}
