package com.sanad.platform.crm.ownership.domain;

/**
 * Write-side port for ownership mutations (CRM-008A design).
 *
 * <p><b>Design-only marker interface.</b> The full method set will be declared
 * in CRM-008B (Foundation) when the JDBC adapter is implemented.</p>
 *
 * <p>All methods on this port will be transactional and will produce an immutable
 * ownership history row as a side effect. No method will ever delete or update
 * an existing ownership history row.</p>
 *
 * <p><b>Planned methods</b> (to be added in CRM-008B with their value objects):
 * <pre>
 *   Assignment assign(AssignmentCommand command);
 *   TransferExecutionResult executeTransfer(TransferExecutionCommand command);
 *   QueueClaimResult claimQueueItem(QueueClaimCommand command);
 *   void releaseQueueItem(QueueReleaseCommand command);
 * </pre>
 * </p>
 *
 * <p>Exceptions (to be added in CRM-008B):
 * <ul>
 *   <li>{@code TransferExecutionException} — atomic transfer failure</li>
 *   <li>{@code ConcurrentClaimConflictException} — two users claimed the same item</li>
 *   <li>{@code QueueCapacityExceededException} — user reached maxItemsPerUser</li>
 * </ul>
 * </p>
 */
public interface OwnershipWritePort {
    // Marker interface — methods added in CRM-008B.
    // See Javadoc above for the planned contract.
}
