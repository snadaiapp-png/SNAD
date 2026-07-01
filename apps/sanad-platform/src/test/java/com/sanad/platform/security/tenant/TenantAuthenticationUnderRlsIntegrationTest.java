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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.6 §6 — Authentication under RLS with membership enforcement.
 * NO SecurityPermitAllTestConfig. Real JWTs through full filter chain.
 *
 * <p>Stage 04A.3.6.2 — Expanded to cover the five strict-membership
 * scenarios required by CD-04-P1-017 and the real signed expired-JWT
 * scenario required by CD-04-P1-018.</p>
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

    // ============================================================
    // Stage 04A.3.6.2 — Strict membership entity-match scenarios
    // (CD-04-P1-017 closure evidence)
    // ============================================================

    @Test
    @DisplayName("activeMembership_200: User A with ACTIVE membership in Tenant A → 200")
    void activeMembership_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("noMembership_401: User with no membership record → 401")
    void noMembership_401() throws Exception {
        // revokedMembershipUserId has NO membership row at all (only a REVOKED role grant).
        String noMembershipToken = jwtTokenProvider.mintAccessToken(
                fixture.revokedMembershipUserId(), fixture.tenantAId(), "revoked@example.com");
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + noMembershipToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("revokedMembership_401: Membership status = REMOVED → 401")
    void revokedMembership_401() throws Exception {
        // First verify it works
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Revoke membership (status -> REMOVED)
        fixtureSeeder.revokeMembership(fixture.tenantAId(), fixture.userAId());

        // Now should be denied (401 — session validation fails)
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("inactiveMembership_401: Membership status = INACTIVE → 401")
    void inactiveMembership_401() throws Exception {
        // First verify it works
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Set membership status to INACTIVE (admin pause, not removal)
        fixtureSeeder.setMembershipStatus(fixture.tenantAId(), fixture.userAId(), "INACTIVE");

        // Now should be denied — INACTIVE is not ACTIVE
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("membershipInOtherTenant_denied: User actively in Tenant B, JWT claims Tenant A → 401")
    void membershipInOtherTenant_denied() throws Exception {
        // User B has an ACTIVE membership in Tenant B ONLY.
        // Forge a JWT that claims (tenantId=A, userId=userB) using the
        // production signing key. The membership query inside the session
        // validation runs as `findByTenantIdAndUserId(tenantA, userB)`.
        // Under RLS with tenant=A setting, userB (who lives in tenant B) is
        // invisible → 0 rows → hasActiveMembership=false → 401.
        // Even if RLS were bypassed, the membership row for userB has
        // tenant_id=B (not A), so the entity-match predicate
        // `claims.tenantId().equals(membership.getTenantId())` fails → 401.
        String forgedToken = jwtTokenProvider.mintAccessToken(
                fixture.userBId(), fixture.tenantAId(), "bob-b@example.com");

        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + forgedToken))
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

    // ============================================================
    // Stage 04A.3.6.2 — Real signed expired JWT (CD-04-P1-018 closure)
    // ============================================================

    @Test
    @DisplayName("expiredValidlySignedJwt_401: Token signed by production key but exp in the past → 401 SANAD-AUTH-001")
    void expiredValidlySignedJwt_401() throws Exception {
        // Mint a token whose clock is anchored 1 hour in the past. With the
        // default 15-minute access-token TTL, the resulting `exp` claim is
        // already ~45 minutes in the past relative to the running server.
        // The token is signed by the SAME production signing key as a real
        // access token, so this exercises the real JwtAuthenticationFilter →
        // JwtTokenProvider.parseAndValidate expiry path. jjwt rejects the
        // token with ExpiredJwtException → parseAndValidate returns null →
        // filter responds 401 SANAD-AUTH-001.
        Clock pastClock = Clock.fixed(
                Instant.now().minusSeconds(3600),
                ZoneOffset.UTC);
        String expiredToken = jwtTokenProvider.mintAccessTokenWithClock(
                fixture.userAId(),
                fixture.tenantAId(),
                "alice-a@example.com",
                false,
                0L,
                pastClock);

        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));
    }
}
