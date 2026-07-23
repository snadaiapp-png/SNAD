package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class ConcurrentClaimConflictException extends OwnershipDomainException {
    public ConcurrentClaimConflictException(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        super("Concurrent claim conflict: tenant=" + tenantId + " type=" + recordType + " record=" + recordId);
    }
}
