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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §22 — HTTP-level idempotency contract on
 * {@code POST /api/v1/organizations}.
 *
 * <p>Stage 05A.1 §13 — All requests carry a real JWT through MockMvc. The
 * {@code TenantContextFilter} establishes a verified TenantContext from the
 * JWT claims, and the {@link IdempotencyCommandInterceptor} enforces the
 * idempotency contract on the annotated {@code createOrganization} method.</p>
 *
 * <p>Three contract tests:</p>
 * <ol>
 *   <li>{@code missingKey_returns400} — POST without an {@code Idempotency-Key}
 *       header returns HTTP 400 with {@code code=SANAD-IDEMP-001}.</li>
 *   <li>{@code sameKeySamePayload_replaysResponse} — POST with key K and body
 *       B returns 201; a second POST with the same K and B returns 201 with
 *       an {@code Idempotency-Replayed: true} header and the same
 *       organization ID in the body.</li>
 *   <li>{@code sameKeyDifferentPayload_returns409} — POST with key K and body
 *       B returns 201; a second POST with the same K but a different body B2
 *       returns 409 with {@code code=SANAD-IDEMP-002}.</li>
 * </ol>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyHttpContractIntegrationTest {

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
    @DisplayName("missingKey_returns400: POST /api/v1/organizations without Idempotency-Key → 400 SANAD-IDEMP-001")
    void missingKey_returns400() throws Exception {
        String body = "{\"name\":\"No Key Org\",\"description\":\"contract test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SANAD-IDEMP-001"));
    }

    @Test
    @DisplayName("sameKeySamePayload_replaysResponse: POST with K+B → 201; POST with K+B → 201 Idempotency-Replayed=true, same org id")
    void sameKeySamePayload_replaysResponse() throws Exception {
        String key = "http-contract-replay-" + UUID.randomUUID();
        String uniqueName = "Replay Org " + UUID.randomUUID();
        String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"replay test\"}";

        // First request → 201
        String firstResponse = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstOrgId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(firstResponse))
                .get("id").asText();

        // Second request with the same key + same payload → 201 with Idempotency-Replayed: true
        String secondResponse = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyCommandInterceptor.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andReturn().getResponse().getContentAsString();

        String secondOrgId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondResponse))
                .get("id").asText();

        // The replayed response must reference the SAME organization ID
        org.assertj.core.api.Assertions.assertThat(secondOrgId)
                .as("replayed response must reference the same organization id as the first response")
                .isEqualTo(firstOrgId);
    }

    @Test
    @DisplayName("sameKeyDifferentPayload_returns409: POST with K+B → 201; POST with K+B2 → 409 SANAD-IDEMP-002")
    void sameKeyDifferentPayload_returns409() throws Exception {
        String key = "http-contract-conflict-" + UUID.randomUUID();
        String body1 = "{\"name\":\"Conflict Org A " + UUID.randomUUID() + "\",\"description\":\"first payload\"}";
        String body2 = "{\"name\":\"Conflict Org B " + UUID.randomUUID() + "\",\"description\":\"DIFFERENT payload\"}";

        // First request with body1 → 201
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated());

        // Second request with the SAME key but DIFFERENT body → 409 SANAD-IDEMP-002
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANAD-IDEMP-002"));
    }
}
