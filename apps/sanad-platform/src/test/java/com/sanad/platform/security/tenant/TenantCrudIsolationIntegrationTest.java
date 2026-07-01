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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.6 §7 — Full CRUD isolation through REAL JWT authentication.
 * NO SecurityPermitAllTestConfig. Full filter chain with RLS enforcement.
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
    private String tokenNoCap;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        tokenA = jwtTokenProvider.mintAccessToken(fixture.userAId(), fixture.tenantAId(), "alice-a@example.com");
        tokenB = jwtTokenProvider.mintAccessToken(fixture.userBId(), fixture.tenantBId(), "bob-b@example.com");
        tokenNoCap = jwtTokenProvider.mintAccessToken(fixture.userWithoutCapabilityId(), fixture.tenantAId(), "nocap@example.com");
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    // === Same-tenant authorized ===

    @Test
    @DisplayName("tokenA + List Tenant A organizations → 200")
    void tokenA_listOrgsA_200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("tokenA + Create organization in A → 201")
    void tokenA_createOrgA_201() throws Exception {
        String body = "{\"name\":\"New Org A\",\"description\":\"test\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("tokenA + Get User A → 200")
    void tokenA_getUserA_200() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", fixture.userAId())
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // === Cross-tenant denied ===

    @Test
    @DisplayName("tokenA + selector B → 403 SANAD-TEN-002")
    void tokenA_selectorB_403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantBId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    @Test
    @DisplayName("tokenA + Get User B through selector A → 404")
    void tokenA_getUserB_404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", fixture.userBId())
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tokenB + selector A → 403 SANAD-TEN-002")
    void tokenB_selectorA_403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-TEN-002"));
    }

    // === Authentication failures ===

    @Test
    @DisplayName("No token → 401 SANAD-AUTH-001")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SANAD-AUTH-001"));
    }

    @Test
    @DisplayName("Malformed token → 401")
    void malformedToken_401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // === Capability enforcement ===

    @Test
    @DisplayName("Token without capability → 403 SANAD-SEC-001")
    void tokenNoCap_403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .header("Authorization", "Bearer " + tokenNoCap))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    // === Mass assignment protection ===

    @Test
    @DisplayName("Mass assignment: body contains tenantId=B with tokenA → created in A not B")
    void massAssignment_tenantIdIgnored() throws Exception {
        String body = "{\"name\":\"Mass Assign Test\",\"description\":\"test\",\"tenantId\":\"" + fixture.tenantBId() + "\"}";
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", fixture.tenantAId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(fixture.tenantAId().toString()));
    }
}
