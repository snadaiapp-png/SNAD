package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for ownership history (append-only, no update/delete). */
public interface OwnershipHistoryRepository {

    /** INSERT only — no update or delete path is ever exposed. */
    OwnershipHistory append(OwnershipHistory history);

    Optional<OwnershipHistory> findById(UUID tenantId, UUID id);

    List<OwnershipHistory> findByRecord(UUID tenantId, AssignmentRecordType recordType, UUID recordId,
                                        Instant before, int limit);

    List<OwnershipHistory> findByCorrelation(UUID tenantId, UUID correlationId);

    long countByRecord(UUID tenantId, AssignmentRecordType recordType, UUID recordId);
}
