package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DEFECT-010: API Regression Suite — Auth, Organizations, Users, Memberships
 *
 * <p>Comprehensive regression tests covering the full authentication lifecycle,
 * organization access, user management, and membership operations.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ApiRegressionSuiteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMembershipRepository membershipRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository userRoleGrantRepository;
    @Autowired private AccessCapabilityRepository accessCapabilityRepository;
    @Autowired private RoleCapabilityRepository roleCapabilityRepository;

    private UUID tenantId;
    private UUID orgId;
    private String testEmail;
    private String testPassword;
    private UUID userId;

    @BeforeEach
    void setUp() {
        roleCapabilityRepository.deleteAll();
        userRoleGrantRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = tenantRepository.save(new Tenant(
                "Regression Test Tenant",
                "regression-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        tenantId = tenant.getId();

        testEmail = "regression@example.com";
        testPassword = UUID.randomUUID().toString();
        User user = new User(tenantId, testEmail, "Regression User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        userId = userRepository.save(user).getId();

        Organization org = organizationRepository.save(new Organization(
                tenant, "Regression Org", "Test organization", OrganizationStatus.ACTIVE));
        orgId = org.getId();

        OrganizationMembership membership = new OrganizationMembership(
                tenantId, orgId, testEmail, "Regression User", MembershipStatus.ACTIVE);
        membership.setUserId(userId);
        membershipRepository.save(membership);

        // Grant ADMIN role with all capabilities for regression testing
        Role adminRole = roleRepository.findByTenantIdAndCode(tenantId, "ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(
                        tenantId, "ADMIN", "Administrator", "Full access")));
        // Add all active capabilities to the admin role
        for (AccessCapability cap : accessCapabilityRepository.findAll()) {
            if (!roleCapabilityRepository.existsByTenantIdAndRoleIdAndCapabilityId(
                    tenantId, adminRole.getId(), cap.getId())) {
                roleCapabilityRepository.save(new RoleCapability(
                        tenantId, adminRole.getId(), cap.getId()));
            }
        }
        UserRoleGrant grant = new UserRoleGrant(tenantId, userId, adminRole.getId(), null);
        userRoleGrantRepository.save(grant);
    }

    // ================================================================
    // Auth Regression
    // ================================================================

    @Nested
    @DisplayName("Auth: Login/Logout/Me/Refresh regression")
    class AuthRegression {

        @Test
        @DisplayName("Full login → me → logout → me(401) cycle")
        void fullAuthCycle() throws Exception {
            String accessToken = loginAndGetToken();

            // /me works
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(testEmail));

            // Logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // /me after logout = 401 (DEFECT-001)
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Change credential revokes all tokens")
        void changeCredentialRevokesTokens() throws Exception {
            String accessToken = loginAndGetToken();
            String newPassword = UUID.randomUUID().toString();

            ChangeCredentialRequest changeRequest = new ChangeCredentialRequest();
            changeRequest.setCurrentCredential(testPassword);
            changeRequest.setNewCredential(newPassword);

            mockMvc.perform(post("/api/v1/auth/change-credential")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isNoContent());

            // Old token revoked
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());

            // Login with new password works
            LoginRequest newLogin = new LoginRequest(testEmail, newPassword);
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newLogin)))
                    .andExpect(status().isOk())
                    .andReturn();

            String newToken = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + newToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Invalid credentials return 401")
        void invalidCredentials() throws Exception {
            LoginRequest request = new LoginRequest(testEmail, "WrongPassword");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("No auth header returns 401")
        void noAuthHeader() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ================================================================
    // Organization Regression
    // ================================================================

    @Nested
    @DisplayName("Organizations: Access regression")
    class OrganizationRegression {

        @Test
        @DisplayName("Authenticated user can list organizations")
        void listOrganizations() throws Exception {
            String accessToken = loginAndGetToken();

            mockMvc.perform(get("/api/v1/organizations")
                            .param("tenantId", tenantId.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Tenant mismatch returns 403")
        void tenantMismatch() throws Exception {
            String accessToken = loginAndGetToken();

            mockMvc.perform(get("/api/v1/organizations")
                            .param("tenantId", UUID.randomUUID().toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    // Membership Regression
    // ================================================================

    @Nested
    @DisplayName("Memberships: Access regression")
    class MembershipRegression {

        @Test
        @DisplayName("Authenticated user can list memberships")
        void listMemberships() throws Exception {
            String accessToken = loginAndGetToken();

            mockMvc.perform(get("/api/v1/organizations/" + orgId + "/memberships")
                            .param("tenantId", tenantId.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User memberships endpoint works")
        void userMemberships() throws Exception {
            String accessToken = loginAndGetToken();

            mockMvc.perform(get("/api/v1/users/" + userId + "/memberships")
                            .param("tenantId", tenantId.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Session Version Regression (DEFECT-001)
    // ================================================================

    @Nested
    @DisplayName("Session Version: Token revocation regression")
    class SessionVersionRegression {

        @Test
        @DisplayName("Session version increments on each logout")
        void sessionVersionIncrements() throws Exception {
            User before = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            long initialVersion = before.getSessionVersion();

            String token = loginAndGetToken();
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            User after = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(after.getSessionVersion()).isEqualTo(initialVersion + 1);
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();
    }
}
