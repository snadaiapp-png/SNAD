package com.sanad.platform.organization.membership.dto;

import com.sanad.platform.organization.membership.domain.MembershipStatus;

import java.time.Instant;
import java.util.UUID;

public class OrganizationMembershipResponse {

    private UUID id;
    private UUID tenantId;
    private UUID organizationId;
    private UUID userId;
    private String email;
    private String displayName;
    private MembershipStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public OrganizationMembershipResponse() {
    }

    public OrganizationMembershipResponse(UUID id, UUID tenantId, UUID organizationId,
                                          String email, String displayName,
                                          MembershipStatus status,
                                          Instant createdAt, Instant updatedAt) {
        this(id, tenantId, organizationId, null, email, displayName,
                status, createdAt, updatedAt);
    }

    public OrganizationMembershipResponse(UUID id, UUID tenantId, UUID organizationId,
                                          UUID userId, String email, String displayName,
                                          MembershipStatus status,
                                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "OrganizationMembershipResponse{id=" + id + ", tenantId=" + tenantId
                + ", organizationId=" + organizationId + ", userId=" + userId
                + ", email='" + email + "', status=" + status + "}";
    }
}
