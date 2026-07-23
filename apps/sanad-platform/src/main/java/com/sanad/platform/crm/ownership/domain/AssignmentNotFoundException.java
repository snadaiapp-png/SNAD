package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class AssignmentNotFoundException extends OwnershipDomainException {
    public AssignmentNotFoundException(UUID tenantId, AssignmentRecordType recordType, UUID recordId) {
        super("Assignment not found: tenant=" + tenantId + " type=" + recordType + " record=" + recordId);
    }
}
