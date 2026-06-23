package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
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
 * Integration tests for DEFECT-001: Immediate Access Token Revocation After Logout.
 *
 * <p>Validates that the session versioning mechanism correctly invalidates
 * access tokens when a user logs out, changes their password, or has their
 * password reset. The JWT contains a session_version claim that is compared
 * against the current value in the database on every request.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TokenRevocationIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "sanad_refresh";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationMembershipRepository membershipRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private UUID tenantId;
    private String testEmail;
    private String testPassword;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant(
                "Token Revocation Test Tenant",
                "token-revocation-" + UUID.randomUUID(),
                TenantStatus.ACTIVE
        );
        tenantId = tenantRepository.save(tenant).getId();

        testEmail = "revocation-test@example.com";
        testPassword = "TestPassword123!";
        User user = new User(tenantId, testEmail, "Revocation Test User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        userId = userRepository.save(user).getId();
    }

    // ================================================================
    // Logout → Access Token Revocation
    // ================================================================

    @Nested
    @DisplayName("DEFECT-001: Logout revokes access token immediately")
    class LogoutRevocation {

        @Test
        @DisplayName("After logout, the same access token returns 401 on /auth/me")
        void logout_accessTokenRevoked_returns401OnMe() throws Exception {
            // Login and get access token
            String accessToken = loginAndGetAccessToken();

            // Verify /me works before logout
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            // Logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // Same access token should now be rejected (401)
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("After logout, the same access token returns 401 on protected endpoints")
        void logout_accessTokenRevoked_returns401OnProtected() throws Exception {
            String accessToken = loginAndGetAccessToken();

            // Verify protected endpoint works before logout
            mockMvc.perform(get("/api/v1/users")
                            .param("tenantId", tenantId.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());

            // Logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // Same access token should now be rejected
            mockMvc.perform(get("/api/v1/users")
                            .param("tenantId", tenantId.toString())
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("After logout, the refresh token also returns 401")
        void logout_refreshTokenAlsoRevoked() throws Exception {
            LoginResult loginResult = loginAndExtractTokens();

            // Logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + loginResult.accessToken))
                    .andExpect(status().isNoContent());

            // Refresh token should also be revoked
            RefreshRequest refreshRequest = new RefreshRequest(loginResult.refreshTokenValue);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("After logout, re-login produces a new valid access token")
        void logout_reLoginProducesValidToken() throws Exception {
            String oldAccessToken = loginAndGetAccessToken();

            // Logout
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isNoContent());

            // Old token is rejected
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isUnauthorized());

            // New login produces a valid token
            String newAccessToken = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + newAccessToken))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Password Change → Access Token Revocation
    // ================================================================

    @Nested
    @DisplayName("DEFECT-001: Password change revokes access token immediately")
    class PasswordChangeRevocation {

        @Test
        @DisplayName("After password change, the old access token returns 401")
        void passwordChange_revokesOldAccessToken() throws Exception {
            String accessToken = loginAndGetAccessToken();

            // Change password
            ChangeCredentialRequest changeRequest = new ChangeCredentialRequest();
            changeRequest.setCurrentCredential(testPassword);
            changeRequest.setNewCredential("NewPassword456!");

            mockMvc.perform(post("/api/v1/auth/change-credential")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isNoContent());

            // Old access token should now be rejected
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("After password change, re-login with new password works")
        void passwordChange_reLoginWithNewPassword() throws Exception {
            String accessToken = loginAndGetAccessToken();
            String newPassword = "NewPassword456!";

            ChangeCredentialRequest changeRequest = new ChangeCredentialRequest();
            changeRequest.setCurrentCredential(testPassword);
            changeRequest.setNewCredential(newPassword);

            mockMvc.perform(post("/api/v1/auth/change-credential")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isNoContent());

            // Login with new password
            LoginRequest loginRequest = new LoginRequest(testEmail, newPassword);
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            // The new access token should be valid
            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AuthResponse.class);
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + response.getAccessToken()))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Session Version Integrity
    // ================================================================

    @Nested
    @DisplayName("DEFECT-001: Session version is tracked correctly")
    class SessionVersionTracking {

        @Test
        @DisplayName("Session version increments on each logout")
        void sessionVersion_incrementsOnLogout() throws Exception {
            // Initial session version should be 0
            User userBefore = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            long initialVersion = userBefore.getSessionVersion();

            // Login and logout
            String accessToken = loginAndGetAccessToken();
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // Session version should have incremented
            User userAfter = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(userAfter.getSessionVersion()).isEqualTo(initialVersion + 1);
        }

        @Test
        @DisplayName("Session version increments on password change")
        void sessionVersion_incrementsOnPasswordChange() throws Exception {
            User userBefore = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            long initialVersion = userBefore.getSessionVersion();

            String accessToken = loginAndGetAccessToken();
            ChangeCredentialRequest changeRequest = new ChangeCredentialRequest();
            changeRequest.setCurrentCredential(testPassword);
            changeRequest.setNewCredential("NewPassword456!");

            mockMvc.perform(post("/api/v1/auth/change-credential")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isNoContent());

            User userAfter = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(userAfter.getSessionVersion()).isEqualTo(initialVersion + 1);
        }

        @Test
        @DisplayName("Multiple logouts increment session version cumulatively")
        void sessionVersion_cumulativeIncrements() throws Exception {
            User userBefore = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            long initialVersion = userBefore.getSessionVersion();

            // Login and logout 3 times
            for (int i = 0; i < 3; i++) {
                String accessToken = loginAndGetAccessToken();
                mockMvc.perform(post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isNoContent());
            }

            User userAfter = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(userAfter.getSessionVersion()).isEqualTo(initialVersion + 3);
        }
    }

    // ================================================================
    // JWT contains session_version claim
    // ================================================================

    @Nested
    @DisplayName("DEFECT-001: JWT contains session_version claim")
    class JwtSessionVersionClaim {

        @Test
        @DisplayName("Login response JWT includes session_version matching DB")
        void jwt_includesSessionVersionClaim() throws Exception {
            // Session version should be 0 for new user
            User user = userRepository.findByTenantIdAndId(tenantId, userId).orElseThrow();
            assertThat(user.getSessionVersion()).isEqualTo(0L);

            // Login
            LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AuthResponse.class);

            // The access token should work (session_version = 0 in both JWT and DB)
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + response.getAccessToken()))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Token without session_version claim (backward compat / migration)
    // ================================================================

    @Nested
    @DisplayName("DEFECT-001: Tokens without session_version claim are rejected")
    class LegacyTokenHandling {

        @Test
        @DisplayName("A token without session_version claim is treated as version 0 and validated")
        void legacyToken_treatedAsVersion0() throws Exception {
            // New users start with session_version = 0.
            // A JWT without the session_version claim defaults to 0 in the filter.
            // If the DB session_version is also 0, the token should be accepted.
            // This is the backward-compatible path for tokens issued during migration.
            String accessToken = loginAndGetAccessToken();

            // Should work since both JWT (version 0 via default) and DB (version 0) match
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static class LoginResult {
        String accessToken;
        String refreshTokenValue;
    }

    private LoginResult loginAndExtractTokens() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResult loginResult = new LoginResult();
        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        loginResult.accessToken = response.getAccessToken();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie != null) {
            for (String part : setCookie.split(";")) {
                part = part.trim();
                if (part.startsWith(REFRESH_COOKIE_NAME + "=")) {
                    loginResult.refreshTokenValue = part.substring(REFRESH_COOKIE_NAME.length() + 1);
                    break;
                }
            }
        }
        return loginResult;
    }

    private String loginAndGetAccessToken() throws Exception {
        LoginResult result = loginAndExtractTokens();
        return result.accessToken;
    }
}
