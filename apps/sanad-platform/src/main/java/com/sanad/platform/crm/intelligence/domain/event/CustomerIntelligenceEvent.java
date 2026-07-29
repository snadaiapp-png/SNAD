package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all customer intelligence domain events.
 * All events are tenant-scoped and carry correlation metadata.
 */
public interface CustomerIntelligenceEvent {
    UUID tenantId();
    UUID accountId();
    String eventType();
    Instant occurredAt();
    String correlationId();
}
