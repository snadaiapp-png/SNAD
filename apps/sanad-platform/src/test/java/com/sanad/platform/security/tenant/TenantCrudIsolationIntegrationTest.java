package com.sanad.platform.security.tenant;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
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
 * Stage 04A.3.1 §7 — Cross-tenant CRUD isolation integration tests.
 *
 * <p>Non-skippable PostgreSQL. Uses Fixture DataSource for test data
 * creation (bypasses RLS). HTTP requests use the Runtime DataSource
 * (sanad_runtime_app, subject to RLS) via the full Spring Security +
 * TenantContextFilter + TenantAwareJpaTransactionManager chain.</p>
 *
 * <p>NO @Transactional — each MockMvc request runs in its own
 * application transaction with proper RLS binding.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantCrudIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        // Mint JWTs for both users — these carry the verified tenant_id
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        tokenB = jwtTokenProvider.mintAccessToken(
                fixture.userBId(), fixture.tenantBId(), "bob-b@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    // === Cross-tenant denied ===

    @Test
    @DisplayName("Cross-tenant read: Tenant A user cannot list Tenant B's users (403)")
    void crossTenantRead_users_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    @Test
    @DisplayName("Cross-tenant read: Tenant A user cannot list Tenant B's organizations (403)")
    void crossTenantRead_organizations_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    @Test
    @DisplayName("Cross-tenant write: Tenant A user cannot create a user in Tenant B (403)")
    void crossTenantWrite_user_rejected() throws Exception {
        String body = "{\"email\":\"eve@evil.com\",\"displayName\":\"Eve\"}";
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", fixture.tenantBId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    // === Same-tenant — missing capability (exact 403 + SANAD-SEC-001) ===

    @Test
    @DisplayName("Same-tenant list: 403 (missing USER.READ capability — exact)")
    void sameTenantList_exact403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("Cross-tenant ID: 403 (missing capability — exact, no ambiguity)")
    void crossTenantId_exact403() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", fixture.userBId())
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    // === Missing/invalid parameters ===

    @Test
    @DisplayName("Missing tenantId: 400 (missing required param)")
    void missingTenantId_selector() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid tenantId format: 400 (type mismatch)")
    void invalidTenantIdFormat_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", "not-a-uuid")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    // === Missing authentication ===

    @Test
    @DisplayName("Missing authentication: 401")
    void missingAuthentication_401() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized());
    }
}
