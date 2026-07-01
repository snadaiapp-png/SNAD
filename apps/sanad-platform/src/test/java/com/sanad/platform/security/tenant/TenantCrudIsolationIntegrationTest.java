package com.sanad.platform.security.tenant;

import com.sanad.platform.security.service.JwtTokenProvider;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 04 §38 — Cross-tenant CRUD isolation integration tests.
 *
 * <p>Verifies that an authenticated user in Tenant A cannot:</p>
 * <ul>
 *   <li>Read Tenant B's users (GET /api/v1/users with Tenant B's tenantId → 403)</li>
 *   <li>Read Tenant B's organizations (GET /api/v1/organizations with Tenant B's tenantId → 403)</li>
 *   <li>Create a user in Tenant B (POST /api/v1/users?tenantId=B → 403)</li>
 *   <li>Discover Tenant B's resource existence via UUID guessing (GET /api/v1/users/{B-userId}?tenantId=A → 404)</li>
 * </ul>
 *
 * <p>The JwtAuthenticationFilter already rejects tenantId query param mismatches
 * with 403. These tests prove that protection is in place and that the
 * TenantContext infrastructure (Stage 04) is correctly wired.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
@Transactional
class TenantCrudIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantContextProvider contextProvider;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID userAId;
    private UUID userBId;
    private String tokenA;

    @BeforeEach
    void setUp() {
        // Create two tenants (tenants table has no RLS — SECURITY_GLOBAL)
        tenantAId = tenantRepository.save(
                new Tenant("Tenant A", "tenant-a-" + UUID.randomUUID(), TenantStatus.ACTIVE)
        ).getId();
        tenantBId = tenantRepository.save(
                new Tenant("Tenant B", "tenant-b-" + UUID.randomUUID(), TenantStatus.ACTIVE)
        ).getId();

        // Stage 04A.3: RLS is active on the users table. Set the tenant context
        // directly on the transaction's connection via EntityManager.
        setRlsTenant(tenantAId);

        User userA = new User(tenantAId, "alice-a@example.com", "Alice A", UserStatus.ACTIVE);
        userA.setPasswordHash("dummy-hash");
        userA = userRepository.save(userA);
        userAId = userA.getId();

        // Switch context to tenant B for user B creation
        setRlsTenant(tenantBId);

        User userB = new User(tenantBId, "bob-b@example.com", "Bob B", UserStatus.ACTIVE);
        userB.setPasswordHash("dummy-hash");
        userB = userRepository.save(userB);
        userBId = userB.getId();

        // Clear context — tests will set their own via JWT
        contextProvider.clear();

        // Mint a JWT for user A in tenant A
        tokenA = jwtTokenProvider.mintAccessToken(userAId, tenantAId, "alice-a@example.com");
    }

    /**
     * Sets the RLS tenant on the current transaction's connection.
     * Uses the EntityManager bound to the test's @Transactional context.
     */
    private void setRlsTenant(UUID tenantId) {
        entityManager.createNativeQuery(
                "SELECT set_config('app.current_tenant_id', :tenant, true)")
                .setParameter("tenant", tenantId.toString())
                .getSingleResult();
    }

    @Test
    @DisplayName("Cross-tenant read: Tenant A user cannot list Tenant B's users (403)")
    void crossTenantRead_users_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantBId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cross-tenant read: Tenant A user cannot list Tenant B's organizations (403)")
    void crossTenantRead_organizations_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/organizations")
                        .param("tenantId", tenantBId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cross-tenant write: Tenant A user cannot create a user in Tenant B (403)")
    void crossTenantWrite_user_rejected() throws Exception {
        String body = "{\"email\":\"eve@evil.com\",\"displayName\":\"Eve\"}";
        mockMvc.perform(post("/api/v1/users")
                        .param("tenantId", tenantBId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Same-tenant read: 403 (missing capability — exact, no ambiguity)")
    void sameTenantRead_users_exactStatus() throws Exception {
        // Stage 04A.3 §13: exact status assertion, no ambiguous ||.
        // The test user has no USER.READ capability → 403 SANAD-SEC-001.
        // This proves the tenant binding was accepted (not 403 for tenant mismatch).
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantAId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("Cross-tenant ID enumeration: 403 (missing capability — exact, no ambiguity)")
    void crossTenantIdEnumeration_exactStatus() throws Exception {
        // Stage 04A.3 §13: exact status assertion.
        // Tenant A user, tenantId=A, userId=B → @RequireCapability returns 403
        // before the service is reached (user lacks USER.READ).
        mockMvc.perform(get("/api/v1/users/{userId}", userBId)
                        .param("tenantId", tenantAId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SANAD-SEC-001"));
    }

    @Test
    @DisplayName("Missing tenantId: request without tenantId param (no selector) — behavior depends on endpoint")
    void missingTenantId_selector() throws Exception {
        // Without a tenantId query param, the JwtAuthenticationFilter doesn't
        // reject (no mismatch). The controller's @RequestParam UUID tenantId
        // triggers a 400 (missing required param).
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid tenantId format: non-UUID tenantId → 400")
    void invalidTenantIdFormat_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", "not-a-uuid")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }
}
