package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when a tenant-scoped team membership cannot be found. */
public class TeamMembershipNotFoundException extends OwnershipDomainException {

    public TeamMembershipNotFoundException(UUID tenantId, UUID membershipId) {
        super("Team membership not found: tenant=" + tenantId + ", membership=" + membershipId);
    }
}
