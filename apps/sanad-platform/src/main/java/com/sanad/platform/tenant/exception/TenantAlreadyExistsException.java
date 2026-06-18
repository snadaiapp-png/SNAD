package com.sanad.platform.tenant.exception;

/**
 * Thrown when attempting to create a Tenant whose subdomain is already
 * in use by another Tenant.
 *
 * <p>Backed by the unique constraint {@code uk_tenants_subdomain} defined
 * in {@code V1__create_tenants_table.sql}, but the application service
 * performs an explicit pre-check via
 * {@code TenantRepository.existsBySubdomain(...)} so that this exception
 * is raised before any INSERT is attempted.</p>
 */
public class TenantAlreadyExistsException extends RuntimeException {

    private final String subdomain;

    public TenantAlreadyExistsException(String subdomain) {
        super("Tenant already exists with subdomain: " + subdomain);
        this.subdomain = subdomain;
    }

    public String getSubdomain() {
        return subdomain;
    }
}
