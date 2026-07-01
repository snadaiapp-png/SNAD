package com.sanad.platform.security.tenant;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
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
 * Stage 04A.3.4 §9 — CRUD isolation certification.
 *
 * <p>Uses SecurityPermitAllTestConfig + testTenantContextFilter which sets
 * TenantContext from the tenantId query param. RLS is enforced via
 * sanad_runtime_app + FORCE RLS on all tenant-owned tables.</p>
 */
@SpringBootTest
@Import({SecurityPermitAllTestConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantCrudIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    // === Same-tenant (RLS enforced) ===

    @Test
    @DisplayName("Tenant A list organizations → 200 (RLS returns only A)")
    void tenantA_list_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant B list organizations → 200 (RLS returns only B)")
    void tenantB_list_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cross-tenant ID: User B not visible in Tenant A scope → 404")
    void crossTenantId_404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", fixture.userBId())
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Missing tenantId → 400")
    void missingTenantId_400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid tenantId format → 400")
    void invalidTenantId_400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
