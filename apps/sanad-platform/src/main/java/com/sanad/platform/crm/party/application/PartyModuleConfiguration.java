package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.party.domain.*;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PartyModuleConfiguration {

    @Bean
    public AccountUseCases accountUseCases(AccountRepository accountRepository,
                                           AccountHierarchyPort hierarchyPort,
                                           OwnerValidationPort ownerValidationPort,
                                           AuditPort auditPort,
                                           TimelineEventPort timelineEventPort) {
        return new AccountUseCases(accountRepository, hierarchyPort, ownerValidationPort, auditPort, timelineEventPort);
    }

    @Bean
    public ContactUseCases contactUseCases(ContactRepository contactRepository) {
        return new ContactUseCases(contactRepository);
    }
}
