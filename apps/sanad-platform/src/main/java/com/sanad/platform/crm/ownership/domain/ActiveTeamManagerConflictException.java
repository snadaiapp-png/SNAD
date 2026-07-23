package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when one user would manage more than one ACTIVE team in a tenant. */
public class ActiveTeamManagerConflictException extends OwnershipDomainException {

    public ActiveTeamManagerConflictException(UUID tenantId, UUID managerUserId) {
        super("User already manages an ACTIVE team in tenant: tenant="
                + tenantId + ", manager=" + managerUserId);
    }
}
