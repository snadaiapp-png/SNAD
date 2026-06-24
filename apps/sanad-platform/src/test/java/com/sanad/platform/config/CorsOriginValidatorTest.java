package com.sanad.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Standalone unit tests for {@link CorsProperties} validation logic.
 *
 * <p>Tests the {@code validateAndParse()} method directly without
 * Spring context, covering accept/reject/normalization rules for
 * CORS origin allowlist entries.</p>
 */
class CorsOriginValidatorTest {

    // ─── Helper ─────────────────────────────────────────────────

    private CorsProperties createProperties(String allowedOrigins) {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(allowedOrigins);
        return props;
    }

    private List<String> validateProduction(String allowedOrigins) {
        CorsProperties props = createProperties(allowedOrigins);
        props.validateAndParse(true);
        return props.getParsedOrigins();
    }

    private List<String> validateDevelopment(String allowedOrigins) {
        CorsProperties props = createProperties(allowedOrigins);
        props.validateAndParse(false);
        return props.getParsedOrigins();
    }

    // ─── Accepted Origins ───────────────────────────────────────

    @Nested
    @DisplayName("Accept valid origins")
    class AcceptTests {

        @Test
        @DisplayName("Accept exact production origin")
        void acceptExactProductionOrigin() {
            List<String> result = validateProduction("https://snad-app.vercel.app");
            assertThat(result).containsExactly("https://snad-app.vercel.app");
        }

        @Test
        @DisplayName("Accept custom domain origin")
        void acceptCustomDomain() {
            List<String> result = validateProduction("https://app.sanad.example");
            assertThat(result).containsExactly("https://app.sanad.example");
        }

        @Test
        @DisplayName("Accept origin with explicit port")
        void acceptExplicitPort() {
            List<String> result = validateProduction("https://app.sanad.example:8443");
            assertThat(result).containsExactly("https://app.sanad.example:8443");
        }

        @Test
        @DisplayName("Accept HTTP localhost in development mode")
        void acceptLocalhostInDev() {
            List<String> result = validateDevelopment("http://localhost:3000");
            assertThat(result).containsExactly("http://localhost:3000");
        }

        @Test
        @DisplayName("Accept HTTP 127.0.0.1 in development mode")
        void accept127InDev() {
            List<String> result = validateDevelopment("http://127.0.0.1:3000");
            assertThat(result).containsExactly("http://127.0.0.1:3000");
        }

        @Test
        @DisplayName("Accept multiple comma-separated origins")
        void acceptMultipleOrigins() {
            List<String> result = validateProduction(
                    "https://snad-app.vercel.app,https://app.sanad.example");
            assertThat(result).containsExactly(
                    "https://snad-app.vercel.app",
                    "https://app.sanad.example");
        }
    }

    // ─── Rejected Origins ───────────────────────────────────────

    @Nested
    @DisplayName("Reject invalid origins")
    class RejectTests {

