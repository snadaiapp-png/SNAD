package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class ArchiveBlockedWithActiveMembershipsException extends OwnershipDomainException {
    public ArchiveBlockedWithActiveMembershipsException(UUID tenantId, UUID teamId, long activeCount) {
        super("Cannot archive team with active memberships: tenant=" + tenantId + " team=" + teamId + " active=" + activeCount);
    }
}
