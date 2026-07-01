package com.sanad.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound request payload for the {@code createOrganization} use case.
 *
 * <p>Stage 04A §18: The {@code tenantId} field has been REMOVED from
 * this DTO. The tenant is established server-side from the verified
 * TenantContext — the client cannot assign a tenant.</p>
 *
 * <p>If the client sends a {@code tenantId} field in the JSON body,
 * it will be silently ignored by Jackson (no setter exists). The
 * service layer always uses {@code TenantResolver.requireTenantId()}
 * to obtain the verified tenant.</p>
 */
public class CreateOrganizationRequest {

    /** Human-readable organization name. Must not be blank, max 200 chars. */
    @NotBlank(message = "name must not be blank")
    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    /** Optional longer description. Max 1000 chars. */
    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    /** Default constructor for JSON deserialization frameworks. */
    public CreateOrganizationRequest() {
    }

    public CreateOrganizationRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "CreateOrganizationRequest{" +
                "name='" + name + '\'' +
                ", description='" + (description != null && description.length() > 50
                        ? description.substring(0, 50) + "..." : description) + '\'' +
                '}';
    }
}
