package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the authentication endpoints.
 *
 * <p>Uses the {@code local} profile (H2 in-memory database) with real
 * Flyway migrations. Each test creates its own tenant + user with a
 * BCrypt-hashed password and verifies the full auth flow.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationMembershipRepository membershipRepository;
    @Autowired private com.sanad.platform.security.domain.RefreshTokenRepository refreshTokenRepository;

    private UUID tenantId;
    private String testEmail;
    private String testPassword;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // Clean up in reverse dependency order (refresh_tokens → memberships → users → tenants)
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        // Create a test tenant
        Tenant tenant = new Tenant(
                "Auth Test Tenant",
                "auth-test-" + UUID.randomUUID(),
                TenantStatus.ACTIVE
        );
        tenantId = tenantRepository.save(tenant).getId();

        // Create a test user with a password
        testEmail = "testuser@example.com";
        testPassword = "TestPassword123!";
        User user = new User(tenantId, testEmail, "Test User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        userId = userRepository.save(user).getId();
    }

    // ------------------------------------------------------------
    // Login tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/login — valid credentials returns 200 with tokens")
    void login_validCredentials_returnsTokens() throws Exception {
        LoginRequest request = new LoginRequest(tenantId, testEmail, testPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(testEmail))
                .andExpect(jsonPath("$.user.tenantId").value(tenantId.toString()))
                .andReturn();

        // Verify the response can be deserialized
        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assert response.getAccessToken() != null;
        assert response.getRefreshToken() != null;
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest(tenantId, testEmail, "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — non-existent user returns 401")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest request = new LoginRequest(tenantId, "nobody@example.com", testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — wrong tenant returns 401")
    void login_wrongTenant_returns401() throws Exception {
        UUID wrongTenantId = UUID.randomUUID();
        LoginRequest request = new LoginRequest(wrongTenantId, testEmail, testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — suspended user returns 401")
    void login_suspendedUser_returns401() throws Exception {
        // Change user status to SUSPENDED
        User user = userRepository.findByTenantIdAndEmail(tenantId, testEmail).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        LoginRequest request = new LoginRequest(tenantId, testEmail, testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — user without password returns 401")
    void login_userWithoutPassword_returns401() throws Exception {
        // Create a user with no password hash
        User nopassUser = new User(tenantId, "nopass@example.com", "No Pass User", UserStatus.ACTIVE);
        // passwordHash is null by default
        userRepository.save(nopassUser);

        LoginRequest request = new LoginRequest(tenantId, "nopass@example.com", "anyPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — malformed request returns 400")
    void login_malformedRequest_returns400() throws Exception {
        String malformedJson = "{\"tenantId\":\"not-a-uuid\",\"email\":\"\",\"password\":\"\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — no password in response body")
    void login_noPasswordInResponse() throws Exception {
        LoginRequest request = new LoginRequest(tenantId, testEmail, testPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assert !responseBody.contains("password") : "Password must not appear in response";
        assert !responseBody.contains("passwordHash") : "Password hash must not appear in response";
    }

    // ------------------------------------------------------------
    // Refresh tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/refresh — valid refresh token returns new tokens")
    void refresh_validToken_returnsNewTokens() throws Exception {
        // First, login to get a refresh token
        AuthResponse loginResponse = loginAndGetResponse();

        // Now refresh
        RefreshRequest refreshRequest = new RefreshRequest(loginResponse.getRefreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                // The new refresh token must be different from the old one (rotation)
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(loginResponse.getRefreshToken())));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — reusing old refresh token returns 401 (replay protection)")
    void refresh_reusingOldToken_returns401() throws Exception {
        AuthResponse loginResponse = loginAndGetResponse();
        RefreshRequest refreshRequest = new RefreshRequest(loginResponse.getRefreshToken());

        // First refresh succeeds
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        // Second refresh with the SAME (now USED) token must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — invalid refresh token returns 401")
    void refresh_invalidToken_returns401() throws Exception {
        RefreshRequest refreshRequest = new RefreshRequest("invalid-token-value");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------
    // /me tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/auth/me — without token returns 401")
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me — with valid token returns user identity")
    void me_withValidToken_returnsUser() throws Exception {
        AuthResponse loginResponse = loginAndGetResponse();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.roleGrants").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me — with invalid token returns 401")
    void me_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------
    // Logout tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/logout — with valid token returns 204")
    void logout_withValidToken_returns204() throws Exception {
        AuthResponse loginResponse = loginAndGetResponse();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout — without token returns 401")
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout — after logout, refresh token is revoked")
    void logout_revokesRefreshToken() throws Exception {
        AuthResponse loginResponse = loginAndGetResponse();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isNoContent());

        // Try to refresh with the old refresh token — should fail
        RefreshRequest refreshRequest = new RefreshRequest(loginResponse.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------
    // Protected endpoint tests (401/403 contracts)
    // ------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/users without token returns 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/v1/users with valid token returns 200")
    void protectedEndpoint_withValidToken_returns200() throws Exception {
        AuthResponse loginResponse = loginAndGetResponse();

        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------
    // Actuator health remains public
    // ------------------------------------------------------------

    @Test
    @DisplayName("GET /actuator/health without token returns 200")
    void actuatorHealth_remainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private AuthResponse loginAndGetResponse() throws Exception {
        LoginRequest request = new LoginRequest(tenantId, testEmail, testPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }
}
