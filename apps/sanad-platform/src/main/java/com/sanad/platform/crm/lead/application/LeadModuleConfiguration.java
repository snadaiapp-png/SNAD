package com.sanad.platform.crm.lead.application;

import com.sanad.platform.crm.lead.domain.LeadRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LeadModuleConfiguration {
    @Bean
    public LeadUseCases leadUseCases(LeadRepository leadRepository) { return new LeadUseCases(leadRepository); }
}
