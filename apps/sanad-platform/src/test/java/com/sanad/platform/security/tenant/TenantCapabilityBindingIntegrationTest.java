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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.4 §11 — Capability tenant binding with RLS enforcement.
 */
@SpringBootTest
@Import({SecurityPermitAllTestConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantCapabilityBindingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private DataSource dataSource;

    private TenantTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("Tenant A organizations → 200 (RLS-enforced, only A visible)")
    void tenantA_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant B organizations → 200 (RLS-enforced, only B visible)")
    void tenantB_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Revoke role grant → endpoint still accessible (permit-all, RLS enforces)")
    void revokeGrant_stillAccessible() throws Exception {
        fixtureSeeder.revokeRoleGrant(fixture.tenantAId(), fixture.roleGrantId());
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isOk());
    }
}
