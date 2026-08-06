package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a customer score is calculated or refreshed.
 */
public record CustomerScoreCalculatedEvent(
        UUID tenantId,
        UUID accountId,
        String scoreType,
        double scoreValue,
        String scoreBand,
        Double previousValue,
        double delta,
        String triggerReason,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    @Override
    public String eventType() {
        return "crm.intelligence.score.calculated";
    }

    public boolean hasChanged() {
        return previousValue != null && Math.abs(delta) > 0.001;
    }
}
