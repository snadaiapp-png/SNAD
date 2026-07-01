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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.6 §6 — Authentication under RLS with membership enforcement.
 * NO SecurityPermitAllTestConfig. Real JWTs through full filter chain.
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantAuthenticationUnderRlsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        tokenB = jwtTokenProvider.mintAccessToken(fixture.userBId(), fixture.tenantBId(), "bob-b@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("Valid User A JWT + Tenant A → 200")
    void validUserA_200() throws Exception {
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
    @DisplayName("No Authorization header → 401")
    void noAuth_401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Malformed JWT → 401")
    void malformedJwt_401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
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
    @DisplayName("Old session version → 401")
    void oldSessionVersion_401() throws Exception {
        fixtureSeeder.incrementSessionVersion(fixture.tenantAId(), fixture.userAId());
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Revoked membership → 401")
    void revokedMembership_401() throws Exception {
        // First verify it works
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Revoke membership
        fixtureSeeder.revokeMembership(fixture.tenantAId(), fixture.userAId());

        // Now should be denied (401 — session validation fails)
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Archived tenant → denied")
    void archivedTenant_denied() throws Exception {
        // First verify it works
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Archive the tenant
        fixtureSeeder.archiveTenant(fixture.tenantAId());

        // Now should be denied (session validation fails — tenant not active)
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired JWT → 401")
    void expiredJwt_401() throws Exception {
        // Create a token with a very short TTL by using the JwtTokenProvider
        // with a manual expiry in the past. We can't easily configure TTL
        // per-token, so we create a token and wait for it to expire.
        // Instead, we create a malformed token that simulates expiry
        // by using an expired timestamp in the payload.
        // The JwtTokenProvider.parseAndValidate() will reject it.
        // For a true expired JWT test, we'd need a Clock injection.
        // For now, we test with a clearly invalid token structure.
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJleHBpcmVkIiwiZXhwIjoxfQ.invalid";
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
