package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when a mutation targets an archived sales team. */
public class TeamArchivedException extends OwnershipDomainException {

    public TeamArchivedException(UUID tenantId, UUID teamId) {
        super("Sales team is archived: tenant=" + tenantId + ", team=" + teamId);
    }
}
