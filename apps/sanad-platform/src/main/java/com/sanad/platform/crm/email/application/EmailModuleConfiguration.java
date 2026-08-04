package com.sanad.platform.crm.email.application;

import com.sanad.platform.crm.email.domain.EmailLogPort;
import com.sanad.platform.crm.email.domain.EmailPort;
import com.sanad.platform.crm.email.domain.EmailTemplatePort;
import com.sanad.platform.crm.email.infrastructure.EmailProperties;
import com.sanad.platform.crm.email.web.EmailTrackingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the CRM email bounded context.
 * <p>
 * Follows the same pattern as {@code CaseModuleConfiguration}:
 * a single {@code @Bean} factory method that instantiates
 * {@code EmailUseCases} with the port interfaces.
 * <p>
 * The JDBC implementation of {@code EmailLogPort} is auto-discovered
 * via {@code @Repository}. The {@code EmailPort} implementation is
 * auto-discovered via {@code @ConditionalOnProperty}.
 */
@Configuration
@EnableConfigurationProperties({EmailProperties.class, EmailTrackingProperties.class})
public class EmailModuleConfiguration {

    @Bean
    EmailUseCases emailUseCases(
            EmailPort emailPort,
            EmailTemplatePort templatePort,
            EmailLogPort logPort,
            EmailProperties properties
    ) {
        return new EmailUseCases(emailPort, templatePort, logPort, properties);
    }
}
