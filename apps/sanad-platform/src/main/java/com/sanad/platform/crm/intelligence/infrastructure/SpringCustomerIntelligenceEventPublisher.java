package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-backed implementation that delegates to ApplicationEventPublisher.
 * Events are published within the current transaction boundary.
 */
@Component
public class SpringCustomerIntelligenceEventPublisher implements CustomerIntelligenceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringCustomerIntelligenceEventPublisher.class);

    private final ApplicationEventPublisher springPublisher;

    public SpringCustomerIntelligenceEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(CustomerIntelligenceEvent event) {
        try {
            springPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish event {} for tenant {}: {}",
                    event.eventType(), event.tenantId(), e.getMessage(), e);
        }
    }
}
