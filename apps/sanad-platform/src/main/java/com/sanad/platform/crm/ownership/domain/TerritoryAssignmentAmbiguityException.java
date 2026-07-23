package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when equal highest-priority territory assignments resolve to different assignees. */
public class TerritoryAssignmentAmbiguityException extends OwnershipDomainException {
    public TerritoryAssignmentAmbiguityException(UUID tenantId, int priority) {
        super("Ambiguous territory assignments for tenant=" + tenantId
                + " at equal highest priority=" + priority);
    }
}
