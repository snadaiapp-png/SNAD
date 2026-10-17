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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UserApiMutationIntegrationTest {

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
    void crossTenantUpdateReturns404() throws Exception {
        UUID userId = createUser(tenantId, "alice@example.com", "Alice");
        // Bind other-tenant context for the cross-tenant update attempt so the
        // controller's SELECT on users uses RLS-visible rows for otherTenantId.
        bindTenantContext(otherTenantId);
        try {
            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .param("tenantId", otherTenantId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload("new@example.com", "New")))
                    .andExpect(status().isNotFound());
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void updateUserPersists() throws Exception {
        UUID userId = createUser(tenantId, "alice@example.com", "Alice");
        bindTenantContext(tenantId);
        try {
            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .param("tenantId", tenantId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload("  NEW@Example.COM ", "Updated")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("new@example.com"));

            User persisted = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(persisted.getDisplayName()).isEqualTo("Updated");
        } finally {
            clearTenantContext();
        }
    }

    @Test void activateUserPersistsActive() throws Exception { assertLifecycle("activate", UserStatus.ACTIVE); }
    @Test void deactivateUserPersistsInactive() throws Exception { assertLifecycle("deactivate", UserStatus.INACTIVE); }
    @Test void suspendUserPersistsSuspended() throws Exception { assertLifecycle("suspend", UserStatus.SUSPENDED); }
    @Test void archiveUserPersistsArchived() throws Exception { assertLifecycle("archive", UserStatus.ARCHIVED); }

    @Test
    void validationErrorIsStructured() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("", "Alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingTenantIdIsStructured() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private UUID saveTenant(String name) {
        return tenantRepository.save(new Tenant(
                name, name.toLowerCase() + "-" + UUID.randomUUID(), TenantStatus.ACTIVE)).getId();
    }

    private UUID createUser(UUID scope, String email, String name) throws Exception {
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
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(created.get("id").asText());
    }

    private String payload(String email, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "displayName", name));
    }

    private void assertLifecycle(String operation, UserStatus expected) throws Exception {
        UUID userId = createUser(tenantId, operation + "@example.com", operation);
        bindTenantContext(tenantId);
        try {
            mockMvc.perform(patch("/api/v1/users/{userId}/" + operation, userId)
                            .param("tenantId", tenantId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(expected.name()));
            assertThat(userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow().getStatus())
                    .isEqualTo(expected);
        } finally {
            clearTenantContext();
        }
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
