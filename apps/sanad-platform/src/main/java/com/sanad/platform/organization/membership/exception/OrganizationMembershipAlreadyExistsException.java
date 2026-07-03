package com.sanad.platform.organization.membership.exception;

import java.util.UUID;

/**
 * Thrown when attempting to invite a member whose email is already
 * associated with a membership for the same (tenantId, organizationId) pair.
 */
public class OrganizationMembershipAlreadyExistsException extends RuntimeException {

    private final UUID tenantId;
    private final UUID organizationId;
    private final String email;

    public OrganizationMembershipAlreadyExistsException(UUID tenantId, UUID organizationId, String email) {
        super("Organization membership already exists for this email");
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.email = email;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
}
