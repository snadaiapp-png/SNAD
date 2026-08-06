package com.sanad.platform.crm.intelligence.domain.event;

/**
 * Port for publishing customer intelligence domain events.
 */
public interface CustomerIntelligenceEventPublisher {
    void publish(CustomerIntelligenceEvent event);
}
