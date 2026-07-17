package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PartyModuleConfiguration {

    @Bean
    public AccountUseCases accountUseCases(AccountRepository accountRepository,
                                           AccountHierarchyPort hierarchyPort,
                                           OwnerValidationPort ownerValidationPort,
                                           AuditPort auditPort,
                                           TimelineEventPort timelineEventPort,
                                           ObjectMapper objectMapper) {
        return new AccountUseCases(accountRepository, hierarchyPort, ownerValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ContactUseCases contactUseCases(ContactRepository contactRepository,
                                           AuditPort auditPort,
                                           TimelineEventPort timelineEventPort,
                                           ObjectMapper objectMapper) {
        return new ContactUseCases(contactRepository, auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public ContactRelationshipUseCases contactRelationshipUseCases(
            ContactRelationshipRepository contactRelationshipRepository,
            OwnerValidationPort ownerValidationPort,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new ContactRelationshipUseCases(contactRelationshipRepository, ownerValidationPort,
                auditPort, timelineEventPort, objectMapper);
    }

    @Bean
    public CustomerMasterUseCases customerMasterUseCases(
            CustomerMasterRepository customerMasterRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new CustomerMasterUseCases(customerMasterRepository, auditPort, timelineEventPort, objectMapper);
    }
}
