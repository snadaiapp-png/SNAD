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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 05A.1 §16 — Verifies HTTP-level cross-tenant idempotency isolation
 * on {@code POST /api/v1/organizations}.
 *
 * <p>Stage 05A.1 §13 — All HTTP requests use real JWTs through MockMvc.
 * Tenant A and Tenant B each use their own JWT.</p>
 *
 * <p>Stage 05A.1 §22 — Each POST carries an {@code Idempotency-Key} header.</p>
 *
 * <p>Verification:</p>
 * <ul>
 *   <li>Tenant A POSTs with key K → 201.</li>
 *   <li>Tenant B POSTs with the SAME key K → 201 (independent record,
 *       not a replay).</li>
 *   <li>Two independent idempotency records exist in the DB for the same
 *       key, one per tenant.</li>
 * </ul>
 *
 * <p>All DB reads use {@link PreparedStatement}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyCrossTenantIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    private TenantTestFixture fixture;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        tokenB = jwtTokenProvider.mintAccessToken(
                fixture.userBId(), fixture.tenantBId(), "bob-b@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("sameKeyDifferentTenants_independent: key K in Tenant A and Tenant B → two independent records")
    void sameKeyDifferentTenants_independent() throws Exception {
        String sharedKey = "cross-tenant-key-" + UUID.randomUUID();
        String bodyA = "{\"name\":\"Cross Tenant A " + UUID.randomUUID() + "\",\"description\":\"a\"}";
        String bodyB = "{\"name\":\"Cross Tenant B " + UUID.randomUUID() + "\",\"description\":\"b\"}";

        // 1. Tenant A POSTs with key K → 201
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyA)
                        .header("Authorization", "Bearer " + tokenA)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, sharedKey))
                .andExpect(status().isCreated());

        // 2. Tenant B POSTs with the SAME key K → 201 (independent, not replay)
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyB)
                        .header("Authorization", "Bearer " + tokenB)
                        .header(IdempotencyCommandInterceptor.IDEMPOTENCY_KEY_HEADER, sharedKey))
                .andExpect(status().isCreated());

        // 3. Two independent records exist for the same key.
        String countSql = "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setString(1, sharedKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                assertThat(count)
                        .as("two independent records must exist for the same key across tenants")
                        .isEqualTo(2);
            }
        }

        // 4. The two records have different tenant IDs.
        String tenantSql = "SELECT tenant_id FROM idempotency_records WHERE idempotency_key = ?";
        try (Connection conn = fixtureDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(tenantSql)) {
            ps.setString(1, sharedKey);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.Set<UUID> tenantIds = new java.util.HashSet<>();
                while (rs.next()) {
                    tenantIds.add((UUID) rs.getObject("tenant_id"));
                }
                assertThat(tenantIds)
                        .as("the two records must belong to different tenants")
                        .containsExactlyInAnyOrder(fixture.tenantAId(), fixture.tenantBId());
            }
        }
    }
}
