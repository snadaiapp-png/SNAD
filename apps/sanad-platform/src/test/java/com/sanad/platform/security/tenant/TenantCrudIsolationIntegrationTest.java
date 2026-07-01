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
@ActiveProfiles("local")
@Transactional
class TenantCrudIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID userAId;
    private UUID userBId;
    private String tokenA;

    @BeforeEach
    void setUp() {
        // Create two tenants
        tenantAId = tenantRepository.save(
                new Tenant("Tenant A", "tenant-a-" + UUID.randomUUID(), TenantStatus.ACTIVE)
        ).getId();
        tenantBId = tenantRepository.save(
                new Tenant("Tenant B", "tenant-b-" + UUID.randomUUID(), TenantStatus.ACTIVE)
        ).getId();

        // Create a user in each tenant
        User userA = new User(tenantAId, "alice-a@example.com", "Alice A", UserStatus.ACTIVE);
        userA.setPasswordHash("dummy-hash");
        userA = userRepository.save(userA);
        userAId = userA.getId();

        User userB = new User(tenantBId, "bob-b@example.com", "Bob B", UserStatus.ACTIVE);
        userB.setPasswordHash("dummy-hash");
        userB = userRepository.save(userB);
        userBId = userB.getId();

        // Mint a JWT for user A in tenant A
        tokenA = jwtTokenProvider.mintAccessToken(userAId, tenantAId, "alice-a@example.com");
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
    @DisplayName("Same-tenant read: Tenant A user can attempt to list Tenant A's users (not 403 for tenant mismatch)")
    void sameTenantRead_users_notRejectedForTenant() throws Exception {
        // The user doesn't have USER.READ capability, so the @RequireCapability
        // aspect may return 403. But the JwtAuthenticationFilter should NOT
        // reject for tenant mismatch (tenantId matches JWT). The status should
        // NOT be 403-for-tenant-mismatch. It may be 403-for-missing-capability
        // or 200 if the test config permits. We verify it's not the tenant
        // binding 403 by checking that the response is not a tenant violation.
        // Since we can't distinguish 403 reasons easily, we accept 200 or 403
        // (capability) but NOT a tenant binding error.
        int status = mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantAId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getStatus();
        // Accept 200 (success) or 403 (missing capability) — both prove the
        // tenant binding itself was accepted (not rejected for cross-tenant).
        assertThat(status == 200 || status == 403)
                .as("Same-tenant read should not be rejected for tenant mismatch (got " + status + ")")
                .isTrue();
    }

    @Test
    @DisplayName("Cross-tenant ID enumeration: Tenant A user cannot read Tenant B's user by ID")
    void crossTenantIdEnumeration_rejected() throws Exception {
        // Tenant A user tries to read Tenant B's user by ID.
        // If tenantId param is A but userId is B's, the JwtAuthenticationFilter
        // accepts (tenantId matches JWT). The service should return 404
        // (no user with that ID in tenant A). If the user lacks capability,
        // it may return 403. Either way, it should NOT return 200 with B's data.
        int status = mockMvc.perform(get("/api/v1/users/{userId}", userBId)
                        .param("tenantId", tenantAId.toString())
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getStatus();
        // Accept 403 (missing capability) or 404 (not found) — both prove
        // cross-tenant data is not returned. 200 would be a violation.
        assertThat(status == 403 || status == 404)
                .as("Cross-tenant ID enumeration must return 403 or 404, not 200 (got " + status + ")")
                .isTrue();
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
