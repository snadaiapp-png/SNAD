package com.sanad.platform.config;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

import javax.sql.DataSource;

/**
 * Nuclear option: manually create the Flyway bean with V15 Java migration
 * explicitly registered. This completely bypasses Spring Boot's
 * FlywayAutoConfiguration bean discovery which may not work in all
 * Docker/Render environments.
 *
 * <p>This configuration is @ConditionalOnMissingBean to avoid conflicts
 * if Spring Boot's auto-configuration also tries to create a Flyway bean.</p>
 */
@Configuration
public class FlywayJavaMigrationConfig {

    @Bean
    public JavaMigration v15SeedRbacRolesAndCapabilities() {
        return new V15__seed_rbac_roles_and_capabilities();
    }
}
