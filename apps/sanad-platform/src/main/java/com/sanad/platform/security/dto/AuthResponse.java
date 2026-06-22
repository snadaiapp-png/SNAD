package com.sanad.platform.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/auth/login} and
 * {@code POST /api/v1/auth/refresh}.
 *
 * <p>Contains the short-lived access JWT, the access token's expiry
 * time, and basic user identity (no sensitive data).</p>
 *
 * <p><strong>The refresh token is NOT included in the JSON response.</strong>
 * It is set exclusively as an HttpOnly cookie by the controller (BFF pattern).
 * The {@code refreshToken} field exists only for internal controller use
 * (to set the cookie) and is annotated with {@code @JsonIgnore} to ensure
 * it is never serialized to JSON.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private Instant expiresAt;
    private AuthUser user;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, Instant expiresAt, AuthUser user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    /** Internal-only — never serialized to JSON. Used by the controller to set the HttpOnly cookie. */
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public AuthUser getUser() { return user; }
    public void setUser(AuthUser user) { this.user = user; }

    /**
     * Minimal user identity included in auth responses.
     * Never includes password hash or other sensitive data.
     */
    public static class AuthUser {
        private UUID id;
        private UUID tenantId;
        private String email;
        private String displayName;
        private String status;

        public AuthUser() {
        }

        public AuthUser(UUID id, UUID tenantId, String email, String displayName, String status) {
            this.id = id;
            this.tenantId = tenantId;
            this.email = email;
            this.displayName = displayName;
            this.status = status;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
