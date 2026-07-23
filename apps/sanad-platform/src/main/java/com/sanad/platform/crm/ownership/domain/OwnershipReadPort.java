package com.sanad.platform.crm.ownership.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side port for ownership queries (CRM-008B).
 *
 * <p>Implemented by JdbcOwnershipReadAdapter. All methods are tenant-scoped.</p>
 */
public interface OwnershipReadPort {

    /** Find the current active assignment for a CRM record. */
    Optional<Assignment> findActiveAssignment(UUID tenantId, AssignmentRecordType recordType, UUID recordId);

    /** Find ownership history page with cursor pagination (stable, no skip). */
    OwnershipHistoryPage findOwnershipHistory(UUID tenantId, AssignmentRecordType recordType, UUID recordId,
                                               UUID cursor, int pageSize);

    /** Aggregate workload for a user (active assignments + queue items + team memberships). */
    WorkloadSummary findUserWorkload(UUID tenantId, UUID userId);

    /** Count active queue items claimed by a user. */
    int findUserQueueClaimCount(UUID tenantId, UUID userId);
}
