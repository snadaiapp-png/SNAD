package com.sanad.platform.organization.exception;

import java.util.UUID;

/**
 * Thrown when attempting to create an Organization whose (tenantId, name)
 * pair already exists in the database.
 *
 * <p>Backed by the unique constraint {@code uk_organizations_tenant_name}
 * defined in {@code V2__create_organizations_table.sql}, but the
 * application service performs an explicit pre-check via
 * {@code OrganizationRepository.existsByTenantIdAndName(...)} so that
 * this exception is raised before any INSERT is attempted, producing a
 * clearer error than the raw {@code DataIntegrityViolationException}
 * that would otherwise bubble up from JPA.</p>
 */
public class OrganizationAlreadyExistsException extends RuntimeException {

    private final UUID tenantId;
    private final String name;

    public OrganizationAlreadyExistsException(UUID tenantId, String name) {
        super("Organization already exists for this tenant");
        this.tenantId = tenantId;
        this.name = name;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }
}
