package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side port for ownership queries.
 *
 * <p>This port is the authoritative read path for "who owns this CRM record right now".
 * It is intentionally separate from {@link OwnershipWritePort} so that read paths
 * (My Work, record details, RBAC checks) never acquire write locks.</p>
 *
 * <p>Implementation note: the JDBC adapter will read from {@code crm_assignments}
 * (the current active row) plus denormalized {@code owner_user_id} columns on
 * the CRM record tables for fast lookup.</p>
 */
public interface OwnershipReadPort {

    /**
     * Returns the current active assignment for the given record, or empty if none.
     *
     * @param tenantId  the tenant scope (from SecurityContext, never from request body)
     * @param recordType the CRM record type
     * @param recordId  the CRM record id
     */
    Optional<Assignment> findActiveAssignment(UUID tenantId, AssignmentRecordType recordType, UUID recordId);

    /**
     * Returns the paginated ownership history for a record, ordered by effectiveAt ASC.
     * The history is append-only and never redacted.
     */
    OwnershipHistoryPage findOwnershipHistory(
            UUID tenantId,
            AssignmentRecordType recordType,
            UUID recordId,
            OwnershipHistoryCursor cursor,
            int pageSize
    );

    /**
     * Returns the workload summary for a user: counts of records currently owned
     * by the user, grouped by record type. Used by the assignment engine's
     * Least-Loaded distribution method.
     */
    WorkloadSummary findUserWorkload(UUID tenantId, UUID userId);

    /**
     * Returns the current claim count for a user against a specific queue.
     * Used by {@code Queue.claim} to enforce {@code maxItemsPerUser}.
     */
    int findUserQueueClaimCount(UUID tenantId, UUID queueId, UUID userId);
}
