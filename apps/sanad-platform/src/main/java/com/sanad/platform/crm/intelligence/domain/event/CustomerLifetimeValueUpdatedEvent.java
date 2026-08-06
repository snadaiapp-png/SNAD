package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when customer lifetime value is recalculated.
 */
public record CustomerLifetimeValueUpdatedEvent(
        UUID tenantId,
        UUID accountId,
        double predictedValue,
        String tier,
        double confidence,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    @Override
    public String eventType() {
        return "crm.intelligence.lifetime_value.updated";
    }
}
