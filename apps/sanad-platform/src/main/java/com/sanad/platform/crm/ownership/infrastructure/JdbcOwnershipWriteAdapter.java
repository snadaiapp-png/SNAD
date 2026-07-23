package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Write-side adapter implementing OwnershipWritePort. */
@Component
public class JdbcOwnershipWriteAdapter implements OwnershipWritePort {

    private final AssignmentRepository assignmentRepo;
    private final OwnershipHistoryRepository historyRepo;
    private final TransferRequestRepository transferRepo;

    public JdbcOwnershipWriteAdapter(AssignmentRepository assignmentRepo,
                                     OwnershipHistoryRepository historyRepo,
                                     TransferRequestRepository transferRepo) {
        this.assignmentRepo = assignmentRepo;
        this.historyRepo = historyRepo;
        this.transferRepo = transferRepo;
    }

    @Override
    @Transactional
    public Assignment assign(AssignmentCommand cmd) {
        Assignment newAssignment = new Assignment(
                null, cmd.tenantId(), 0,
                cmd.recordType().name(), cmd.recordId(), cmd.ownerUserId(), "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, null,
                cmd.ownerType(), cmd.ownerUserId(), cmd.ownerTeamId(), cmd.ownerQueueId(),
                cmd.recordType(), cmd.recordId(), cmd.assignedByRuleId(), cmd.actorUserId(),
                cmd.correlationId(), null, Instant.now(), null,
                Instant.now(), Instant.now(), cmd.actorUserId(), cmd.actorUserId()
        );
        return assignmentRepo.supersedeAndInsert(
                cmd.tenantId(), cmd.recordType(), cmd.recordId(),
                newAssignment, cmd.actorUserId(), cmd.reason());
    }

    @Override
    @Transactional
    public void executeTransfer(TransferExecutionCommand cmd) {
        TransferRequest request = transferRepo.findById(cmd.tenantId(), cmd.transferRequestId())
                .orElseThrow(() -> new OwnershipDomainException("Transfer not found: " + cmd.transferRequestId()));
        if (request.state() != TransferState.APPROVED) {
            throw new TransferStateConflictException(cmd.tenantId(), cmd.transferRequestId(),
                    request.state(), TransferState.COMPLETED);
        }
        // Execute the transfer for each record
        for (UUID recordId : request.recordIds()) {
            Assignment current = assignmentRepo.findActive(cmd.tenantId(), request.recordType(), recordId)
                    .orElseThrow(() -> new OwnershipDomainException(
                            "No active assignment for record: " + recordId));

            Assignment newAssignment = new Assignment(
                    null, cmd.tenantId(), 0,
                    request.recordType().name(), recordId,
                    request.proposedOwnerUserId() != null ? request.proposedOwnerUserId() : current.assignedUserId(),
                    "OWNER", AssignmentStatus.ACTIVE, Instant.now(), null, null,
                    request.proposedOwnerUserId() != null ? OwnerType.USER :
                        (request.proposedOwnerTeamId() != null ? OwnerType.TEAM : current.ownerType()),
                    request.proposedOwnerUserId(), request.proposedOwnerTeamId(), null,
                    request.recordType(), recordId, null, cmd.executedByUserId(),
                    UUID.randomUUID(),
                    null, Instant.now(), null,
                    Instant.now(), Instant.now(), cmd.executedByUserId(), cmd.executedByUserId()
            );
            assignmentRepo.supersedeAndInsert(
                    cmd.tenantId(), request.recordType(), recordId,
                    newAssignment, cmd.executedByUserId(), "Transfer executed: " + request.reason());
        }
        transferRepo.updateState(cmd.tenantId(), cmd.transferRequestId(),
                TransferState.COMPLETED, cmd.executedByUserId(), null);
    }

    @Override
    @Transactional
    public Assignment claimQueueItem(QueueClaimCommand cmd) {
        // Check for existing active assignment (concurrent claim detection)
        var existing = assignmentRepo.findActive(cmd.tenantId(), cmd.recordType(), cmd.recordId());
        if (existing.isPresent() && existing.get().ownerQueueId() != null) {
            throw new ConcurrentClaimConflictException(cmd.tenantId(), cmd.recordType(), cmd.recordId());
        }

        Assignment claim = new Assignment(
                null, cmd.tenantId(), 0,
                cmd.recordType().name(), cmd.recordId(), cmd.userId(), "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, null,
                OwnerType.QUEUE, cmd.userId(), null, cmd.queueId(),
                cmd.recordType(), cmd.recordId(), null, cmd.userId(),
                cmd.correlationId(), null, Instant.now(), null,
                Instant.now(), Instant.now(), cmd.userId(), cmd.userId()
        );
        Assignment saved = assignmentRepo.save(claim);

        // Append ownership history
        historyRepo.append(new OwnershipHistory(
                UUID.randomUUID(), cmd.tenantId(), cmd.recordType(), cmd.recordId(),
                null, null, null, cmd.queueId(),
                OwnerType.USER, cmd.userId(), null, null,
                ChangeType.QUEUE_CLAIM, TriggerSource.MANUAL, cmd.queueId(),
                cmd.userId(), "Queue claim", cmd.correlationId(),
                Instant.now(), Instant.now()
        ));
        return saved;
    }

    @Override
    @Transactional
    public void releaseQueueItem(QueueReleaseCommand cmd) {
        Assignment current = assignmentRepo.findActive(cmd.tenantId(), cmd.recordType(), cmd.recordId())
                .orElseThrow(() -> new AssignmentNotFoundException(cmd.tenantId(), cmd.recordType(), cmd.recordId()));

        assignmentRepo.endAssignment(cmd.tenantId(), current.id(), cmd.userId(), cmd.reason());

        historyRepo.append(new OwnershipHistory(
                UUID.randomUUID(), cmd.tenantId(), cmd.recordType(), cmd.recordId(),
                current.ownerType(), current.ownerUserId(), current.ownerTeamId(), current.ownerQueueId(),
                OwnerType.QUEUE, null, null, cmd.queueId(),
                ChangeType.QUEUE_RELEASE, TriggerSource.MANUAL, cmd.queueId(),
                cmd.userId(), cmd.reason(), cmd.correlationId(),
                Instant.now(), Instant.now()
        ));
    }
}
