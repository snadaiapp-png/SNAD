package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class TeamCodeConflictException extends OwnershipDomainException {
    public TeamCodeConflictException(UUID tenantId, String code) {
        super("Team code already exists in tenant: tenant=" + tenantId + " code=" + code);
    }
}
