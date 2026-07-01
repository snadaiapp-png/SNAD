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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the Organization REST API.
 *
 * <p>Unlike {@link OrganizationControllerTest} (which is a {@code @WebMvcTest}
 * slice test with a mocked service), this test class boots the FULL Spring
 * context with the {@code local} profile, real JPA repositories, real
 * Flyway-migrated H2 database, real service layer, and real controller.
 * Only the HTTP transport is mocked via {@link MockMvc}.</p>
 *
 * <p>This is the closest test to production without going over the network.</p>
 *
 * <h2>What is exercised</h2>
 * <ul>
 *   <li>MockMvc -> OrganizationController (real)</li>
 *   <li>OrganizationController -> OrganizationService (real)</li>
 *   <li>OrganizationService -> OrganizationRepository / TenantRepository (real, JPA)</li>
 *   <li>JPA -> H2 in-memory database (real, Flyway-migrated)</li>
 *   <li>OrganizationApiExceptionHandler (real, translates exceptions to HTTP)</li>
 * </ul>
 *
 * <h2>Test isolation</h2>
 * <p>Each test method runs inside a {@link Transactional @Transactional}
 * boundary that is rolled back at the end, so tests do not pollute each
 * other's state. {@code @BeforeEach} creates a fresh Tenant for each test.</p>
 *
 * <h2>Test cases (10 total — required by EXEC-PROMPT-011)</h2>
 * <ol>
 *   <li>POST valid -> 201, verify persisted row exists</li>
 *   <li>POST duplicate -> 409</li>
 *   <li>GET list -> 200, list contains created organization</li>
 *   <li>GET by id -> 200</li>
 *   <li>PUT update -> 200, verify DB updated</li>
 *   <li>PATCH activate -> 200, verify status ACTIVE</li>
 *   <li>PATCH deactivate -> 200, verify status INACTIVE</li>
 *   <li>PATCH archive -> 200, verify status ARCHIVED</li>
 *   <li>GET with wrong tenantId -> 404 (tenant isolation)</li>
 *   <li>POST invalid payload -> 400</li>
 * </ol>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class OrganizationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // Create a real Tenant row that subsequent API calls can reference
        Tenant tenant = new Tenant("Acme Integration Corp", "acme-integ-" + UUID.randomUUID(),
                TenantStatus.ACTIVE);
        Tenant saved = tenantRepository.save(tenant);
        this.tenantId = saved.getId();
    }

    // ============================================================
    // Helper: build a create-request JSON payload
    // ============================================================
    private String createPayload(String name, String description) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "description", description
        ));
    }

    // ============================================================
    // Helper: POST a new organization and return the parsed response
    // ============================================================
    private JsonNode createOrganization(String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(name, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ============================================================
    // TEST 1 — POST valid -> 201, verify persisted row exists
    // ============================================================
    @Test
    @DisplayName("TEST 1: POST valid request -> 201 Created, row persisted in DB")
    void createOrganization_validRequest_persistsRow() throws Exception {
        // --- Act ---
        JsonNode response = createOrganization("Acme Riyadh", "Main Riyadh branch");

        // --- Assert HTTP response ---
        UUID createdId = UUID.fromString(response.get("id").asText());
        assertThat(response.get("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(response.get("name").asText()).isEqualTo("Acme Riyadh");
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");

        // --- Assert DB persistence ---
        assertThat(organizationRepository.findById(createdId)).isPresent();
        assertThat(organizationRepository.findByTenantIdAndId(tenantId, createdId)).isPresent();
        assertThat(organizationRepository.existsByTenantIdAndName(tenantId, "Acme Riyadh")).isTrue();
    }

    // ============================================================
    // TEST 2 — POST duplicate -> 409 Conflict
    // ============================================================
    @Test
    @DisplayName("TEST 2: POST duplicate organization -> 409 Conflict")
    void createOrganization_duplicate_returns409() throws Exception {
        // --- Arrange: first create succeeds ---
        createOrganization("Dup Name", "First");

        // --- Act + Assert: second create with same name fails ---
        mockMvc.perform(post("/api/v1/organizations")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Dup Name", "Second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }

    // ============================================================
    // TEST 3 — GET list -> 200, list contains created organization
    // ============================================================
    @Test
    @DisplayName("TEST 3: GET list by tenantId -> 200 OK, list contains created organization")
    void listOrganizations_returns200_listContainsCreated() throws Exception {
        // --- Arrange ---
        createOrganization("Org A", "Description A");
        createOrganization("Org B", "Description B");

        // --- Act + Assert ---
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.content[*].name").value(
                        org.hamcrest.Matchers.containsInAnyOrder("Org A", "Org B")));
    }

    // ============================================================
    // TEST 4 — GET by id -> 200 OK
    // ============================================================
    @Test
    @DisplayName("TEST 4: GET by id -> 200 OK")
    void getOrganization_byId_returns200() throws Exception {
        // --- Arrange ---
        JsonNode created = createOrganization("Fetch Me", "Will be fetched");

        // --- Act + Assert ---
        mockMvc.perform(get("/api/v1/organizations/{id}", created.get("id").asText())
                        .param("tenantId", tenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.get("id").asText()))
                .andExpect(jsonPath("$.name").value("Fetch Me"))
                .andExpect(jsonPath("$.description").value("Will be fetched"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ============================================================
    // TEST 5 — PUT update -> 200, verify DB updated
    // ============================================================
    @Test
    @DisplayName("TEST 5: PUT update -> 200 OK, verify DB row updated")
    void updateOrganization_validUpdate_dbRowUpdated() throws Exception {
        // --- Arrange ---
        JsonNode created = createOrganization("Old Name", "Old description");
        UUID orgId = UUID.fromString(created.get("id").asText());

        String updatePayload = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "New Name",
                "description", "New description"
        ));

        // --- Act + Assert HTTP ---
        mockMvc.perform(put("/api/v1/organizations/{id}", orgId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // --- Assert DB persistence ---
        var persisted = organizationRepository.findByTenantIdAndId(tenantId, orgId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("New Name");
        assertThat(persisted.getDescription()).isEqualTo("New description");
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    // ============================================================
    // TEST 6 — PATCH activate -> 200, verify status ACTIVE
    // ============================================================
    @Test
    @DisplayName("TEST 6: PATCH activate -> 200 OK, verify status ACTIVE in DB")
    void activateOrganization_verifyStatusActive() throws Exception {
        // --- Arrange ---
        JsonNode created = createOrganization("To Activate", "x");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // --- Act + Assert HTTP ---
        mockMvc.perform(patch("/api/v1/organizations/{id}/activate", orgId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // --- Assert DB ---
        var persisted = organizationRepository.findByTenantIdAndId(tenantId, orgId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    // ============================================================
    // TEST 7 — PATCH deactivate -> 200, verify status INACTIVE
    // ============================================================
    @Test
    @DisplayName("TEST 7: PATCH deactivate -> 200 OK, verify status INACTIVE in DB")
    void deactivateOrganization_verifyStatusInactive() throws Exception {
        // --- Arrange ---
        JsonNode created = createOrganization("To Deactivate", "x");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // --- Act + Assert HTTP ---
        mockMvc.perform(patch("/api/v1/organizations/{id}/deactivate", orgId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // --- Assert DB ---
        var persisted = organizationRepository.findByTenantIdAndId(tenantId, orgId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
    }

    // ============================================================
    // TEST 8 — PATCH archive -> 200, verify status ARCHIVED
    // ============================================================
    @Test
    @DisplayName("TEST 8: PATCH archive -> 200 OK, verify status ARCHIVED in DB")
    void archiveOrganization_verifyStatusArchived() throws Exception {
        // --- Arrange ---
        JsonNode created = createOrganization("To Archive", "x");
        UUID orgId = UUID.fromString(created.get("id").asText());

        // --- Act + Assert HTTP ---
        mockMvc.perform(patch("/api/v1/organizations/{id}/archive", orgId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // --- Assert DB ---
        var persisted = organizationRepository.findByTenantIdAndId(tenantId, orgId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);

        // --- Assert no hard delete: row still exists in DB (soft delete only) ---
        assertThat(organizationRepository.findById(orgId)).isPresent();
    }

    // ============================================================
    // TEST 9 — GET with wrong tenantId -> 404 (tenant isolation)
    // ============================================================
    @Test
    @DisplayName("TEST 9: GET with wrong tenantId -> 404 Not Found (tenant isolation)")
    void getOrganization_wrongTenant_returns404() throws Exception {
        // --- Arrange: create org under tenantId, then try to fetch with a different tenant ---
        JsonNode created = createOrganization("Isolation Test", "x");
        UUID orgId = UUID.fromString(created.get("id").asText());
        UUID wrongTenantId = UUID.randomUUID();

        // --- Act + Assert ---
        mockMvc.perform(get("/api/v1/organizations/{id}", orgId)
                        .param("tenantId", wrongTenantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    // ============================================================
    // TEST 10 — POST invalid payload -> 400 Bad Request
    // ============================================================
    @Test
    @DisplayName("TEST 10: POST invalid payload -> 400 Bad Request")
    void createOrganization_invalidPayload_returns400() throws Exception {
        // Missing tenantId, blank name, description too long
        String tooLongDescription = "x".repeat(1001);
        String invalidPayload = objectMapper.writeValueAsString(java.util.Map.of(
                "description", tooLongDescription
        ));

        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/organizations"));
    }
}
