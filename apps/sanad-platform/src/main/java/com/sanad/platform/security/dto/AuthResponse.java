package com.sanad.platform.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private Instant expiresAt;
    private AuthUser user;
    private MeResponse profile;

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

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public AuthUser getUser() { return user; }
    public void setUser(AuthUser user) { this.user = user; }

    public MeResponse getProfile() { return profile; }
    public void setProfile(MeResponse profile) { this.profile = profile; }

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
