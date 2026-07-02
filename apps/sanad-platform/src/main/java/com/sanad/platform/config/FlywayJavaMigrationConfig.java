package com.sanad.platform.config;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.api.configuration.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly registers the Java V15 migration with Flyway via
 * FlywayConfigurationCustomizer — the official Spring Boot extension
 * point for customizing Flyway configuration.
 *
 * <p>This bypasses any bean discovery or classpath scanning issues
 * that may occur in Docker/Render environments where Flyway 11.x
 * with flyway-database-postgresql might not auto-discover Java
 * migrations.</p>
 */
@Configuration
public class FlywayJavaMigrationConfig {

    @Bean
    public FlywayConfigurationCustomizer flywayJavaMigrationCustomizer() {
        return configuration -> configuration.javaMigrations(
                new V15__seed_rbac_roles_and_capabilities());
    }
}
