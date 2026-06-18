package com.sanad.platform.organization.membership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inbound request payload for the {@code inviteMember} use case.
 *
 * <p>The email is normalized to lowercase inside the service layer before
 * the existence check and persistence, so callers may submit any casing
 * without affecting deduplication.</p>
 */
public class InviteOrganizationMemberRequest {

    @NotNull(message = "tenantId must not be null")
    private UUID tenantId;

    @NotNull(message = "organizationId must not be null")
    private UUID organizationId;

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be a valid email address")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @Size(max = 200, message = "displayName must be at most 200 characters")
    private String displayName;

    public InviteOrganizationMemberRequest() {
    }

    public InviteOrganizationMemberRequest(UUID tenantId, UUID organizationId,
                                           String email, String displayName) {
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.email = email;
        this.displayName = displayName;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Override
    public String toString() {
        return "InviteOrganizationMemberRequest{tenantId=" + tenantId
                + ", organizationId=" + organizationId
                + ", email='" + email + '\'' + ", displayName='" + displayName + "'}";
    }
}
