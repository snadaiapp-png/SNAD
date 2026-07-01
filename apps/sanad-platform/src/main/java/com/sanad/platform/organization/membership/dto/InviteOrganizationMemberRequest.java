package com.sanad.platform.organization.membership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inbound request payload for the {@code inviteMember} use case.
 *
 * <p>Stage 04A §18: The {@code tenantId} field has been REMOVED.
 * The tenant is established server-side from the verified TenantContext.</p>
 *
 * <p>The {@code organizationId} field is retained because it identifies
 * the parent resource within the tenant scope — it is not a tenant
 * assignment field. The service validates that the organization belongs
 * to the verified tenant before creating the membership.</p>
 */
public class InviteOrganizationMemberRequest {

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

    public InviteOrganizationMemberRequest(UUID organizationId,
                                           String email, String displayName) {
        this.organizationId = organizationId;
        this.email = email;
        this.displayName = displayName;
    }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Override
    public String toString() {
        return "InviteOrganizationMemberRequest{organizationId=" + organizationId
                + ", email='" + email + '\'' + ", displayName='" + displayName + "'}";
    }
}
