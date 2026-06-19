package com.sanad.platform.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserApiIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUp() {
        tenantId = saveTenant("Acme");
        otherTenantId = saveTenant("Other");
    }

    @Test
    void createUser_persists() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        assertThat(userRepository.findByTenantIdAndId(
                tenantId, UUID.fromString(created.get("id").asText()))).isPresent();
        assertThat(created.get("status").asText()).isEqualTo("INVITED");
    }

    @Test
    void createUser_normalizesEmail() throws Exception {
        JsonNode created = createUser(tenantId, "  Alice@Example.COM  ", "Alice");
        assertThat(created.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(userRepository.findByTenantIdAndEmail(
                tenantId, "alice@example.com")).isPresent();
    }

    @Test
    void duplicateEmail_returns409() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(post("/api/v1/users").param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("ALICE@example.com", "Alice 2")))
                .andExpect(status().isConflict());
    }

    @Test
    void sameEmailDifferentTenants_allowed() throws Exception {
        createUser(tenantId, "shared@example.com", "Shared A");
        JsonNode second = createUser(otherTenantId, "shared@example.com", "Shared B");
        assertThat(second.get("tenantId").asText()).isEqualTo(otherTenantId.toString());
    }

    @Test
    void listUsers_isTenantScoped() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");
        createUser(tenantId, "bob@example.com", "Bob");
        createUser(otherTenantId, "carol@example.com", "Carol");
        mockMvc.perform(get("/api/v1/users").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getUser_isTenantScoped() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void crossTenantGet_returns404() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", otherTenantId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantUpdate_returns404() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(put("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", otherTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("new@example.com", "New")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_persists() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        UUID userId = UUID.fromString(created.get("id").asText());
        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("  NEW@Example.COM ", "Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"));
        User persisted = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
        assertThat(persisted.getDisplayName()).isEqualTo("Updated");
    }

    @Test void activateUser_persistsActive() throws Exception { assertLifecycle("activate", UserStatus.ACTIVE); }
    @Test void deactivateUser_persistsInactive() throws Exception { assertLifecycle("deactivate", UserStatus.INACTIVE); }
    @Test void suspendUser_persistsSuspended() throws Exception { assertLifecycle("suspend", UserStatus.SUSPENDED); }
    @Test void archiveUser_persistsArchived() throws Exception { assertLifecycle("archive", UserStatus.ARCHIVED); }

    @Test
    void validationError_isStructured() throws Exception {
        mockMvc.perform(post("/api/v1/users").param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload("", "Alice")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingTenantId_isStructured() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void openApi_containsUserAndMembershipAssociationOperations() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn();
        JsonNode paths = objectMapper.readTree(
                result.getResponse().getContentAsString()).path("paths");

        assertOperation(paths, "/api/v1/users", "post");
        assertOperation(paths, "/api/v1/users", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "put");
        assertOperation(paths, "/api/v1/users/{userId}/activate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/deactivate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/suspend", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/archive", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/memberships", "get");
        assertOperation(paths, "/api/v1/organizations/{organizationId}/memberships/{membershipId}/assign/{userId}", "patch");
        assertOperation(paths, "/api/v1/organizations/{organizationId}/memberships/{membershipId}/unassign", "patch");
        assertThat(countOperations(paths, "/api/v1/users")).isEqualTo(9);
        assertThat(countOperations(paths, null)).isEqualTo(24);
    }

    private UUID saveTenant(String name) {
        return tenantRepository.save(new Tenant(
                name, name.toLowerCase() + "-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId();
    }

    private JsonNode createUser(UUID scope, String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users").param("tenantId", scope.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload(email, name)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createPayload(String email, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "displayName", name));
    }

    private String updatePayload(String email, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "displayName", name));
    }

    private void assertLifecycle(String operation, UserStatus expected) throws Exception {
        UUID id = UUID.fromString(createUser(tenantId, operation + "@example.com", operation)
                .get("id").asText());
        mockMvc.perform(patch("/api/v1/users/{userId}/" + operation, id)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected.name()));
    }

    private static void assertOperation(JsonNode paths, String path, String method) {
        assertThat(paths.path(path).has(method)).as(method.toUpperCase() + " " + path).isTrue();
    }

    private static long countOperations(JsonNode paths, String prefix) {
        long count = 0;
        Iterator<Map.Entry<String, JsonNode>> entries = paths.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            if (prefix != null && !entry.getKey().startsWith(prefix)) continue;
            Iterator<String> fields = entry.getValue().fieldNames();
            while (fields.hasNext()) if (HTTP_METHODS.contains(fields.next())) count++;
        }
        return count;
    }
}
