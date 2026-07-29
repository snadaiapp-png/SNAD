package com.sanad.platform.crm.intelligence.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an opportunity score is updated.
 */
public record OpportunityScoreUpdatedEvent(
        UUID tenantId,
        UUID accountId,
        double opportunityScore,
        String opportunityType,
        double estimatedValue,
        Instant occurredAt,
        String correlationId
) implements CustomerIntelligenceEvent {

    @Override
    public String eventType() {
        return "crm.intelligence.opportunity.updated";
    }
}
