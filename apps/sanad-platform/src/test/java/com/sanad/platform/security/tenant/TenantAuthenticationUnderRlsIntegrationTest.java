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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.4 §10 — Authentication under RLS with real MockMvc.
 */
@SpringBootTest
@Import({com.sanad.platform.security.SecurityPermitAllTestConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantAuthenticationUnderRlsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;
    @Autowired private javax.sql.DataSource dataSource;

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
    @DisplayName("Valid User A JWT + Tenant A → 200 (reaches controller)")
    void validUserA_200() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valid User B JWT + Tenant B → 200")
    void validUserB_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown user ID → 401")
    void unknownUser_401() throws Exception {
        String unknownToken = jwtTokenProvider.mintAccessToken(
                UUID.randomUUID(), fixture.tenantAId(), "unknown@example.com");
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + unknownToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Suspended user → 401")
    void suspendedUser_401() throws Exception {
        String suspendedToken = jwtTokenProvider.mintAccessToken(
                fixture.suspendedUserId(), fixture.tenantAId(), "suspended@example.com");
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + suspendedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Stored session version incremented → old JWT → 401")
    void oldSessionVersion_401() throws Exception {
        // Increment session version in DB
        fixtureSeeder.incrementSessionVersion(fixture.tenantAId(), fixture.userAId());
        // Old token (session_version=0) should now fail
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Malformed JWT → 401")
    void malformedJwt_401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT with non-existent tenant → 401")
    void nonexistentTenant_401() throws Exception {
        String badTenantToken = jwtTokenProvider.mintAccessToken(
                fixture.userAId(), UUID.randomUUID(), "alice-a@example.com");
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + badTenantToken))
                .andExpect(status().isUnauthorized());
    }

}
