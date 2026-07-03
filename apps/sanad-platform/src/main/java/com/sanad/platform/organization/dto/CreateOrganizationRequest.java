package com.sanad.platform.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inbound request payload for the {@code createOrganization} use case.
 *
 * <p>This DTO is the contract between the future transport layer
 * (REST controllers, message handlers, etc.) and the application service
 * layer. It is intentionally unaware of persistence details — callers
 * identify the parent Tenant by its UUID only, and the service layer
 * is responsible for resolving that UUID into a {@code Tenant} entity
 * before constructing the {@code Organization} aggregate (per the
 * DDD aggregate-consistency rule enforced by EXEC-PROMPT-005-FIX).</p>
 *
 * <p>Validation annotations are evaluated by Spring's
 * {@code @Valid} mechanism at the controller boundary (when one is
 * added in a later stage). The service layer also performs defensive
 * validation as a second line of defence.</p>
 */
public class CreateOrganizationRequest {

    /** UUID of the parent Tenant. Must not be null. */
    @NotNull(message = "tenantId must not be null")
    private UUID tenantId;

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

    public CreateOrganizationRequest(UUID tenantId, String name, String description) {
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
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
        return "CreateOrganizationRequest{" +
                "tenantId=" + tenantId +
                ", name='" + name + '\'' +
                ", description='" + (description != null && description.length() > 50
                        ? description.substring(0, 50) + "..." : description) + '\'' +
                '}';
    }
}
