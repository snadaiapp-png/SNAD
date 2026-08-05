package com.sanad.platform.crm.reporting.application;

import com.sanad.platform.crm.reporting.domain.ReportRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Reporting module.
 * Wires domain ports to use cases.
 */
@Configuration
public class ReportModuleConfiguration {

    @Bean
    public ReportUseCases reportUseCases(ReportRepository reportRepository) {
        return new ReportUseCases(reportRepository);
    }
}
