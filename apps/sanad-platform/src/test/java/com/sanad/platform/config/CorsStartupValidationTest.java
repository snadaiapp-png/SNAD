package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring Boot context-level startup validation tests for {@link CorsProperties}.
 *
 * <p>Uses {@link ApplicationContextRunner} with
 * {@link EnableConfigurationProperties} so that Spring Boot's
 * configuration binding pipeline is fully exercised. The custom
 * validation in {@code validateAndParse()} is invoked explicitly
 * because it runs in {@code @PostConstruct} of SecurityConfig, which
 * is not loaded in this minimal test context.</p>
 *
 * <p>Test scenarios:</p>
 * <ul>
 *   <li>Production valid: HTTPS origin → context starts, validation passes</li>
 *   <li>Production missing: no origin → validation fails</li>
 *   <li>Production empty: empty string → validation fails</li>
 *   <li>Production wildcard: https://*.vercel.app → validation fails</li>
 *   <li>Production localhost: http://localhost:3000 → validation fails</li>
 *   <li>Development localhost: http://localhost:3000 → validation passes</li>
 *   <li>Test profile isolation: no production env vars needed</li>
 * </ul>
 */
class CorsStartupValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsTestConfig.class);

    @EnableConfigurationProperties(CorsProperties.class)
    static class CorsTestConfig {
    }

    // ─── Production Valid Configuration ─────────────────────────

    @Nested
    @DisplayName("Production: valid configuration")
    class ProductionValid {

        @Test
        @DisplayName("Context starts and validation passes with valid HTTPS origin")
        void contextStarts_withValidProdOrigin() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=https://snad-app.vercel.app"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThat(props.getAllowedOrigins()).isEqualTo("https://snad-app.vercel.app");
                // Production mode validation should pass
                props.validateAndParse(true);
                assertThat(props.getParsedOrigins()).containsExactly("https://snad-app.vercel.app");
            });
        }
    }

    // ─── Production Missing Configuration ───────────────────────

    @Nested
    @DisplayName("Production: missing configuration")
    class ProductionMissing {

        @Test
        @DisplayName("Validation fails when CORS origins are absent in production mode")
        void validationFails_whenProdOriginsMissing() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins="
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("must not be empty in production");
            });
        }
    }

    // ─── Production Empty Configuration ─────────────────────────

    @Nested
    @DisplayName("Production: empty configuration")
    class ProductionEmpty {

        @Test
        @DisplayName("Validation fails when CORS origins are empty in production mode")
        void validationFails_whenProdOriginsEmpty() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins="
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("must not be empty in production");
            });
        }
    }

    // ─── Production Wildcard ────────────────────────────────────

    @Nested
    @DisplayName("Production: wildcard rejection")
    class ProductionWildcard {

        @Test
        @DisplayName("Validation fails when CORS origin contains wildcard")
        void validationFails_whenWildcardOrigin() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=https://snad-*.vercel.app"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("wildcard");
            });
        }

        @Test
        @DisplayName("Validation fails when CORS origin contains *.vercel.app wildcard")
        void validationFails_whenVercelWildcard() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=https://*.vercel.app"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("wildcard");
            });
        }
    }

    // ─── Production Localhost ───────────────────────────────────

    @Nested
    @DisplayName("Production: localhost rejection")
    class ProductionLocalhost {

        @Test
        @DisplayName("Validation fails when CORS origin is HTTP localhost in production mode")
        void validationFails_whenLocalhostInProd() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=http://localhost:3000"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("http scheme in production");
            });
        }

        @Test
        @DisplayName("Validation fails when CORS origin is HTTP 127.0.0.1 in production mode")
        void validationFails_when127InProd() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=http://127.0.0.1:3000"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                assertThatThrownBy(() -> props.validateAndParse(true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("http scheme in production");
            });
        }
    }

    // ─── Development Localhost ──────────────────────────────────

    @Nested
    @DisplayName("Development: localhost acceptance")
    class DevelopmentLocalhost {

        @Test
        @DisplayName("Validation passes with HTTP localhost in development mode")
        void validationPasses_withLocalhostInDev() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=http://localhost:3000"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                props.validateAndParse(false);
                assertThat(props.getParsedOrigins()).containsExactly("http://localhost:3000");
            });
        }
    }

    // ─── Test Profile Isolation ─────────────────────────────────

    @Nested
    @DisplayName("Test profile isolation")
    class TestProfileIsolation {

        @Test
        @DisplayName("Tests do not depend on production environment variables")
        void testProfileIsolation() {
            // The runner uses its own property values; no production
            // environment variables are required.
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=http://localhost:3000"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                // Validate in non-production mode — should not fail
                props.validateAndParse(false);
                assertThat(props.getParsedOrigins()).containsExactly("http://localhost:3000");
            });
        }

        @Test
        @DisplayName("Default empty config is valid in non-production mode")
        void defaultEmptyConfigValidInNonProd() {
            // No sanad.cors.allowed-origins set — the ApplicationContextRunner
            // provides a clean context. The default value is empty, which is
            // valid in non-production mode.
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                props.validateAndParse(false);
                assertThat(props.getParsedOrigins()).isEmpty();
            });
        }
    }

    // ─── Property Binding Verification ──────────────────────────

    @Nested
    @DisplayName("Property binding verification")
    class PropertyBindingTests {

        @Test
        @DisplayName("CorsProperties bean is registered and injectable via @ConfigurationPropertiesScan")
        void corsPropertiesBeanRegistered() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=https://snad-app.vercel.app"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(CorsProperties.class)).isNotNull();
            });
        }

        @Test
        @DisplayName("Multiple origins bind and validate correctly")
        void multipleOriginsBind() {
            runner.withPropertyValues(
                    "sanad.cors.allowed-origins=https://snad-app.vercel.app,https://app.sanad.example:8443"
            ).run(context -> {
                assertThat(context).hasNotFailed();
                CorsProperties props = context.getBean(CorsProperties.class);
                props.validateAndParse(true);
                assertThat(props.getParsedOrigins()).containsExactly(
                        "https://snad-app.vercel.app",
                        "https://app.sanad.example:8443");
            });
        }
    }
}