        @Test
        @DisplayName("Reject bare wildcard *")
        void rejectBareWildcard() {
            assertThatThrownBy(() -> validateProduction("*"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
        }

        @Test
        @DisplayName("Reject https://* wildcard")
        void rejectHttpsWildcard() {
            assertThatThrownBy(() -> validateProduction("https://*"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
        }

        @Test
        @DisplayName("Reject https://*.vercel.app wildcard")
        void rejectVercelWildcard() {
            assertThatThrownBy(() -> validateProduction("https://*.vercel.app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
        }

        @Test
        @DisplayName("Reject https://snad-*.vercel.app wildcard")
        void rejectSnadWildcard() {
            assertThatThrownBy(() -> validateProduction("https://snad-*.vercel.app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
        }

        @Test
        @DisplayName("Reject origin with path")
        void rejectPath() {
            assertThatThrownBy(() -> validateProduction("https://snad-app.vercel.app/path"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("path");
        }

        @Test
        @DisplayName("Reject origin with query string")
        void rejectQuery() {
            assertThatThrownBy(() -> validateProduction("https://snad-app.vercel.app?x=1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("query");
        }

        @Test
        @DisplayName("Reject origin with fragment")
        void rejectFragment() {
            assertThatThrownBy(() -> validateProduction("https://snad-app.vercel.app#fragment"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("fragment");
        }

        @Test
        @DisplayName("Reject origin with user credentials")
        void rejectCredentials() {
            assertThatThrownBy(() -> validateProduction(
                    "https://user:password@snad-app.vercel.app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("credentials");
        }

        @Test
        @DisplayName("Reject javascript: scheme")
        void rejectJavascriptScheme() {
            assertThatThrownBy(() -> validateProduction("javascript://example.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("scheme");
        }

        @Test
        @DisplayName("Reject ftp: scheme")
        void rejectFtpScheme() {
            assertThatThrownBy(() -> validateProduction("ftp://example.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("scheme");
        }

        @Test
        @DisplayName("Reject file: scheme")
        void rejectFileScheme() {
            assertThatThrownBy(() -> validateDevelopment("file://example"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("scheme");
        }

        @Test
        @DisplayName("Reject not-a-url")
        void rejectNotAUrl() {
            assertThatThrownBy(() -> validateProduction("not-a-url"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Reject scheme-only https://")
        void rejectSchemeOnly() {
            assertThatThrownBy(() -> validateProduction("https://"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Reject scheme-relative //example.com")
        void rejectSchemeRelative() {
            assertThatThrownBy(() -> validateProduction("//example.com"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Reject bare hostname without scheme")
        void rejectBareHostname() {
            assertThatThrownBy(() -> validateProduction("snad-app.vercel.app"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Reject empty string")
        void rejectEmpty() {
            assertThatThrownBy(() -> validateProduction(""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be empty in production");
        }

        @Test
        @DisplayName("Reject whitespace-only string")
        void rejectWhitespace() {
            assertThatThrownBy(() -> validateProduction("   "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be empty in production");
        }

        @Test
        @DisplayName("Reject HTTP origin in production mode")
        void rejectHttpInProduction() {
            assertThatThrownBy(() -> validateProduction("http://localhost:3000"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("http scheme in production");
        }

        @Test
        @DisplayName("Reject missing production config (null)")
        void rejectNullInProduction() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOrigins(null);
            assertThatThrownBy(() -> props.validateAndParse(true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be empty in production");
        }
    }

    // ─── Normalization Tests ────────────────────────────────────

    @Nested
    @DisplayName("Normalize origin values")
    class NormalizationTests {

        @Test
        @DisplayName("Trim whitespace around origins")
        void trimWhitespace() {
            List<String> result = validateProduction("  https://snad-app.vercel.app  ");
            assertThat(result).containsExactly("https://snad-app.vercel.app");
        }

        @Test
        @DisplayName("Eliminate duplicate origins")
        void eliminateDuplicates() {
            assertThatThrownBy(() -> validateProduction(
                    "https://snad-app.vercel.app,https://snad-app.vercel.app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("Strip trailing slash from origin")
        void stripTrailingSlash() {
            List<String> result = validateProduction("https://snad-app.vercel.app/");
            assertThat(result).containsExactly("https://snad-app.vercel.app");
        }

        @Test
        @DisplayName("Preserve explicit port after trailing slash removal")
        void preserveExplicitPort() {
            List<String> result = validateProduction("https://app.sanad.example:8443/");
            assertThat(result).containsExactly("https://app.sanad.example:8443");
        }

        @Test
        @DisplayName("Do NOT accept a path disguised as trailing slash + segment")
        void rejectPathAfterSlash() {
            assertThatThrownBy(() -> validateProduction("https://snad-app.vercel.app/api"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("path");
        }

        @Test
        @DisplayName("Comma-separated with whitespace is parsed correctly")
        void commaSeparatedWithWhitespace() {
            List<String> result = validateProduction(
                    "https://snad-app.vercel.app , https://app.sanad.example:8443");
            assertThat(result).containsExactly(
                    "https://snad-app.vercel.app",
                    "https://app.sanad.example:8443");
        }

        @Test
        @DisplayName("Empty entries between commas are filtered out")
        void emptyEntriesFiltered() {
            List<String> result = validateProduction(
                    "https://snad-app.vercel.app,,https://app.sanad.example");
            assertThat(result).containsExactly(
                    "https://snad-app.vercel.app",
                    "https://app.sanad.example");
        }
    }

    // ─── Development Mode ───────────────────────────────────────

    @Nested
    @DisplayName("Development mode relaxations")
    class DevelopmentModeTests {

        @Test
        @DisplayName("Allow HTTP localhost in development")
        void allowHttpLocalhost() {
            List<String> result = validateDevelopment("http://localhost:3000");
            assertThat(result).containsExactly("http://localhost:3000");
        }

        @Test
        @DisplayName("Allow HTTP 127.0.0.1 in development")
        void allowHttp127() {
            List<String> result = validateDevelopment("http://127.0.0.1:3000");
            assertThat(result).containsExactly("http://127.0.0.1:3000");
        }

        @Test
        @DisplayName("Empty origins allowed in non-production mode")
        void emptyOriginsAllowedInDev() {
            List<String> result = validateDevelopment("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Wildcard still rejected in development mode")
        void wildcardRejectedInDev() {
            assertThatThrownBy(() -> validateDevelopment("https://*.vercel.app"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
        }
    }
}
