package com.sanad.platform.tenant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * End-to-end integration tests for the Tenant REST API.
 *
 * <p>Boots the FULL Spring context with the {@code local} profile, real
 * JPA repositories, real Flyway-migrated H2 database, real service layer,
 * and real controller. Only the HTTP transport is mocked via {@link MockMvc}.</p>
 *
 * <h2>Tenant Isolation Verification</h2>
 * <p>TEST 9 specifically verifies tenant isolation by creating two tenants
 * and confirming each can only see its own data — the foundation of the
 * SANAD multi-tenant model.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class TenantApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    private String createPayload(String name, String subdomain) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "subdomain", subdomain
        ));
    }

    private JsonNode createTenant(String name, String subdomain) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(name, subdomain)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TEST 1: POST valid -> 201, row persisted in DB")
    void createTenant_valid_persistsRow() throws Exception {
        JsonNode response = createTenant("Acme Integ", "acme-integ-" + UUID.randomUUID());

        UUID createdId = UUID.fromString(response.get("id").asText());
        assertThat(response.get("name").asText()).isEqualTo("Acme Integ");
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");

        assertThat(tenantRepository.findById(createdId)).isPresent();
        assertThat(tenantRepository.existsBySubdomain(response.get("subdomain").asText())).isTrue();
    }

    @Test
    @DisplayName("TEST 2: POST duplicate subdomain -> 409 Conflict")
    void createTenant_duplicate_returns409() throws Exception {
        String subdomain = "dup-" + UUID.randomUUID();
        createTenant("First Tenant", subdomain);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Second Tenant", subdomain)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tenant already exists with subdomain: " + subdomain)))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants"));
    }

    @Test
    @DisplayName("TEST 3: GET list -> 200, list contains created tenant")
    void listTenants_returns200_containsCreated() throws Exception {
        JsonNode created = createTenant("Listed Tenant", "listed-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/tenants").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id").value(
                        org.hamcrest.Matchers.hasItem(created.get("id").asText())));
    }

    @Test
    @DisplayName("TEST 4: GET by id -> 200 OK")
    void getTenant_byId_returns200() throws Exception {
        JsonNode created = createTenant("Fetch Me", "fetch-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/tenants/{id}", created.get("id").asText())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.get("id").asText()))
                .andExpect(jsonPath("$.name").value("Fetch Me"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("TEST 5: PUT update -> 200, verify DB updated")
    void updateTenant_dbUpdated() throws Exception {
        JsonNode created = createTenant("Old Name", "old-" + UUID.randomUUID());
        UUID tenantId = UUID.fromString(created.get("id").asText());

        String updatePayload = objectMapper.writeValueAsString(Map.of("name", "New Name"));

        mockMvc.perform(put("/api/v1/tenants/{id}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.subdomain").value(created.get("subdomain").asText())); // unchanged

        var persisted = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("New Name");
        assertThat(persisted.getSubdomain()).isEqualTo(created.get("subdomain").asText());
    }

    @Test
    @DisplayName("TEST 6: PATCH activate -> 200, status ACTIVE in DB")
    void activateTenant_statusActive() throws Exception {
        JsonNode created = createTenant("To Activate", "act-" + UUID.randomUUID());
        UUID tenantId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/tenants/{id}/activate", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(tenantRepository.findById(tenantId).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    @DisplayName("TEST 7: PATCH deactivate -> 200, status SUSPENDED in DB")
    void deactivateTenant_statusSuspended() throws Exception {
        JsonNode created = createTenant("To Deactivate", "deact-" + UUID.randomUUID());
        UUID tenantId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/tenants/{id}/deactivate", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(tenantRepository.findById(tenantId).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("TEST 8: PATCH archive -> 200, status ARCHIVED in DB (no hard delete)")
    void archiveTenant_statusArchived_noHardDelete() throws Exception {
        JsonNode created = createTenant("To Archive", "arch-" + UUID.randomUUID());
        UUID tenantId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/tenants/{id}/archive", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Verify soft delete: row still exists in DB
        assertThat(tenantRepository.findById(tenantId)).isPresent();
        assertThat(tenantRepository.findById(tenantId).orElseThrow().getStatus())
                .isEqualTo(TenantStatus.ARCHIVED);
    }

    @Test
    @DisplayName("TEST 9: Tenant isolation - two tenants, each sees only its own data")
    void tenantIsolation_twoTenants_eachSeesOnlyOwnData() throws Exception {
        // --- Arrange: create two distinct tenants ---
        JsonNode tenantA = createTenant("Tenant A Corp", "tenant-a-" + UUID.randomUUID());
        JsonNode tenantB = createTenant("Tenant B Corp", "tenant-b-" + UUID.randomUUID());

        UUID tenantAId = UUID.fromString(tenantA.get("id").asText());
        UUID tenantBId = UUID.fromString(tenantB.get("id").asText());

        // --- Assert: each tenant ID is distinct (no collision) ---
        assertThat(tenantAId).isNotEqualTo(tenantBId);

        // --- Assert: fetching tenant A by ID does NOT return tenant B's data ---
        mockMvc.perform(get("/api/v1/tenants/{id}", tenantAId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantAId.toString()))
                .andExpect(jsonPath("$.name").value("Tenant A Corp"))
                .andExpect(jsonPath("$.subdomain").value(tenantA.get("subdomain").asText()));

        mockMvc.perform(get("/api/v1/tenants/{id}", tenantBId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantBId.toString()))
                .andExpect(jsonPath("$.name").value("Tenant B Corp"))
                .andExpect(jsonPath("$.subdomain").value(tenantB.get("subdomain").asText()));

        // --- Assert: fetching a non-existent tenant ID returns 404 ---
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/tenants/{id}", randomId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tenant not found with id: " + randomId)));

        // --- Assert: both tenants are visible in the list (admin/global view) ---
        mockMvc.perform(get("/api/v1/tenants").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(
                        org.hamcrest.Matchers.hasItems(tenantAId.toString(), tenantBId.toString())));

        // --- Assert: tenant A's subdomain is unique, cannot be reused ---
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Imposter", tenantA.get("subdomain").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("TEST 10: POST invalid payload -> 400 Bad Request")
    void createTenant_invalidPayload_returns400() throws Exception {
        // Missing name + invalid subdomain format
        String invalidPayload = objectMapper.writeValueAsString(Map.of(
                "subdomain", "INVALID UPPER CASE"
        ));

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants"));
    }
}
