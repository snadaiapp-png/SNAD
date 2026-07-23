package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class TerritoryCycleException extends OwnershipDomainException {
    public TerritoryCycleException(UUID tenantId, UUID territoryId, UUID proposedParentId) {
        super("Territory cycle detected: tenant=" + tenantId + " territory=" + territoryId + " proposedParent=" + proposedParentId);
    }
}
