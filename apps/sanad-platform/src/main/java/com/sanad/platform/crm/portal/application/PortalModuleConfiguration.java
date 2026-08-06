package com.sanad.platform.crm.portal.application;

import com.sanad.platform.crm.portal.domain.PortalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Customer Portal module.
 * Wires domain ports to use cases.
 */
@Configuration
public class PortalModuleConfiguration {

    @Bean
    public PortalUseCases portalUseCases(PortalRepository portalRepository) {
        return new PortalUseCases(portalRepository);
    }
}
