package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.AccountHierarchyPort;
import com.sanad.platform.crm.party.domain.AccountMasterRepository;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PartyModuleConfiguration {

    @Bean
    public AccountUseCases accountUseCases(
            AccountRepository accountRepository,
            AccountHierarchyPort hierarchyPort,
            OwnerValidationPort ownerValidationPort,
            AccountMasterRepository accountMasterRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new AccountUseCases(
                accountRepository,
                hierarchyPort,
                ownerValidationPort,
                accountMasterRepository,
                auditPort,
                timelineEventPort,
                objectMapper);
    }

    @Bean
    public AccountMasterUseCases accountMasterUseCases(
            AccountRepository accountRepository,
            AccountMasterRepository accountMasterRepository,
            AuditPort auditPort,
            TimelineEventPort timelineEventPort,
            ObjectMapper objectMapper) {
        return new AccountMasterUseCases(
                accountRepository,
                accountMasterRepository,
                auditPort,
                timelineEventPort,
                objectMapper);
    }

    @Bean
    public ContactUseCases contactUseCases(
            ContactRepository contactRepository,
            TimelineEventPort timelineEventPort) {
        return new ContactUseCases(contactRepository, timelineEventPort);
    }
}
