package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentNotFoundException;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.AssignmentStatus;
import com.sanad.platform.crm.ownership.domain.ChangeType;
import com.sanad.platform.crm.ownership.domain.ConcurrentClaimConflictException;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueCapacityExceededException;
import com.sanad.platform.crm.ownership.domain.QueueMembershipRepository;
import com.sanad.platform.crm.ownership.domain.QueueNotFoundException;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.TransferRequest;
import com.sanad.platform.crm.ownership.domain.TransferRequestRepository;
import com.sanad.platform.crm.ownership.domain.TransferState;
import com.sanad.platform.crm.ownership.domain.TransferStateConflictException;
import com.sanad.platform.crm.ownership.domain.TriggerSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Write-side adapter implementing atomic CRM-008B ownership mutations. */
@Component
public class JdbcOwnershipWriteAdapter implements OwnershipWritePort {

    private final AssignmentRepository assignmentRepo;
    private final TransferRequestRepository transferRepo;
    private final QueueRepository queueRepo;
    private final QueueMembershipRepository queueMembershipRepo;

    public JdbcOwnershipWriteAdapter(AssignmentRepository assignmentRepo,
                                     TransferRequestRepository transferRepo,
                                     QueueRepository queueRepo,
                                     QueueMembershipRepository queueMembershipRepo) {
        this.assignmentRepo = assignmentRepo;
        this.transferRepo = transferRepo;
        this.queueRepo = queueRepo;
        this.queueMembershipRepo = queueMembershipRepo;
    }

    @Override
    @Transactional
    public Assignment assign(AssignmentCommand command) {
        UUID legacyAssignedUserId = legacyAssignedUser(command);
        Assignment newAssignment = new Assignment(
                null, command.tenantId(), 0,
                command.recordType().name(), command.recordId(), legacyAssignedUserId, "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, normalizeReason(command.reason()),
                command.ownerType(), command.ownerUserId(), command.ownerTeamId(), command.ownerQueueId(),
                command.recordType(), command.recordId(), command.assignedByRuleId(), command.actorUserId(),
                command.correlationId(), null, Instant.now(), null,
                Instant.now(), Instant.now(), command.actorUserId(), command.actorUserId());

        TriggerSource source = command.triggerSource() != null
                ? command.triggerSource()
                : TriggerSource.MANUAL;
        UUID referenceId = source == TriggerSource.RULE ? command.assignedByRuleId() : null;

        return assignmentRepo.supersedeAndInsert(
                command.tenantId(), command.recordType(), command.recordId(),
                newAssignment, command.actorUserId(), normalizeReason(command.reason()),
                ChangeType.REASSIGN, source, referenceId);
    }

    private UUID legacyAssignedUser(AssignmentCommand command) {
        if (command.ownerType() == OwnerType.USER) {
            if (command.ownerUserId() == null) {
                throw new OwnershipDomainException("USER ownership requires ownerUserId");
            }
            return command.ownerUserId();
        }
        if (command.ownerType() == OwnerType.QUEUE) {
            Queue queue = requireQueue(command.tenantId(), command.ownerQueueId());
            if (!queue.recordType().name().equals(command.recordType().name())) {
                throw new OwnershipDomainException("Queue does not accept record type " + command.recordType());
            }
            return queue.defaultOwnerId() != null ? queue.defaultOwnerId() : command.actorUserId();
        }
        return command.actorUserId();
    }

    @Override
    @Transactional
    public void executeTransfer(TransferExecutionCommand command) {
        TransferRequest request = transferRepo.findById(command.tenantId(), command.transferRequestId())
                .orElseThrow(() -> new OwnershipDomainException(
                        "Transfer not found: " + command.transferRequestId()));
        if (request.state() != TransferState.APPROVED) {
            throw new TransferStateConflictException(
                    command.tenantId(), command.transferRequestId(),
                    request.state(), TransferState.COMPLETED);
        }

        for (UUID recordId : request.recordIds()) {
            Assignment current = assignmentRepo.findActive(
                            command.tenantId(), request.recordType(), recordId)
                    .orElseThrow(() -> new AssignmentNotFoundException(
                            command.tenantId(), request.recordType(), recordId));

            OwnerType targetType = request.proposedOwnerUserId() != null
                    ? OwnerType.USER
                    : (request.proposedOwnerTeamId() != null ? OwnerType.TEAM : current.ownerType());
            UUID targetLegacyUser = request.proposedOwnerUserId() != null
                    ? request.proposedOwnerUserId()
                    : current.assignedUserId();

            Assignment replacement = new Assignment(
                    null, command.tenantId(), 0,
                    request.recordType().name(), recordId, targetLegacyUser,
                    "OWNER", AssignmentStatus.ACTIVE, Instant.now(), null,
                    normalizeReason(request.reason()),
                    targetType, request.proposedOwnerUserId(), request.proposedOwnerTeamId(), null,
                    request.recordType(), recordId, null, command.executedByUserId(),
                    UUID.randomUUID(), null, Instant.now(), null,
                    Instant.now(), Instant.now(), command.executedByUserId(), command.executedByUserId());

            assignmentRepo.supersedeAndInsert(
                    command.tenantId(), request.recordType(), recordId,
                    replacement, command.executedByUserId(),
                    "Transfer executed: " + normalizeReason(request.reason()),
                    ChangeType.TRANSFER, TriggerSource.TRANSFER_REQUEST,
                    command.transferRequestId());
        }

        transferRepo.updateState(command.tenantId(), command.transferRequestId(),
                TransferState.COMPLETED, command.executedByUserId(), null);
    }

