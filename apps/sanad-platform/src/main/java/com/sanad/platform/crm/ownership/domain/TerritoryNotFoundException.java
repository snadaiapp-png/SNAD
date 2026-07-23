package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class TerritoryNotFoundException extends OwnershipDomainException {
    public TerritoryNotFoundException(UUID tenantId, UUID territoryId) {
        super("Territory not found: tenant=" + tenantId + " territory=" + territoryId);
    }
}
