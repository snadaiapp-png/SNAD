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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserApiIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUp() {
        tenantId = saveTenant("Acme");
        otherTenantId = saveTenant("Other");
    }

    @Test
    @DisplayName("TEST 1: POST creates and persists user")
    void createUser_persists() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        UUID userId = UUID.fromString(created.get("id").asText());

        assertThat(userRepository.findByTenantIdAndId(tenantId, userId)).isPresent();
        assertThat(created.get("status").asText()).isEqualTo("INVITED");
    }

    @Test
    @DisplayName("TEST 2: API normalizes email")
    void createUser_normalizesEmail() throws Exception {
        JsonNode created = createUser(tenantId, "  Alice@Example.COM  ", "Alice");

        assertThat(created.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(userRepository.findByTenantIdAndEmail(tenantId, "alice@example.com")).isPresent();
    }

    @Test
    @DisplayName("TEST 3: duplicate email in tenant returns 409")
    void duplicateEmail_returns409() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");

        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("ALICE@example.com", "Alice 2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("TEST 4: same email in different tenants is allowed")
    void sameEmailDifferentTenants_allowed() throws Exception {
        createUser(tenantId, "shared@example.com", "Shared A");
        JsonNode second = createUser(otherTenantId, "shared@example.com", "Shared B");

        assertThat(second.get("tenantId").asText()).isEqualTo(otherTenantId.toString());
        assertThat(userRepository.findByTenantIdAndEmail(otherTenantId, "shared@example.com")).isPresent();
    }

    @Test
    @DisplayName("TEST 5: list is tenant scoped")
    void listUsers_isTenantScoped() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");
        createUser(tenantId, "bob@example.com", "Bob");
        createUser(otherTenantId, "carol@example.com", "Carol");

        mockMvc.perform(get("/api/v1/users").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].email").value(
                        org.hamcrest.Matchers.containsInAnyOrder("alice@example.com", "bob@example.com")))
                .andExpect(jsonPath("$[*].email").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("carol@example.com"))));
    }

    @Test
    @DisplayName("TEST 6: get returns tenant user")
    void getUser_isTenantScoped() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");

        mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("TEST 7: cross-tenant get returns 404")
    void crossTenantGet_returns404() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");

        mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", otherTenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("TEST 8: cross-tenant update returns 404")
    void crossTenantUpdate_returns404() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");

        mockMvc.perform(put("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", otherTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("new@example.com", "New")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    @DisplayName("TEST 9: update persists normalized email and display name")
    void updateUser_persists() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        UUID userId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(put("/api/v1/users/{userId}", userId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("  NEW@Example.COM ", "Updated")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("Updated"));

        User persisted = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
        assertThat(persisted.getEmail()).isEqualTo("new@example.com");
        assertThat(persisted.getDisplayName()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("TEST 10: activate persists ACTIVE")
    void activateUser_persistsActive() throws Exception {
        assertLifecycle("activate", UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("TEST 11: deactivate persists INACTIVE")
    void deactivateUser_persistsInactive() throws Exception {
        assertLifecycle("deactivate", UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("TEST 12: suspend persists SUSPENDED")
    void suspendUser_persistsSuspended() throws Exception {
        assertLifecycle("suspend", UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("TEST 13: archive persists ARCHIVED")
    void archiveUser_persistsArchived() throws Exception {
        assertLifecycle("archive", UserStatus.ARCHIVED);
    }

    @Test
    @DisplayName("TEST 14: validation errors use ApiErrorResponse")
    void validationError_isStructured() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("", "Alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"));
    }

    @Test
    @DisplayName("TEST 15: missing tenantId returns structured 400")
    void missingTenantId_isStructured() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("tenantId")))
                .andExpect(jsonPath("$.path").value("/api/v1/users"));
    }

    @Test
    @DisplayName("TEST 16: OpenAPI exposes exactly 8 User operations and 21 total")
    void openApi_containsUserOperations() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = root.path("paths");

        assertOperation(paths, "/api/v1/users", "post");
        assertOperation(paths, "/api/v1/users", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "get");
        assertOperation(paths, "/api/v1/users/{userId}", "put");
        assertOperation(paths, "/api/v1/users/{userId}/activate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/deactivate", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/suspend", "patch");
        assertOperation(paths, "/api/v1/users/{userId}/archive", "patch");

        assertThat(countOperations(paths, "/api/v1/users")).isEqualTo(8);
        assertThat(countOperations(paths, null)).isEqualTo(21);
    }

    private UUID saveTenant(String name) {
        Tenant tenant = tenantRepository.save(new Tenant(
                name,
                name.toLowerCase() + "-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        return tenant.getId();
    }

    private JsonNode createUser(UUID scopeTenantId, String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", scopeTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createPayload(String email, String displayName) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "displayName", displayName));
    }

    private String updatePayload(String email, String displayName) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "displayName", displayName));
    }

    private void assertLifecycle(String operation, UserStatus expectedStatus) throws Exception {
        JsonNode created = createUser(tenantId, operation + "@example.com", operation);
        UUID userId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/users/{userId}/" + operation, userId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus.name()));

        assertThat(userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow().getStatus())
                .isEqualTo(expectedStatus);
    }

    private static void assertOperation(JsonNode paths, String path, String method) {
        assertThat(paths.path(path).has(method))
                .as(method.toUpperCase() + " " + path)
                .isTrue();
    }

    private static long countOperations(JsonNode paths, String prefix) {
        long count = 0;
        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            if (prefix != null && !pathEntry.getKey().startsWith(prefix)) {
                continue;
            }
            Iterator<String> fields = pathEntry.getValue().fieldNames();
            while (fields.hasNext()) {
                if (HTTP_METHODS.contains(fields.next())) {
                    count++;
                }
            }
        }
        return count;
    }
}
