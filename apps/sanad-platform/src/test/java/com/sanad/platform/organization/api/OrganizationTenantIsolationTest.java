package com.sanad.platform.organization.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Dedicated tenant-isolation hardening tests for the Organization REST API.
 *
 * <p>This test class focuses exclusively on verifying that no Organization
 * operation can leak data across tenant boundaries. Every test creates
 * two distinct tenants and confirms that operations scoped to one tenant
 * can never read, modify, or transition the status of an Organization
 * belonging to the other tenant.</p>
 *
 * <h2>What is verified</h2>
 * <ul>
 *   <li>GET by id with wrong tenantId → 404 (not the other tenant's data)</li>
 *   <li>GET list never includes other tenant's organizations</li>
 *   <li>PUT update with wrong tenantId → 404 (cannot rename another tenant's org)</li>
 *   <li>PATCH activate with wrong tenantId → 404 (cannot activate another tenant's org)</li>
 *   <li>PATCH deactivate with wrong tenantId → 404 (cannot deactivate another tenant's org)</li>
 *   <li>PATCH archive with wrong tenantId → 404 (cannot archive another tenant's org)</li>
 *   <li>Same organization name is allowed in different tenants (uniqueness is tenant-scoped)</li>
 *   <li>Organization created under tenant A is invisible to tenant B at the repository level</li>
 * </ul>
 *
 * <h2>Why this matters</h2>
 * <p>These tests are the security contract of the SANAD multi-tenant platform.
 * If any of them fails, it means a tenant can see or modify another tenant's
 * data — a critical security breach. They must never be skipped or weakened.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class OrganizationTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        // Create two distinct tenants
        Tenant tenantA = new Tenant("Tenant A Corp", "tenant-a-" + UUID.randomUUID(),
                TenantStatus.ACTIVE);
        Tenant tenantB = new Tenant("Tenant B Corp", "tenant-b-" + UUID.randomUUID(),
                TenantStatus.ACTIVE);
        tenantAId = tenantRepository.save(tenantA).getId();
        tenantBId = tenantRepository.save(tenantB).getId();

        assertThat(tenantAId).isNotEqualTo(tenantBId);
    }

    // ============================================================
    // Helper: create an organization under a specific tenant
    // ============================================================
    private JsonNode createOrganization(UUID tenantId, String name, String description) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId.toString(),
                "name", name,
                "description", description
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ============================================================
    // TEST 1: GET by id with wrong tenantId → 404
    // ============================================================
    @Test
    @DisplayName("ISOLATION 1: GET by id with wrong tenantId -> 404 (no cross-tenant read)")
    void getById_wrongTenant_returns404() throws Exception {
        // Create org under tenant A
        JsonNode created = createOrganization(tenantAId, "Acme Riyadh", "Branch A");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // Attempt to read it using tenant B's tenantId
        mockMvc.perform(get("/api/v1/organizations/{id}", orgId)
                        .param("tenantId", tenantBId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Organization not found with id")));
    }

    // ============================================================
    // TEST 2: GET list never includes other tenant's organizations
    // ============================================================
    @Test
    @DisplayName("ISOLATION 2: GET list by tenantId never includes other tenant's organizations")
    void listByTenant_doesNotIncludeOtherTenantOrgs() throws Exception {
        // Create orgs under each tenant
        createOrganization(tenantAId, "Org A1", "desc A1");
        createOrganization(tenantAId, "Org A2", "desc A2");
        createOrganization(tenantBId, "Org B1", "desc B1");

        // List orgs for tenant A — should only see A1 and A2, never B1
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantAId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name").value(
                        org.hamcrest.Matchers.containsInAnyOrder("Org A1", "Org A2")))
                .andExpect(jsonPath("$[*].name").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Org B1"))));

        // List orgs for tenant B — should only see B1
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantBId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Org B1"));
    }

    // ============================================================
    // TEST 3: PUT update with wrong tenantId → 404
    // ============================================================
    @Test
    @DisplayName("ISOLATION 3: PUT update with wrong tenantId -> 404 (no cross-tenant modify)")
    void update_wrongTenant_returns404() throws Exception {
        JsonNode created = createOrganization(tenantAId, "Original Name", "desc");
        UUID orgId = UUID.fromString(created.get("id").asText());

        String updatePayload = objectMapper.writeValueAsString(Map.of(
                "name", "Hacked Name",
                "description", "hacked"
        ));

        // Attempt to update tenant A's org using tenant B's tenantId
        mockMvc.perform(put("/api/v1/organizations/{id}", orgId)
                        .param("tenantId", tenantBId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Verify the org name was NOT changed
        var persisted = organizationRepository.findByTenantIdAndId(tenantAId, orgId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Original Name");
        assertThat(persisted.getDescription()).isEqualTo("desc");
    }

    // ============================================================
    // TEST 4: PATCH activate with wrong tenantId → 404
    // ============================================================
    @Test
    @DisplayName("ISOLATION 4: PATCH activate with wrong tenantId -> 404 (no cross-tenant status change)")
    void activate_wrongTenant_returns404() throws Exception {
        // Create org as INACTIVE under tenant A
        JsonNode created = createOrganization(tenantAId, "Inactive Org", "desc");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // Attempt to activate using tenant B's tenantId
        mockMvc.perform(patch("/api/v1/organizations/{id}/activate", orgId)
                        .param("tenantId", tenantBId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============================================================
    // TEST 5: PATCH deactivate with wrong tenantId → 404
    // ============================================================
    @Test
    @DisplayName("ISOLATION 5: PATCH deactivate with wrong tenantId -> 404")
    void deactivate_wrongTenant_returns404() throws Exception {
        JsonNode created = createOrganization(tenantAId, "Active Org", "desc");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // Attempt to deactivate using tenant B's tenantId
        mockMvc.perform(patch("/api/v1/organizations/{id}/deactivate", orgId)
                        .param("tenantId", tenantBId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Verify status was NOT changed (still ACTIVE)
        var persisted = organizationRepository.findByTenantIdAndId(tenantAId, orgId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    // ============================================================
    // TEST 6: PATCH archive with wrong tenantId → 404
    // ============================================================
    @Test
    @DisplayName("ISOLATION 6: PATCH archive with wrong tenantId -> 404 (no cross-tenant soft delete)")
    void archive_wrongTenant_returns404() throws Exception {
        JsonNode created = createOrganization(tenantAId, "To Archive", "desc");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // Attempt to archive using tenant B's tenantId
        mockMvc.perform(patch("/api/v1/organizations/{id}/archive", orgId)
                        .param("tenantId", tenantBId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Verify the org was NOT archived (still ACTIVE)
        var persisted = organizationRepository.findByTenantIdAndId(tenantAId, orgId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    // ============================================================
    // TEST 7: Same organization name allowed in different tenants
    // ============================================================
    @Test
    @DisplayName("ISOLATION 7: Same name allowed in different tenants (uniqueness is tenant-scoped)")
    void sameName_differentTenants_bothSucceed() throws Exception {
        // Create org with name "Shared Name" under tenant A
        createOrganization(tenantAId, "Shared Name", "tenant A's org");

        // Create org with SAME name under tenant B — should NOT get 409
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantId", tenantBId.toString(),
                                "name", "Shared Name",
                                "description", "tenant B's org"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Shared Name"));
    }

    // ============================================================
    // TEST 8: Repository-level isolation (findByTenantIdAndId)
    // ============================================================
    @Test
    @DisplayName("ISOLATION 8: Repository findByTenantIdAndId returns empty for cross-tenant lookup")
    void repository_findByTenantIdAndId_crossTenant_returnsEmpty() throws Exception {
        // Create org under tenant A
        JsonNode created = createOrganization(tenantAId, "Repo Isolation", "desc");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // Repository lookup with correct tenant → present
        assertThat(organizationRepository.findByTenantIdAndId(tenantAId, orgId)).isPresent();

        // Repository lookup with wrong tenant → empty (NOT the other tenant's data)
        assertThat(organizationRepository.findByTenantIdAndId(tenantBId, orgId)).isEmpty();
    }

    // ============================================================
    // TEST 9: Repository-level isolation (findByTenantId list)
    // ============================================================
    @Test
    @DisplayName("ISOLATION 9: Repository findByTenantId only returns own tenant's orgs")
    void repository_findByTenantId_onlyReturnsOwnOrgs() throws Exception {
        createOrganization(tenantAId, "A1", "desc");
        createOrganization(tenantAId, "A2", "desc");
        createOrganization(tenantBId, "B1", "desc");

        // Tenant A sees 2 orgs
        assertThat(organizationRepository.findByTenantId(tenantAId)).hasSize(2);

        // Tenant B sees 1 org
        assertThat(organizationRepository.findByTenantId(tenantBId)).hasSize(1);

        // None of tenant B's org names appear in tenant A's list
        assertThat(organizationRepository.findByTenantId(tenantAId))
                .noneMatch(o -> o.getName().equals("B1"));
    }

    // ============================================================
    // TEST 10: Duplicate name check is tenant-scoped
    // ============================================================
    @Test
    @DisplayName("ISOLATION 10: existsByTenantIdAndName returns false for name in different tenant")
    void repository_existsByTenantIdAndName_crossTenant_returnsFalse() throws Exception {
        // Create org with name "Unique Per Tenant" under tenant A
        createOrganization(tenantAId, "Unique Per Tenant", "desc");

        // The same name under tenant B should NOT be considered a duplicate
        assertThat(organizationRepository.existsByTenantIdAndName(tenantAId, "Unique Per Tenant"))
                .isTrue();
        assertThat(organizationRepository.existsByTenantIdAndName(tenantBId, "Unique Per Tenant"))
                .isFalse();
    }
}
