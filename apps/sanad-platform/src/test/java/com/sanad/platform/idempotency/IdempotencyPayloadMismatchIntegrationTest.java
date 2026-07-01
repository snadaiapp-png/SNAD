package com.sanad.platform.idempotency;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §16 — Verifies HTTP-level idempotency conflict semantics on
 * {@code POST /api/v1/organizations}.
 *
 * <p>Stage 05A.1 §13 — All HTTP requests use real JWTs through MockMvc.
 * No direct {@link com.sanad.platform.idempotency.service.IdempotencyService}
 * calls — the test exercises the full filter chain including the
 * {@link IdempotencyCommandInterceptor}.</p>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header.</p>
 *
 * <p>Verification:</p>
 * <ul>
 *   <li>POST with key K + body B → 201 Created.</li>
 *   <li>POST with the SAME key K + DIFFERENT body B2 → 409 Conflict with
 *       {@code code=SANAD-IDEMP-002} (payload mismatch).</li>
 * </ul>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyPayloadMismatchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private String tokenA;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("sameKeyDifferentPayload_returns409: POST with K+B → 201; POST with K+B2 → 409 SANAD-IDEMP-002")
    void sameKeyDifferentPayload_returns409() throws Exception {
        String key = "mismatch-key-" + UUID.randomUUID();
        String body1 = "{\"name\":\"Mismatch Org A " + UUID.randomUUID() + "\",\"description\":\"first payload\"}";
        String body2 = "{\"name\":\"Mismatch Org B " + UUID.randomUUID() + "\",\"description\":\"DIFFERENT payload\"}";

        // 1. First POST with key K + body B1 → 201
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated());

        // 2. Second POST with the SAME key K + DIFFERENT body B2 → 409 SANAD-IDEMP-002
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANAD-IDEMP-002"));
    }
}
