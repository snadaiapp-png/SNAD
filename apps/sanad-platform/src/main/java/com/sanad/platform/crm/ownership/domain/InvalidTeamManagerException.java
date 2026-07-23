package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when a team manager is not an ACTIVE user in the same tenant. */
public class InvalidTeamManagerException extends OwnershipDomainException {

    public InvalidTeamManagerException(UUID tenantId, UUID managerUserId) {
        super("Manager must be an ACTIVE user in the same tenant: tenant="
                + tenantId + ", manager=" + managerUserId);
    }
}
