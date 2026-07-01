package com.sanad.platform.security.tenant;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.4 §11 — Capability tenant binding with real DB grants.
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantCapabilityBindingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private String tokenA;
    private String tokenB;
    private String tokenNoCap;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        tokenB = jwtTokenProvider.mintAccessToken(
                fixture.userBId(), fixture.tenantBId(), "bob-b@example.com");
        tokenNoCap = jwtTokenProvider.mintAccessToken(
                fixture.userWithoutCapabilityId(), fixture.tenantAId(), "nocap@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("Token A + Tenant A (has USER.READ) → 200")
    void tokenA_tenantA_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token A + Tenant B (no capability in B) → 403 SANAD-TEN-002")
    void tokenA_tenantB_403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    @Test
    @DisplayName("Token without capability → 403 SANAD-SEC-001")
    void tokenNoCap_403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenNoCap))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("Revoke USER.READ grant → Token A → 403 SANAD-SEC-001")
    void revokeGrant_then403() throws Exception {
        // First verify it works
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Revoke the grant
        fixtureSeeder.revokeRoleGrant(fixture.tenantAId(), fixture.roleGrantId());

        // Now should be denied
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("Token B + Tenant B (independent capability) → 200")
    void tokenB_tenantB_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }
}
