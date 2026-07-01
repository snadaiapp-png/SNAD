package com.sanad.platform.shared.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 03A §15 — Tenant-aware pagination integration tests.
 *
 * <p>Verifies that paginated collection endpoints:</p>
 * <ul>
 *   <li>Return only the requesting tenant's records in {@code $.content}</li>
 *   <li>Compute {@code totalElements} from a tenant-scoped count query</li>
 *   <li>Do not cross tenant boundaries when sorting</li>
 *   <li>Reject oversized page sizes</li>
 *   <li>Reject invalid sort fields with SANAD-PAG-002</li>
 * </ul>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantAwarePaginationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private OrganizationRepository organizationRepository;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() throws Exception {
        // Create tenants (tenants table has no RLS — SECURITY_GLOBAL)
        Tenant tenantA = new Tenant("Tenant A", "slug-a-" + UUID.randomUUID(), TenantStatus.ACTIVE);
        tenantAId = tenantRepository.save(tenantA).getId();
        Tenant tenantB = new Tenant("Tenant B", "slug-b-" + UUID.randomUUID(), TenantStatus.ACTIVE);
        tenantBId = tenantRepository.save(tenantB).getId();

        // Stage 04A.3: No @Transactional on test class — each HTTP request
        // creates its own transaction. The testTenantContextFilter sets the
        // TenantContext from the tenantId query param, and the
        // TenantAwareJpaTransactionManager.doBegin() sets RLS on the
        // transaction's connection. This ensures each INSERT is RLS-scoped.
        createOrg(tenantAId, "Alpha-A1");
        createOrg(tenantAId, "Alpha-A2");
        createOrg(tenantAId, "Alpha-A3");
        createOrg(tenantBId, "Bravo-B1");
        createOrg(tenantBId, "Bravo-B2");
    }

    private void createOrg(UUID tenantId, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "description", "seed"));
        mockMvc.perform(post("/api/v1/organizations")
                .param("tenantId", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
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
    @DisplayName("Oversized page size is rejected")
    void oversizedPageSize_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                .param("tenantId", tenantAId.toString())
                .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid sort field is rejected with SANAD-PAG-002")
    void invalidSortField_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                .param("tenantId", tenantAId.toString())
                .param("sort", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SANAD-PAG-002"));
    }

    @Test
    @DisplayName("Invalid sort direction is rejected")
    void invalidSortDirection_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                .param("tenantId", tenantAId.toString())
                .param("sort", "name,sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Multiple sort parameters are applied in order")
    void multipleSortParameters_appliedInOrder() throws Exception {
        // Sort by name asc, then createdAt desc — both valid fields
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
