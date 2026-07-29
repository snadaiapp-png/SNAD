package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new next-best-action recommendation is generated.
 */
public record NextBestActionGeneratedEvent(
        UUID tenantId,
        UUID accountId,
        UUID actionId,
        String actionCode,
        double confidence,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    @Override
    public String eventType() {
        return "crm.intelligence.next_best_action.generated";
    }
}
