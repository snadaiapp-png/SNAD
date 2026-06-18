package com.sanad.platform.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound request payload for the {@code createTenant} use case.
 *
 * <p>A Tenant is the top-level isolation boundary in the SANAD platform;
 * it has no parent aggregate. Callers identify a Tenant by its
 * {@code subdomain} (the URL-safe routing key), which must be unique
 * across the entire platform.</p>
 */
public class CreateTenantRequest {

    /** Human-readable tenant name (e.g. "Acme Corporation"). */
    @NotBlank(message = "name must not be blank")
    @Size(max = 200, message = "name must be at most 200 characters")
    private String name;

    /**
     * URL-safe subdomain used for tenant routing. Lowercase letters,
     * digits, and hyphens only; must start and end with a letter or digit.
     */
    @NotBlank(message = "subdomain must not be blank")
    @Size(max = 63, message = "subdomain must be at most 63 characters")
    @Pattern(
            regexp = "^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$",
            message = "subdomain must be 1-63 chars, lowercase alphanumeric with optional internal hyphens"
    )
    private String subdomain;

    public CreateTenantRequest() {
    }

    public CreateTenantRequest(String name, String subdomain) {
        this.name = name;
        this.subdomain = subdomain;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubdomain() { return subdomain; }
    public void setSubdomain(String subdomain) { this.subdomain = subdomain; }

    @Override
    public String toString() {
        return "CreateTenantRequest{name='" + name + "', subdomain='" + subdomain + "'}";
    }
}
