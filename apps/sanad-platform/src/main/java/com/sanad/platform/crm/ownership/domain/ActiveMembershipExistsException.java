package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class ActiveMembershipExistsException extends OwnershipDomainException {
    public ActiveMembershipExistsException(UUID tenantId, UUID teamId, UUID userId) {
        super("Active membership already exists: tenant=" + tenantId + " team=" + teamId + " user=" + userId);
    }
}
