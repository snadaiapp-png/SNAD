package com.sanad.platform.idempotency;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantRuntimeDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §16 — Verifies HTTP-level idempotency replay semantics on
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
 *   <li>POST again with the SAME K + B → 201 Created with
 *       {@code Idempotency-Replayed: true} header, same organization ID in
 *       the body, and exactly 1 idempotency record in the DB.</li>
 * </ul>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantRuntimeDataSourceConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencySameRequestReplayIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

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
    @DisplayName("sameKeySamePayload_replaysResponse: POST with K+B → 201; POST with K+B → 201 Idempotency-Replayed=true, same org id")
    void sameKeySamePayload_replaysResponse() throws Exception {
        String key = "replay-key-" + UUID.randomUUID();
        String uniqueName = "Replay Org " + UUID.randomUUID();
        String body = "{\"name\":\"" + uniqueName + "\",\"description\":\"replay test\"}";

        // 1. First POST with key K + body B → 201
        String firstResponse = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstOrgId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(firstResponse))
                .get("id").asText();

        // 2. Second POST with the SAME key + body → 201 with Idempotency-Replayed: true
        String secondResponse = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyCommandInterceptor.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andReturn().getResponse().getContentAsString();

        String secondOrgId = com.fasterxml.jackson.databind.JsonNode.class.cast(
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondResponse))
                .get("id").asText();

        // 3. The replayed response must reference the SAME organization ID.
        assertThat(secondOrgId)
                .as("replayed response must reference the same organization id as the first response")
                .isEqualTo(firstOrgId);

        // 4. Verify exactly 1 idempotency record exists for this key.
        String sql = "SELECT COUNT(*) FROM idempotency_records "
                + "WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fixture.tenantAId());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                assertThat(count)
                        .as("exactly one idempotency record must exist for the replayed key")
                        .isEqualTo(1);
            }
        }
    }
}
