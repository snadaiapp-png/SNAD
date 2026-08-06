package com.sanad.platform.crm.cases.application;

import com.sanad.platform.crm.cases.domain.CaseRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Case bounded context.
 * <p>
 * Provides the {@link CaseUseCases} bean. The {@link CaseRepository}
 * bean is auto-discovered via {@code @Repository} on the JDBC
 * implementation.
 */
@Configuration
public class CaseModuleConfiguration {

    @Bean
    public CaseUseCases caseUseCases(CaseRepository repo) {
        return new CaseUseCases(repo);
    }
}
