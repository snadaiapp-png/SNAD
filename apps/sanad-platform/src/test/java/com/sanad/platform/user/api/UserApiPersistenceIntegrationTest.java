package com.sanad.platform.user.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserApiPersistenceIntegrationTest {

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
    void createUserPersists() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        UUID userId = UUID.fromString(created.get("id").asText());
        assertThat(userRepository.findByTenantIdAndId(tenantId, userId)).isPresent();
        assertThat(created.get("status").asText()).isEqualTo("INVITED");
    }

    @Test
    void createUserNormalizesEmail() throws Exception {
        JsonNode created = createUser(tenantId, "  Alice@Example.COM  ", "Alice");
        assertThat(created.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(userRepository.findByTenantIdAndEmail(
                tenantId, "alice@example.com")).isPresent();
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ALICE@example.com", "Alice 2")))
                .andExpect(status().isConflict());
    }

    @Test
    void sameEmailDifferentTenantsAllowed() throws Exception {
        createUser(tenantId, "shared@example.com", "Shared A");
        JsonNode second = createUser(otherTenantId, "shared@example.com", "Shared B");
        assertThat(second.get("tenantId").asText()).isEqualTo(otherTenantId.toString());
    }

    @Test
    void listUsersIsTenantScoped() throws Exception {
        createUser(tenantId, "alice@example.com", "Alice");
        createUser(tenantId, "bob@example.com", "Bob");
        createUser(otherTenantId, "carol@example.com", "Carol");

        mockMvc.perform(get("/api/v1/users").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getUserIsTenantScoped() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void crossTenantGetReturns404() throws Exception {
        JsonNode created = createUser(tenantId, "alice@example.com", "Alice");
        // Bind other-tenant context for the cross-tenant get so the controller's
        // SELECT on users uses RLS-visible rows for otherTenantId (returns 404).
        bindTenantContext(otherTenantId);
        try {
            mockMvc.perform(get("/api/v1/users/{userId}", created.get("id").asText())
                            .param("tenantId", otherTenantId.toString()))
                    .andExpect(status().isNotFound());
        } finally {
            clearTenantContext();
        }
    }

    private UUID saveTenant(String name) {
        return tenantRepository.save(new Tenant(
                name, name.toLowerCase() + "-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId();
    }

    private JsonNode createUser(UUID scope, String email, String name) throws Exception {
        // public.users is FORCE RLS (V20261011_3). This test class is @Transactional,
        // so the controller's @Transactional participates in the test's tx. Bind the
        // SecurityContextHolder with the scope tenant_id BEFORE mockMvc.perform so
        // TenantRlsConnectionHandler applies SET LOCAL app.tenant_id on the tx-bound
        // Connection before the controller's userRepository.save(user) INSERT.
        bindTenantContext(scope);
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", scope.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(email, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String payload(String email, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "displayName", name));
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside the @Transactional boundary.
     * user_id is not needed for RLS — pass a placeholder.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void clearTenantContext() {
        SecurityContextHolder.clearContext();
    }
}
