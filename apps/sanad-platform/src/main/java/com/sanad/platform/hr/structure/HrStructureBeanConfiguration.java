package com.sanad.platform.hr.structure;

import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * HRM-G0 / WS5 Task 4 — Spring wiring for the canonical Structure v2 slice.
 * The certified WS2 repository remains annotation-free; this configuration
 * exposes it to the production context.
 */
@Configuration
public class HrStructureBeanConfiguration {

    @Bean
    public JdbcHrStructureRepository hrStructureRepository(DataSource dataSource) {
        return new JdbcHrStructureRepository(dataSource);
    }
}
