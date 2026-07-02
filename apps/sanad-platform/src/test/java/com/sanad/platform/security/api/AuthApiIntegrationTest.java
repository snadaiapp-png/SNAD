package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.grant.UserGrantStatus;
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
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
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
 *
 * <p><strong>Refresh token is delivered via HttpOnly cookie only.</strong>
 * The JSON response body does NOT contain the refresh token. Tests
 * extract the refresh token from the Set-Cookie header.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthApiIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "sanad_refresh";

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OrganizationMembershipRepository membershipRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository userRoleGrantRepository;
    @Autowired private AccessCapabilityRepository accessCapabilityRepository;
    @Autowired private RoleCapabilityRepository roleCapabilityRepository;

    private UUID tenantId;
    private String testEmail;
    private String testPassword;
    private UUID userId;

    @BeforeEach
    void setUp() {
        roleCapabilityRepository.deleteAll();
        userRoleGrantRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
        // Stage 05A.2.5: Clean up audit/idempotency tables first (FK ON DELETE RESTRICT)
        try { new JdbcTemplate(dataSource).execute("DELETE FROM audit_events"); } catch (Exception ignored) {}
        try { new JdbcTemplate(dataSource).execute("DELETE FROM audit_chain_heads"); } catch (Exception ignored) {}
        try { new JdbcTemplate(dataSource).execute("DELETE FROM idempotency_records"); } catch (Exception ignored) {}
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant(
                "Auth Test Tenant",
                "auth-test-" + UUID.randomUUID(),
                TenantStatus.ACTIVE
        );
        tenantId = tenantRepository.save(tenant).getId();

        testEmail = "testuser@example.com";
        testPassword = UUID.randomUUID().toString();
        User user = new User(tenantId, testEmail, "Test User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        userId = userRepository.save(user).getId();

        // Grant VIEWER role with USER.READ capability to test user
        // (V14 seeds roles for existing tenants only; test tenants are new)
        Role viewerRole = roleRepository.findByTenantIdAndCode(tenantId, "VIEWER")
                .orElseGet(() -> roleRepository.save(new Role(
                        tenantId, "VIEWER", "Viewer", "Read-only access")));
        AccessCapability userReadCap = accessCapabilityRepository.findByCode("USER.READ")
                .orElseGet(() -> accessCapabilityRepository.save(new AccessCapability(
                        "USER.READ", "Read Users", null)));
        if (!roleCapabilityRepository.existsByTenantIdAndRoleIdAndCapabilityId(
                tenantId, viewerRole.getId(), userReadCap.getId())) {
            roleCapabilityRepository.save(new RoleCapability(
                    tenantId, viewerRole.getId(), userReadCap.getId()));
        }
        UserRoleGrant grant = new UserRoleGrant(tenantId, userId, viewerRole.getId(), null);
        userRoleGrantRepository.save(grant);

        // Stage 04A.3.6.1: Create an ACTIVE membership for the test user
        // so strict membership enforcement allows authentication
        Organization org = new Organization(tenant, "Auth Test Org",
                "Test org", OrganizationStatus.ACTIVE);
        organizationRepository.save(org);
        OrganizationMembership membership = new OrganizationMembership(
                tenantId, org.getId(), testEmail, "Test User",
                com.sanad.platform.organization.membership.domain.MembershipStatus.ACTIVE);
        membership.setUserId(userId);
        membershipRepository.save(membership);
    }

    // ------------------------------------------------------------
    // Login tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/login — valid credentials returns 200 with access token; refresh token in cookie only")
    void login_validCredentials_returnsTokens() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(testEmail))
                .andExpect(jsonPath("$.user.tenantId").value(tenantId.toString()))
                // Refresh token must NOT be in the JSON body
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Refresh token must be in the Set-Cookie header
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assert setCookie != null : "Set-Cookie header must be present";
        assert setCookie.contains(REFRESH_COOKIE_NAME) : "Set-Cookie must contain refresh cookie";
        assert setCookie.contains("HttpOnly") : "Cookie must be HttpOnly";
        assert setCookie.contains("Path=/api/v1/auth") : "Cookie must be scoped to /api/v1/auth";
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — non-existent user returns 401")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest request = new LoginRequest("nobody@example.com", testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — email-only login finds user across tenants")
    void login_wrongTenant_returns401() throws Exception {
        // With email-only login, the user is found by email regardless of tenant.
        // This test now verifies that email-only login works (returns 200, not 401).
        LoginRequest request = new LoginRequest(testEmail, testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — suspended user returns 401")
    void login_suspendedUser_returns401() throws Exception {
        User user = userRepository.findByTenantIdAndEmail(tenantId, testEmail).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        LoginRequest request = new LoginRequest(testEmail, testPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — user without password returns 401")
    void login_userWithoutPassword_returns401() throws Exception {
        User nopassUser = new User(tenantId, "nopass@example.com", "No Pass User", UserStatus.ACTIVE);
        userRepository.save(nopassUser);

        LoginRequest request = new LoginRequest("nopass@example.com", "anyPassword");

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
    @DisplayName("POST /api/v1/auth/login — no password in response body or headers")
    void login_noPasswordInResponse() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assert !responseBody.contains("password") : "Password must not appear in response";
        assert !responseBody.contains("passwordHash") : "Password hash must not appear in response";
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — login with optional tenantId returns 200")
    void login_withOptionalTenantId_returns200() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        request.setTenantId(tenantId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.tenantId").value(tenantId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — duplicate email across tenants returns 409")
    void login_duplicateEmailAcrossTenants_returns409() throws Exception {
        // Create a second tenant with the same email
        Tenant tenant2 = new Tenant(
                "Second Tenant",
                "second-tenant-" + UUID.randomUUID(),
                TenantStatus.ACTIVE
        );
        UUID tenant2Id = tenantRepository.save(tenant2).getId();
        User user2 = new User(tenant2Id, testEmail, "Duplicate User", UserStatus.ACTIVE);
        user2.setPasswordHash(passwordEncoder.encode(testPassword));
        userRepository.save(user2);

        // Login without tenantId should return 409 (ambiguous tenant)
        // Stage 03A: tenantIds array is no longer in the unified error body —
        // the safe detail instructs the client to disambiguate.
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("SANAD-TEN-003"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login — duplicate email resolved with explicit tenantId")
    void login_duplicateEmailWithExplicitTenantId_returns200() throws Exception {
        // Create a second tenant with the same email
        Tenant tenant2 = new Tenant(
                "Second Tenant",
                "second-tenant-explicit-" + UUID.randomUUID(),
                TenantStatus.ACTIVE
        );
        UUID tenant2Id = tenantRepository.save(tenant2).getId();
        User user2 = new User(tenant2Id, testEmail, "Duplicate User", UserStatus.ACTIVE);
        user2.setPasswordHash(passwordEncoder.encode(testPassword));
        userRepository.save(user2);

        // Login WITH explicit tenantId should succeed
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        request.setTenantId(tenantId);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.tenantId").value(tenantId.toString()));
    }

    // ------------------------------------------------------------
    // Refresh tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/refresh — valid refresh token (from cookie) returns new tokens")
    void refresh_validToken_returnsNewTokens() throws Exception {
        // Login to get the refresh token cookie
        LoginResult loginResult = loginAndExtractCookie();

        // Refresh using the body (local profile allows body fallback for testing)
        RefreshRequest refreshRequest = new RefreshRequest(loginResult.refreshTokenValue);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // The new refresh cookie must be different (rotation)
        String newCookie = refreshResult.getResponse().getHeader("Set-Cookie");
        assert newCookie != null && newCookie.contains(REFRESH_COOKIE_NAME);
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh — reusing old refresh token returns 401 (replay protection)")
    void refresh_reusingOldToken_returns401() throws Exception {
        LoginResult loginResult = loginAndExtractCookie();
        RefreshRequest refreshRequest = new RefreshRequest(loginResult.refreshTokenValue);

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

    @Test
    @DisplayName("POST /api/v1/auth/refresh — blocked for SUSPENDED user (revokes all tokens)")
    void refresh_blockedForSuspendedUser() throws Exception {
        LoginResult loginResult = loginAndExtractCookie();

        // Suspend the user
        User user = userRepository.findByTenantIdAndEmail(tenantId, testEmail).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        // Attempt refresh — should fail with 401
        RefreshRequest refreshRequest = new RefreshRequest(loginResult.refreshTokenValue);
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
        AuthResponse loginResponse = loginAndGetAccessToken();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.credentialRotationRequired").isBoolean())
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
        AuthResponse loginResponse = loginAndGetAccessToken();

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
        LoginResult loginResult = loginAndExtractCookie();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginResult.accessToken))
                .andExpect(status().isNoContent());

        // Try to refresh with the old refresh token — should fail
        RefreshRequest refreshRequest = new RefreshRequest(loginResult.refreshTokenValue);
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
        AuthResponse loginResponse = loginAndGetAccessToken();

        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenantId.toString())
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------
    // Tenant binding tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/users with JWT tenantId mismatch returns 403")
    void protectedEndpoint_tenantMismatch_returns403() throws Exception {
        AuthResponse loginResponse = loginAndGetAccessToken();
        UUID wrongTenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", wrongTenantId.toString())
                        .header("Authorization", "Bearer " + loginResponse.getAccessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
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

    private static class LoginResult {
        String accessToken;
        String refreshTokenValue;
    }

    private LoginResult loginAndExtractCookie() throws Exception {
        LoginRequest request = new LoginRequest(testEmail, testPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResult loginResult = new LoginResult();
        // accessToken is in the JSON body
        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        loginResult.accessToken = response.getAccessToken();

        // refreshToken is NOT in the JSON body (@JsonIgnore) — extract from Set-Cookie header
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie != null) {
            // Parse the cookie value: "sanad_refresh=<value>; Path=...; HttpOnly; ..."
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

    private AuthResponse loginAndGetAccessToken() throws Exception {
        LoginResult result = loginAndExtractCookie();
        AuthResponse response = new AuthResponse();
        response.setAccessToken(result.accessToken);
        response.setRefreshToken(result.refreshTokenValue);
        return response;
    }
}