    @Override
    @Transactional
    public Assignment claimQueueItem(QueueClaimCommand command) {
        Queue queue = requireQueue(command.tenantId(), command.queueId());
        ensureQueueAccepts(queue, command.recordType().name());
        ensureActiveQueueMembership(command.tenantId(), command.queueId(), command.userId());

        long activeWorkload = assignmentRepo.countActiveByUser(command.tenantId(), command.userId());
        if (activeWorkload >= queue.maxItemsPerUser()) {
            throw new QueueCapacityExceededException(
                    command.tenantId(), command.queueId(), command.userId(), queue.maxItemsPerUser());
        }

        Assignment current = assignmentRepo.findActive(
                        command.tenantId(), command.recordType(), command.recordId())
                .orElseThrow(() -> new AssignmentNotFoundException(
                        command.tenantId(), command.recordType(), command.recordId()));

        if (current.ownerType() != OwnerType.QUEUE
                || !command.queueId().equals(current.ownerQueueId())) {
            throw new ConcurrentClaimConflictException(
                    command.tenantId(), command.recordType(), command.recordId());
        }

        Assignment claimed = new Assignment(
                null, command.tenantId(), 0,
                command.recordType().name(), command.recordId(), command.userId(), "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, "Queue claim",
                OwnerType.USER, command.userId(), null, null,
                command.recordType(), command.recordId(), null, command.userId(),
                command.correlationId(), null, Instant.now(), null,
                Instant.now(), Instant.now(), command.userId(), command.userId());

        return assignmentRepo.supersedeAndInsert(
                command.tenantId(), command.recordType(), command.recordId(),
                claimed, command.userId(), "Queue claim",
                ChangeType.QUEUE_CLAIM, TriggerSource.MANUAL, command.queueId());
    }

    @Override
    @Transactional
    public void releaseQueueItem(QueueReleaseCommand command) {
        Queue queue = requireQueue(command.tenantId(), command.queueId());
        ensureQueueAccepts(queue, command.recordType().name());
        ensureActiveQueueMembership(command.tenantId(), command.queueId(), command.userId());

        Assignment current = assignmentRepo.findActive(
                        command.tenantId(), command.recordType(), command.recordId())
                .orElseThrow(() -> new AssignmentNotFoundException(
                        command.tenantId(), command.recordType(), command.recordId()));

        if (current.ownerType() != OwnerType.USER
                || !command.userId().equals(current.ownerUserId())) {
            throw new ConcurrentClaimConflictException(
                    command.tenantId(), command.recordType(), command.recordId());
        }

        UUID legacyAssignedUser = queue.defaultOwnerId() != null
                ? queue.defaultOwnerId()
                : command.userId();
        Assignment released = new Assignment(
                null, command.tenantId(), 0,
                command.recordType().name(), command.recordId(), legacyAssignedUser, "OWNER",
                AssignmentStatus.ACTIVE, Instant.now(), null, normalizeReason(command.reason()),
                OwnerType.QUEUE, null, null, command.queueId(),
                command.recordType(), command.recordId(), null, command.userId(),
                command.correlationId(), null, Instant.now(), null,
                Instant.now(), Instant.now(), command.userId(), command.userId());

        assignmentRepo.supersedeAndInsert(
                command.tenantId(), command.recordType(), command.recordId(),
                released, command.userId(), normalizeReason(command.reason()),
                ChangeType.QUEUE_RELEASE, TriggerSource.MANUAL, command.queueId());
    }

    private Queue requireQueue(UUID tenantId, UUID queueId) {
        if (queueId == null) {
            throw new QueueNotFoundException(tenantId, null);
        }
        Queue queue = queueRepo.findById(tenantId, queueId)
                .orElseThrow(() -> new QueueNotFoundException(tenantId, queueId));
        if (!queue.isClaimable()) {
            throw new OwnershipDomainException("Queue is not active: " + queueId);
        }
        return queue;
    }

    private void ensureQueueAccepts(Queue queue, String recordType) {
        if (!queue.recordType().name().equals(recordType)) {
            throw new OwnershipDomainException(
                    "Queue " + queue.id() + " does not accept record type " + recordType);
        }
    }

    private void ensureActiveQueueMembership(UUID tenantId, UUID queueId, UUID userId) {
        queueMembershipRepo.findActive(tenantId, queueId, userId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active queue membership required: queue=" + queueId + " user=" + userId));
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "Ownership transition" : reason;
    }
}
