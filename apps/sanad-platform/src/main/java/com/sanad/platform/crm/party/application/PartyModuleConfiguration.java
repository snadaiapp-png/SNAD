package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.ContactRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Spring configuration for the Party module.
 * Registers use case beans ONLY when their repository adapters exist.
 * Branch: crm/004-modular-domain-architecture
 */
@Configuration
public class PartyModuleConfiguration {

    @Bean
    public AccountUseCases accountUseCases(AccountRepository accountRepository) {
        return new AccountUseCases(accountRepository);
    }

    @Bean
    public ContactUseCases contactUseCases(ContactRepository contactRepository) {
        return new ContactUseCases(contactRepository);
    }
}
