package com.sanad.platform.organization.membership.exception;

import java.util.UUID;

/**
 * Thrown when a membership lookup by (tenantId, organizationId, membershipId)
 * returns no result. This may be because the membership does not exist, OR
 * because it exists but belongs to a different tenant/organization — the
 * service intentionally returns the same error for both cases to avoid
 * leaking existence information across tenant boundaries.
 */
public class OrganizationMembershipNotFoundException extends RuntimeException {

    private final UUID tenantId;
    private final UUID organizationId;
    private final UUID membershipId;

    public OrganizationMembershipNotFoundException(UUID tenantId, UUID organizationId, UUID membershipId) {
        super("Organization membership not found");
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.membershipId = membershipId;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getMembershipId() { return membershipId; }
}
