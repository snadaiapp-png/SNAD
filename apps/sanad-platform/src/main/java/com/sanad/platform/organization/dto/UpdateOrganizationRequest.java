package com.sanad.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound request payload for the {@code updateOrganization} use case.
 *
 * <p>Only {@code name} and {@code description} are mutable on an
 * Organization. The tenant relationship and the lifecycle status
 * cannot be changed through this DTO — status transitions go through
 * the dedicated activate/deactivate endpoints, and tenant reassignment
 * is intentionally not supported at this stage.</p>
 */
public class UpdateOrganizationRequest {

    /** New human-readable name. Must not be blank, max 200 chars. */
    @NotBlank(message = "name must not be blank")
    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    /** New description. May be null to clear the existing description. Max 1000 chars. */
    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    public UpdateOrganizationRequest() {
    }

    public UpdateOrganizationRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

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
        return "UpdateOrganizationRequest{" +
                "name='" + name + '\'' +
                ", description='" + (description != null && description.length() > 50
                        ? description.substring(0, 50) + "..." : description) + '\'' +
                '}';
    }
}
