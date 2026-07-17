package com.sanad.platform.crm.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.lead.domain.LeadRepository;
import com.sanad.platform.crm.opportunity.application.OpportunityUseCases;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LeadModuleConfiguration {
    @Bean
    public LeadUseCases leadUseCases(LeadRepository leadRepository,
                                     TimelineEventPort timelineEventPort) {
        return new LeadUseCases(leadRepository, timelineEventPort);
    }

    @Bean
    public LeadConversionUseCases leadConversionUseCases(
            LeadUseCases leadUseCases,
            AccountUseCases accountUseCases,
            ContactUseCases contactUseCases,
            OpportunityUseCases opportunityUseCases,
            AuditPort auditPort,
            ObjectMapper objectMapper
    ) {
        return new LeadConversionUseCases(
                leadUseCases,
                accountUseCases,
                contactUseCases,
                opportunityUseCases,
                auditPort,
                objectMapper);
    }
}
