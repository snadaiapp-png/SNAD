package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a customer's health band changes.
 */
public record CustomerHealthChangedEvent(
        UUID tenantId,
        UUID accountId,
        String previousBand,
        String newBand,
        double newValue,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    @Override
    public String eventType() {
        return "crm.intelligence.health.changed";
    }
}
