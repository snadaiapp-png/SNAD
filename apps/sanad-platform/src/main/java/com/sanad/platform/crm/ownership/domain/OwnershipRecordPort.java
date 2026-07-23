package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Validates CRM records and updates their backward-compatible owner fast-path columns. */
public interface OwnershipRecordPort {

    boolean exists(UUID tenantId, AssignmentRecordType recordType, UUID recordId);

    void updateOwner(UUID tenantId,
                     AssignmentRecordType recordType,
                     UUID recordId,
                     OwnerType ownerType,
                     UUID ownerId);
}
