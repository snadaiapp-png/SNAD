package com.sanad.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/auth/me}.
 *
 * <p>Returns the authenticated user's identity, their organization
 * memberships, and their active role grants. This is the session
 * bootstrap payload for the frontend.</p>
 */
public class MeResponse {

    private UUID id;
    private UUID tenantId;
    private String email;
    private String displayName;
    private String status;
    private Instant lastLoginAt;
    private List<MembershipSummary> memberships;
    private List<RoleGrantSummary> roleGrants;

    public MeResponse() {
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

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

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public List<MembershipSummary> getMemberships() { return memberships; }
    public void setMemberships(List<MembershipSummary> memberships) { this.memberships = memberships; }

    public List<RoleGrantSummary> getRoleGrants() { return roleGrants; }
    public void setRoleGrants(List<RoleGrantSummary> roleGrants) { this.roleGrants = roleGrants; }

    // ------------------------------------------------------------
    // Nested summaries
    // ------------------------------------------------------------

    public static class MembershipSummary {
        private UUID id;
        private UUID organizationId;
        private String status;

        public MembershipSummary() {
        }

        public MembershipSummary(UUID id, UUID organizationId, String status) {
            this.id = id;
            this.organizationId = organizationId;
            this.status = status;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class RoleGrantSummary {
        private UUID id;
        private UUID roleId;
        private String roleCode;
        private UUID organizationId;
        private String status;

        public RoleGrantSummary() {
        }

        public RoleGrantSummary(UUID id, UUID roleId, String roleCode, UUID organizationId, String status) {
            this.id = id;
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.organizationId = organizationId;
            this.status = status;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public UUID getRoleId() { return roleId; }
        public void setRoleId(UUID roleId) { this.roleId = roleId; }

        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
