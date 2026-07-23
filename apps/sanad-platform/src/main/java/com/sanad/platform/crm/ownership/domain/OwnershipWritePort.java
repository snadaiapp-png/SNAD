package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Write-side port for ownership mutations (CRM-008B).
 *
 * <p>Implemented by JdbcOwnershipWriteAdapter. All methods are transactional
 * and produce an immutable ownership history row as a side effect.</p>
 */
public interface OwnershipWritePort {

    /** Assign a CRM record to an owner. Supersedes any prior active assignment. */
    Assignment assign(AssignmentCommand command);

    /** Execute an approved transfer atomically. */
    void executeTransfer(TransferExecutionCommand command);

    /** Claim a queue item for a user. Throws ConcurrentClaimConflictException on conflict. */
    Assignment claimQueueItem(QueueClaimCommand command);

    /** Release a queue item back to the queue. */
    void releaseQueueItem(QueueReleaseCommand command);

    /** Command for assigning a record. */
    record AssignmentCommand(
            UUID tenantId,
            AssignmentRecordType recordType,
            UUID recordId,
            OwnerType ownerType,
            UUID ownerUserId,
            UUID ownerTeamId,
            UUID ownerQueueId,
            UUID actorUserId,
            UUID assignedByRuleId,
            String reason,
            UUID correlationId,
            TriggerSource triggerSource
    ) {}

    /** Command for executing a transfer. */
    record TransferExecutionCommand(
            UUID tenantId,
            UUID transferRequestId,
            UUID executedByUserId
    ) {}

    /** Command for claiming a queue item. */
    record QueueClaimCommand(
            UUID tenantId,
            UUID queueId,
            AssignmentRecordType recordType,
            UUID recordId,
            UUID userId,
            UUID correlationId
    ) {}

    /** Command for releasing a queue item. */
    record QueueReleaseCommand(
            UUID tenantId,
            UUID queueId,
            AssignmentRecordType recordType,
            UUID recordId,
            UUID userId,
            String reason,
            UUID correlationId
    ) {}
}
