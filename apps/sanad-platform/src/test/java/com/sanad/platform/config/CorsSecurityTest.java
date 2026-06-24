package com.sanad.platform.config;

import com.sanad.platform.config.CorsTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for CORS exact-origin allowlist security.
 *
 * <p>Verifies that Spring Security's CORS filter correctly enforces
 * the exact-origin allowlist configured via {@link CorsProperties}.
 * Only explicitly configured origins are accepted in the CORS
 * preflight response. Wildcard, attacker, lookalike, and
 * unauthorized origins are rejected.</p>
 *
 * <p>Uses {@code @ActiveProfiles("local")} with H2 in-memory database
 * so tests run without external dependencies.</p>
 */
@SpringBootTest
@Import(CorsTestSecurityConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CorsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // ─── Approved Origin Preflight ───────────────────────────────

    @Nested
    @DisplayName("Approved origin preflight")
    class ApprovedOriginTests {

        @Test
        @DisplayName("CORS: approved exact origin receives Allow-Origin header")
        void cors_allowedOrigin() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Access-Control-Allow-Origin"))
                    .andExpect(header().string(
                            "Access-Control-Allow-Origin", "https://snad-app.vercel.app"));
        }

        @Test
        @DisplayName("CORS: approved origin receives Allow-Credentials = true")
        void cors_approvedOrigin_credentials() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        @Test
        @DisplayName("CORS: response never contains wildcard * in Allow-Origin")
        void cors_noWildcardOriginHeader() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            "Access-Control-Allow-Origin", org.hamcrest.Matchers.not("*")));
        }

        @Test
        @DisplayName("CORS: allowed methods include GET, POST, PUT, PATCH, DELETE, OPTIONS")
        void cors_allowedMethods() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            "Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("GET")))
                    .andExpect(header().string(
                            "Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("POST")))
                    .andExpect(header().string(
                            "Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("PUT")))
                    .andExpect(header().string(
                            "Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("PATCH")))
                    .andExpect(header().string(
                            "Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("DELETE")));
        }
    }

    // ─── Attacker Vercel Origin ─────────────────────────────────

    @Nested
    @DisplayName("Attacker Vercel origin rejection")
    class AttackerVercelTests {

        @Test
        @DisplayName("CORS: attacker Vercel subdomain is rejected")
        void cors_attackerVercelOrigin() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-attacker.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null || !allowOrigin.contains("snad-attacker")
                                : "Attacker Vercel origin should not be allowed";
                    });
        }
    }

    // ─── Lookalike Origins ──────────────────────────────────────

    @Nested
    @DisplayName("Lookalike origin rejection")
    class LookalikeOriginTests {

        @Test
        @DisplayName("CORS: snad-app-malicious.vercel.app is rejected (prefix attack)")
        void cors_maliciousPrefixAttack() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app-malicious.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null || !allowOrigin.contains("snad-app-malicious")
                                : "Similar-prefix origin should not be allowed";
                    });
        }

        @Test
        @DisplayName("CORS: malicious-snad-app.vercel.app is rejected (suffix attack)")
        void cors_suffixAttack() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://malicious-snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null || !allowOrigin.contains("malicious-snad-app")
                                : "Suffix attack origin should not be allowed";
                    });
        }

        @Test
        @DisplayName("CORS: snad-app.vercel.app.attacker.example is rejected (domain append)")
        void cors_domainAppendAttack() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app.attacker.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null
                                : "Domain-append attack should not be allowed";
                    });
        }

        @Test
        @DisplayName("CORS: HTTP scheme on approved host is rejected")
        void cors_wrongScheme() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "http://snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null
                                : "HTTP scheme should not be allowed for production origin";
                    });
        }

        @Test
        @DisplayName("CORS: different port on approved host is rejected")
        void cors_wrongPort() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://snad-app.vercel.app:444")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null
                                : "Different port should not be allowed";
                    });
        }

        @Test
        @DisplayName("CORS: subdomain of approved origin is rejected")
        void cors_subdomainRejected() throws Exception {
            mockMvc.perform(options("/api/v1/organizations")
                            .header("Origin", "https://sub.snad-app.vercel.app")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(result -> {
                        String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                        assert allowOrigin == null || !allowOrigin.contains("sub.snad-app")
                                : "Subdomain should not be allowed unless explicitly configured";
                    });
        }
    }

    // ─── Unrelated Vercel Project ───────────────────────────────

    @Test
    @DisplayName("CORS: unrelated Vercel project is rejected")
    void cors_unrelatedVercelOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/organizations")
                        .header("Origin", "https://unrelated-project.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(result -> {
                    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
                    assert allowOrigin == null || !allowOrigin.contains("unrelated-project")
                            : "Unrelated Vercel origin should not be allowed";
                });
    }

    // ─── Generic Disallowed Origin ──────────────────────────────

    @Test
    @DisplayName("CORS: completely disallowed origin is rejected")
    void cors_disallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/organizations")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ─── Actuator Routes Not CORS-Enabled ───────────────────────

    @Test
    @DisplayName("CORS: actuator routes are not CORS-enabled")
    void cors_actuatorNotEnabled() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", "https://snad-app.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
