package com.sanad.platform.config;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.api.configuration.FlywayConfigurationCustomizer;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly registers the Java V15 migration with Flyway.
 *
 * <p>In Flyway 10+, Java migrations are not auto-discovered from the
 * classpath. Spring Boot's FlywayAutoConfiguration collects
 * {@link JavaMigration} beans and passes them to Flyway via
 * {@code javaMigrations()}. The V15 migration is annotated with
 * {@code @Component} and extends {@link org.flywaydb.core.api.migration.BaseJavaMigration},
 * so it should be picked up automatically — but this explicit
 * configuration acts as a safety net to guarantee discovery in all
 * deployment contexts (Docker, Render, local).</p>
 */
@Configuration
public class FlywayJavaMigrationConfig {

    @Bean
    public JavaMigration v15SeedRbacRolesAndCapabilities() {
        return new V15__seed_rbac_roles_and_capabilities();
    }
}
