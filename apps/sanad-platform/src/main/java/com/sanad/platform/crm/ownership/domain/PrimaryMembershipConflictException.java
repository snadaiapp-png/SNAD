package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class PrimaryMembershipConflictException extends OwnershipDomainException {
    public PrimaryMembershipConflictException(UUID tenantId, UUID userId) {
        super("User already has a primary team: tenant=" + tenantId + " user=" + userId);
    }
}
