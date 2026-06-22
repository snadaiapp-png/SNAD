package com.sanad.platform;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Test-classpath customization for the PostgreSQL row-lock fixture only. */
@Configuration
public class TestJpaSchemaConfig {

    @Bean
    HibernatePropertiesCustomizer refreshLockSchemaCustomizer(Environment environment) {
        return properties -> {
            String url = environment.getProperty("spring.datasource.url", "");
            if (url.contains("sanad_refresh_lock")) {
                properties.put("hibernate.hbm2ddl.auto", "none");
                properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            }
        };
    }
}
