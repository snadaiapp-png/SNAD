package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Write-side port for ownership mutations.
 *
 * <p>All methods on this port are transactional and produce an immutable
 * {@link OwnershipHistoryEntry} as a side effect. No method ever deletes or
 * updates an existing ownership history row.</p>
 */
public interface OwnershipWritePort {

    /**
     * Persists a new active assignment for the given record.
     *
     * <p>Side effects:
     * <ul>
     *   <li>The previous active assignment (if any) is marked SUPERSEDED.</li>
     *   <li>The {@code owner_user_id} (or {@code owner_team_id} / {@code owner_queue_id})
     *       column on the CRM record table is updated for read-path fast lookup.</li>
     *   <li>An {@link OwnershipHistoryEntry} is appended to {@code crm_ownership_history}.</li>
     *   <li>An audit event is written to {@code platform_audit_logs} with the same
     *       correlation id.</li>
     * </ul>
     * </p>
     *
     * @param command the assignment command (validated upstream)
     * @return the persisted assignment (with generated id and timestamps)
     */
    Assignment assign(AssignmentCommand command);

    /**
     * Executes a transfer request atomically.
     *
     * <p>Atomicity: all record ids in the transfer must succeed or all fail.
     * Partial completion is forbidden.</p>
     *
     * @throws TransferExecutionException if any single record's ownership change fails
     *         (the entire transaction rolls back)
     */
    TransferExecutionResult executeTransfer(TransferExecutionCommand command);

    /**
     * Claims a queue item for the given user.
     *
     * <p>Concurrency: uses {@code SELECT ... FOR UPDATE} on the queue item row
     * to serialize concurrent claims. Exactly one claim succeeds; concurrent claims
     * receive a {@link ConcurrentClaimConflictException}.</p>
     *
     * @throws ConcurrentClaimConflictException if another user claimed the item first
     * @throws QueueCapacityExceededException if the user has reached {@code maxItemsPerUser}
     */
    QueueClaimResult claimQueueItem(QueueClaimCommand command);

    /**
     * Releases a queue item back to the queue.
     */
    void releaseQueueItem(QueueReleaseCommand command);
}
