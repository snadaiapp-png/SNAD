package com.sanad.platform.audit;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §9 — Verifies that denied requests (authentication failures,
 * authorization denials) produce the correct HTTP 401/403 responses with
 * the correct error codes through the real JWT filter chain.
 *
 * <p>Stage 05A.1 §13 — All HTTP requests use real JWTs through MockMvc.
 * The {@code JwtAuthenticationFilter} rejects unauthenticated requests
 * with 401 SANAD-AUTH-001; the {@code CapabilityAuthorizationAspect}
 * rejects unauthorized requests with 403 SANAD-SEC-001. No manual
 * {@link com.sanad.platform.audit.service.AuditService#recordDenied}
 * calls are made — the denial flow is exercised purely via HTTP.</p>
 *
 * <p>Stage 05A.1 §22 — POST requests carry an {@code Idempotency-Key}
 * header (required by the {@code @IdempotentOperation} annotation on
 * {@code createOrganization}). The 401 case omits the JWT (and thus the
 * Idempotency-Key) to verify the auth filter fires first; the 403 case
 * includes both.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditDeniedRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private String tokenNoCap;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenNoCap = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(),
                "nocap@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("authFailure_401: GET /organizations without JWT → 401 SANAD-AUTH-001 (no manual recordDenied call)")
    void authFailure_401() throws Exception {
        // Trigger a 401 by omitting the Authorization header. The
        // JwtAuthenticationFilter (or Spring Security's
        // authenticationEntryPoint) writes the 401 response with
        // SANAD-AUTH-001.
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));
    }

    @Test
    @DisplayName("authorizationDenied_403: GET /organizations with token lacking capability → 403 SANAD-SEC-001")
    void authorizationDenied_403() throws Exception {
        // Trigger a 403 by using a token without ORGANIZATION.READ capability.
        // The CapabilityAuthorizationAspect writes the 403 response with
        // SANAD-SEC-001.
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenNoCap))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("deniedPost_401BeforeIdempotency: POST /organizations without JWT → 401 SANAD-AUTH-001 (auth fires before idempotency)")
    void deniedPost_401BeforeIdempotency() throws Exception {
        // The JwtAuthenticationFilter fires before the
        // IdempotencyCommandInterceptor (filters run before interceptors).
        // A POST without a JWT returns 401 SANAD-AUTH-001, regardless of
        // whether the Idempotency-Key header is present.
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"denied-post\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));
    }
}
