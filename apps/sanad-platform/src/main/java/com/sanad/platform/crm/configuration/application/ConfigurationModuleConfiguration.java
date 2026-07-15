package com.sanad.platform.crm.configuration.application;

import com.sanad.platform.crm.configuration.domain.CustomFieldRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigurationModuleConfiguration {
    @Bean
    public ConfigurationUseCases configurationUseCases(CustomFieldRepository repo) { return new ConfigurationUseCases(repo); }
}
