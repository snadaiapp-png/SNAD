package com.sanad.platform.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete authentication bootstrap returned by login and refresh.
 *
 * <p>The response contains the short-lived access token plus the minimum
 * identity, membership, authorization and navigation context required to
 * enter the application without a second sequential {@code /auth/me} call.</p>
 *
 * <p><strong>The refresh token is never serialized.</strong> It is transferred
 * only through the trusted BFF header/cookie boundary.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private Instant expiresAt;
    private AuthUser user;
    private Instant lastLoginAt;
    private boolean credentialRotationRequired;
    private List<MeResponse.MembershipSummary> memberships = List.of();
    private List<MeResponse.RoleGrantSummary> effectiveRoleGrants = List.of();
    private UUID defaultOrganizationId;
    private String defaultDestination = "/workspace";
    private List<String> availableDestinations = List.of("/workspace");
    private TenantContext tenantContext;

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
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public boolean isCredentialRotationRequired() { return credentialRotationRequired; }
    public void setCredentialRotationRequired(boolean credentialRotationRequired) {
        this.credentialRotationRequired = credentialRotationRequired;
    }
    public List<MeResponse.MembershipSummary> getMemberships() { return memberships; }
    public void setMemberships(List<MeResponse.MembershipSummary> memberships) {
        this.memberships = memberships == null ? List.of() : List.copyOf(memberships);
    }
    public List<MeResponse.RoleGrantSummary> getEffectiveRoleGrants() { return effectiveRoleGrants; }
    public void setEffectiveRoleGrants(List<MeResponse.RoleGrantSummary> effectiveRoleGrants) {
        this.effectiveRoleGrants = effectiveRoleGrants == null ? List.of() : List.copyOf(effectiveRoleGrants);
    }
    public UUID getDefaultOrganizationId() { return defaultOrganizationId; }
    public void setDefaultOrganizationId(UUID defaultOrganizationId) { this.defaultOrganizationId = defaultOrganizationId; }
    public String getDefaultDestination() { return defaultDestination; }
    public void setDefaultDestination(String defaultDestination) { this.defaultDestination = defaultDestination; }
    public List<String> getAvailableDestinations() { return availableDestinations; }
    public void setAvailableDestinations(List<String> availableDestinations) {
        this.availableDestinations = availableDestinations == null ? List.of("/workspace") : List.copyOf(availableDestinations);
    }
    public TenantContext getTenantContext() { return tenantContext; }
    public void setTenantContext(TenantContext tenantContext) { this.tenantContext = tenantContext; }

    /** Minimal user identity; never includes password or credential material. */
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

    /** Non-secret tenant navigation context used by the client bootstrap. */
    public static class TenantContext {
        private UUID tenantId;
        private UUID defaultOrganizationId;

        public TenantContext() {
        }

        public TenantContext(UUID tenantId, UUID defaultOrganizationId) {
            this.tenantId = tenantId;
            this.defaultOrganizationId = defaultOrganizationId;
        }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        public UUID getDefaultOrganizationId() { return defaultOrganizationId; }
        public void setDefaultOrganizationId(UUID defaultOrganizationId) { this.defaultOrganizationId = defaultOrganizationId; }
    }
}
