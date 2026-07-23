package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class TeamNotFoundException extends OwnershipDomainException {
    public TeamNotFoundException(UUID tenantId, UUID teamId) {
        super("Team not found: tenant=" + tenantId + " team=" + teamId);
    }
    public TeamNotFoundException(UUID tenantId, String code) {
        super("Team not found: tenant=" + tenantId + " code=" + code);
    }
}
