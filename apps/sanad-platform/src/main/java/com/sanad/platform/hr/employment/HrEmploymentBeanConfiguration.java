package com.sanad.platform.hr.employment;

import com.sanad.platform.hr.audit.HrTransactionalEvidenceWriter;
import com.sanad.platform.hr.identity.HrPersonRepository;
import com.sanad.platform.hr.identity.JdbcHrPersonRepository;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * HRM-G0 / WS5 Task 3 — Spring wiring for the canonical employment
 * aggregate, previously constructed manually by WS2 integration tests.
 *
 * <p>The certified WS2 classes remain annotation-free (plain, final,
 * constructor-injected); this configuration exposes them to the production
 * context without touching their source. The two-arg repository variant
 * (transactional evidence writer) is chosen so employment lifecycle
 * transitions keep appending atomic audit evidence exactly as the WS4
 * atomicity suite proved.
 */
@Configuration
public class HrEmploymentBeanConfiguration {

    @Bean
    public EmploymentRepository employmentRepository(DataSource dataSource,
                                                     HrTransactionalEvidenceWriter evidenceWriter) {
        return new JdbcEmploymentRepository(dataSource, evidenceWriter);
    }

    @Bean
    public EmploymentCommandService employmentCommandService(EmploymentRepository employmentRepository) {
        return new JdbcEmploymentCommandService(employmentRepository);
    }

    @Bean
    public HrPersonRepository hrPersonRepository(DataSource dataSource,
                                                 PlatformCryptographyService cryptographyService) {
        return new JdbcHrPersonRepository(dataSource, cryptographyService);
    }
}
