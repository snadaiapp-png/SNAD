package com.sanad.platform.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound request payload for the {@code updateTenant} use case.
 *
 * <p>Only {@code name} is mutable on a Tenant. The {@code subdomain}
 * is immutable because it is the routing key — changing it would break
 * every saved URL, bookmark, and external reference. Subdomain
 * reassignment, if ever needed, would require a dedicated transfer
 * operation not exposed at this stage.</p>
 */
public class UpdateTenantRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    public UpdateTenantRequest() {
    }

    public UpdateTenantRequest(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return "UpdateTenantRequest{name='" + name + "'}";
    }
}
