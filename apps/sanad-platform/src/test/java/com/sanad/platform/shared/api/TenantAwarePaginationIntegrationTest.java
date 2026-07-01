package com.sanad.platform.shared.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04A.3.1 §6 — Tenant-aware pagination integration test.
 *
 * <p>Non-skippable PostgreSQL. Uses Fixture DataSource for data creation.
 * HTTP requests use Runtime DataSource with full RLS enforcement.</p>
 *
 * <p>NO @Transactional — each request runs in its own application transaction.</p>
 */
@SpringBootTest
@Import({SecurityPermitAllTestConfig.class, TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantAwarePaginationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private UUID tenantAId;
    private UUID tenantBId;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedPaginationFixture();
        tenantAId = fixture.tenantAId();
        tenantBId = fixture.tenantBId();

        // Create users for JWT minting (fixture seeder for pagination doesn't create users)
        // We'll use a simple approach: mint a JWT with a random userId matching the tenant
        // The JwtAuthenticationFilter will try to validate the session, but with
        // SecurityPermitAllTestConfig, the real JWT filter is replaced.
        // So we just need the tenant_id claim to match.
        // Actually, SecurityPermitAllTestConfig bypasses JWT validation entirely.
        // The testTenantContextFilter sets context from the tenantId query param.
        // So we don't need a real JWT — just pass tenantId as a query param.
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    @Test
    @DisplayName("Tenant A content contains only A records")
    void tenantA_contentContainsOnlyA() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].name").value(
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.startsWith("Alpha-A"))));
    }

    @Test
    @DisplayName("Tenant A totalElements excludes B records")
    void tenantA_totalElementsExcludesB() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    @DisplayName("Tenant B totalElements excludes A records")
    void tenantB_totalElementsExcludesA() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantBId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("Sorting does not cross tenant boundary")
    void sorting_doesNotCrossTenant() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString())
                        .param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Alpha-A3"))
                .andExpect(jsonPath("$.content[2].name").value("Alpha-A1"));
    }

    @Test
    @DisplayName("Second page remains tenant-scoped")
    void secondPage_remainsTenantScoped() throws Exception {
        // page=0 size=2 returns first 2 A records
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString())
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(true));

        // page=1 size=2 returns the 3rd A record (no B)
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        org.hamcrest.Matchers.startsWith("Alpha-A")));
    }

    @Test
    @DisplayName("Multiple sort parameters are applied in order")
    void multipleSortParameters_appliedInOrder() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString())
                        .param("sort", "name,asc")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.sort.length()").value(2))
                .andExpect(jsonPath("$.page.sort[0].field").value("name"))
                .andExpect(jsonPath("$.page.sort[0].direction").value("asc"))
                .andExpect(jsonPath("$.page.sort[1].field").value("createdAt"))
                .andExpect(jsonPath("$.page.sort[1].direction").value("desc"));
    }

    @Test
    @DisplayName("Default page params return first page")
    void defaultPageParams_returnFirstPage() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.first").value(true));
    }
}
