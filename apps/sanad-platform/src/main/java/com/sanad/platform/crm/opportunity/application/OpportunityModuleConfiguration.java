package com.sanad.platform.crm.opportunity.application;

import com.sanad.platform.crm.opportunity.domain.OpportunityRepository;
import com.sanad.platform.crm.opportunity.domain.PipelineRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpportunityModuleConfiguration {
    @Bean
    public OpportunityUseCases opportunityUseCases(OpportunityRepository oppRepo, PipelineRepository pipelineRepo) { return new OpportunityUseCases(oppRepo, pipelineRepo); }
}
