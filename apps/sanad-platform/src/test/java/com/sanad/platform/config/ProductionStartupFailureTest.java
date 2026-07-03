package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot context-level startup failure tests for
 * {@link ProductionDatabaseProperties}.
 *
 * <p>Uses {@link ApplicationContextRunner} with
 * {@link EnableConfigurationProperties} so that Spring Boot's
 * configuration binding + validation pipeline is fully exercised.
 * This proves that the application context fails to start when
 * mandatory database properties are missing in the {@code prod}
 * profile.</p>
 */
class ProductionStartupFailureTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("spring.profiles.active=prod");

    @EnableConfigurationProperties(ProductionDatabaseProperties.class)
    static class TestConfig {
    }

    @Test
    @DisplayName("Context fails to start when DATABASE_URL is blank")
    void contextFails_whenUrlBlank() {
        runner.withPropertyValues(
                "sanad.database.username=user",
                "sanad.database.password=pass"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @Test
    @DisplayName("Context fails to start when DATABASE_USERNAME is blank")
    void contextFails_whenUsernameBlank() {
        runner.withPropertyValues(
                "sanad.database.url=jdbc:postgresql://localhost/db",
                "sanad.database.password=pass"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @Test
    @DisplayName("Context fails to start when DATABASE_PASSWORD is blank")
    void contextFails_whenPasswordBlank() {
        runner.withPropertyValues(
                "sanad.database.url=jdbc:postgresql://localhost/db",
                "sanad.database.username=user"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @Test
    @DisplayName("Context starts successfully when all properties are set")
    void contextStarts_whenAllPropertiesSet() {
        runner.withPropertyValues(
                "sanad.database.url=jdbc:postgresql://localhost/db",
                "sanad.database.username=user",
                "sanad.database.password=pass"
        ).run(context -> assertThat(context).hasNotFailed());
    }
}
