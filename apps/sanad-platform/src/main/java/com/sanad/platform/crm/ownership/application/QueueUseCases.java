package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.Assignment;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentRepository;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.OwnerType;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.OwnershipReadPort;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import com.sanad.platform.crm.ownership.domain.OwnershipWritePort;
import com.sanad.platform.crm.ownership.domain.Queue;
import com.sanad.platform.crm.ownership.domain.QueueClaimIdempotencyPort;
import com.sanad.platform.crm.ownership.domain.QueueItemPage;
import com.sanad.platform.crm.ownership.domain.QueueMembership;
import com.sanad.platform.crm.ownership.domain.QueueMembershipRepository;
import com.sanad.platform.crm.ownership.domain.QueueNotFoundException;
import com.sanad.platform.crm.ownership.domain.QueueRecordType;
import com.sanad.platform.crm.ownership.domain.QueueRepository;
import com.sanad.platform.crm.ownership.domain.QueueStatus;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application service for CRM-008B WP-04 queues, memberships and claims. */
public class QueueUseCases {

    private final QueueRepository queueRepository;
    private final QueueMembershipRepository membershipRepository;
    private final AssignmentRepository assignmentRepository;
    private final OwnershipReadPort ownershipReadPort;
    private final OwnershipWritePort ownershipWritePort;
    private final QueueClaimIdempotencyPort idempotencyPort;
    private final OwnershipUserValidationPort userValidationPort;
    private final AuditPort auditPort;
    private final TimelineEventPort timelineEventPort;
    private final ObjectMapper objectMapper;

    public QueueUseCases(QueueRepository queueRepository,
                         QueueMembershipRepository membershipRepository,
                         AssignmentRepository assignmentRepository,
                         OwnershipReadPort ownershipReadPort,
                         OwnershipWritePort ownershipWritePort,
                         QueueClaimIdempotencyPort idempotencyPort,
                         OwnershipUserValidationPort userValidationPort,
                         AuditPort auditPort,
                         TimelineEventPort timelineEventPort,
                         ObjectMapper objectMapper) {
        this.queueRepository = queueRepository;
        this.membershipRepository = membershipRepository;
        this.assignmentRepository = assignmentRepository;
        this.ownershipReadPort = ownershipReadPort;
        this.ownershipWritePort = ownershipWritePort;
        this.idempotencyPort = idempotencyPort;
        this.userValidationPort = userValidationPort;
        this.auditPort = auditPort;
        this.timelineEventPort = timelineEventPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Queue createQueue(UUID tenantId, UUID actorId, CreateQueueCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.recordType() == null) {
            throw new OwnershipDomainException("Queue create command and recordType are required");
        }
        validateDefaultOwner(tenantId, command.defaultOwnerId());
        Queue queue = new Queue(
                null,
                tenantId,
                normalizeCode(command.code()),
                requireText(command.displayName(), "displayName", 200),
                command.recordType(),
                bounded(command.description(), 1000),
                QueueStatus.ACTIVE,
                command.maxItemsPerUser(),
                command.slaMinutes(),
                command.escalationTargetQueueId(),
                command.defaultOwnerId(),
                null,
                null,
                actorId,
                actorId);
        Queue created = queueRepository.save(queue);
        recordMutation(tenantId, actorId, "CREATE", "QUEUE", created.id(),
                null, json(created), "crm.queue.created", "Queue created");
        return created;
    }

    public Queue getQueue(UUID tenantId, UUID queueId) {
        return requireQueue(tenantId, queueId);
    }

    public List<Queue> listQueues(UUID tenantId, QueueStatus status) {
        requireTenant(tenantId);
        return status == null
                ? queueRepository.findAllByTenant(tenantId)
                : queueRepository.findByTenant(tenantId, status);
    }

    @Transactional
    public Queue updateQueue(UUID tenantId,
                             UUID actorId,
                             UUID queueId,
                             UpdateQueueCommand command) {
        requireTenantActor(tenantId, actorId);
        Queue current = requireQueue(tenantId, queueId);
        if (current.status() == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Archived queue is immutable: " + queueId);
        }
        if (command == null) {
            throw new OwnershipDomainException("Queue update command required");
        }
        QueueStatus targetStatus = command.status() != null ? command.status() : current.status();
        if (targetStatus == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Use archiveQueue for the ARCHIVED transition");
        }
        UUID defaultOwner = command.defaultOwnerIdSet()
                ? command.defaultOwnerId() : current.defaultOwnerId();
        validateDefaultOwner(tenantId, defaultOwner);
        Queue updated = queueRepository.save(new Queue(
                current.id(),
                current.tenantId(),
                current.code(),
                command.displayName() != null
                        ? requireText(command.displayName(), "displayName", 200)
                        : current.displayName(),
                current.recordType(),
                command.descriptionSet() ? bounded(command.description(), 1000) : current.description(),
                targetStatus,
                command.maxItemsPerUser() != null
                        ? command.maxItemsPerUser() : current.maxItemsPerUser(),
                command.slaMinutesSet() ? command.slaMinutes() : current.slaMinutes(),
                command.escalationTargetQueueIdSet()
                        ? command.escalationTargetQueueId() : current.escalationTargetQueueId(),
                defaultOwner,
                current.createdAt(),
                current.updatedAt(),
                current.createdBy(),
                actorId));
        recordMutation(tenantId, actorId, "UPDATE", "QUEUE", updated.id(),
                json(current), json(updated), "crm.queue.updated", "Queue updated");
        return updated;
    }

    @Transactional
    public Queue archiveQueue(UUID tenantId, UUID actorId, UUID queueId) {
        requireTenantActor(tenantId, actorId);
        Queue current = requireQueue(tenantId, queueId);
        if (current.status() == QueueStatus.ARCHIVED) {
            return current;
        }
        long waitingItems = assignmentRepository.countActiveByOwner(tenantId, OwnerType.QUEUE, queueId);
        long activeMemberships = membershipRepository.countActiveByQueue(tenantId, queueId);
        if (waitingItems > 0 || activeMemberships > 0) {
            throw new OwnershipDomainException(
                    "Queue archive requires zero waiting items and zero active memberships: queue="
                            + queueId + " waiting=" + waitingItems + " memberships=" + activeMemberships);
        }
        queueRepository.updateStatus(tenantId, queueId, QueueStatus.ARCHIVED, actorId);
        Queue archived = requireQueue(tenantId, queueId);
        recordMutation(tenantId, actorId, "ARCHIVE", "QUEUE", queueId,
                json(current), json(archived), "crm.queue.archived", "Queue archived");
        return archived;
    }

    public List<QueueMembership> listMemberships(UUID tenantId, UUID queueId) {
        requireQueue(tenantId, queueId);
        return membershipRepository.findActiveByQueue(tenantId, queueId);
    }

    @Transactional
    public QueueMembership addMembership(UUID tenantId,
                                         UUID actorId,
                                         UUID queueId,
                                         UUID userId) {
        requireTenantActor(tenantId, actorId);
        Queue queue = requireQueue(tenantId, queueId);
        if (queue.status() == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Cannot add membership to archived queue: " + queueId);
        }
        if (!userValidationPort.isActiveUser(tenantId, userId)) {
            throw new OwnershipDomainException("Queue member must be ACTIVE in the same tenant: " + userId);
        }
        QueueMembership membership = membershipRepository.save(new QueueMembership(
                null,
                tenantId,
                queueId,
                userId,
                MembershipStatus.ACTIVE,
                Instant.now(),
                null,
                null,
                null,
                null,
                actorId,
                actorId));
        recordMutation(tenantId, actorId, "CREATE", "QUEUE_MEMBERSHIP", membership.id(),
                null, json(membership), "crm.queue_membership.created", "Queue membership created");
        return membership;
    }

    @Transactional
    public QueueMembership removeMembership(UUID tenantId,
                                            UUID actorId,
                                            UUID queueId,
                                            UUID membershipId,
                                            String reason) {
        requireTenantActor(tenantId, actorId);
        requireQueue(tenantId, queueId);
        QueueMembership current = membershipRepository.findById(tenantId, membershipId)
                .filter(membership -> queueId.equals(membership.queueId()))
                .filter(QueueMembership::isActive)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active queue membership not found on queue path: " + membershipId));
        long claims = assignmentRepository.countActiveQueueClaims(
                tenantId, queueId, current.userId());
        if (claims > 0) {
            throw new OwnershipDomainException(
                    "Release active claimed items before removing queue membership: " + claims);
        }
        membershipRepository.remove(
                tenantId,
                membershipId,
                requireText(reason, "removedReason", 100),
                actorId);
        QueueMembership removed = membershipRepository.findById(tenantId, membershipId)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Removed queue membership disappeared: " + membershipId));
        recordMutation(tenantId, actorId, "REMOVE", "QUEUE_MEMBERSHIP", membershipId,
                json(current), json(removed), "crm.queue_membership.removed", "Queue membership removed");
        return removed;
    }

    public QueueItemPage listItems(UUID tenantId,
                                   UUID queueId,
                                   UUID cursor,
                                   int pageSize) {
        requireQueue(tenantId, queueId);
        return ownershipReadPort.findQueueItems(tenantId, queueId, cursor, pageSize);
    }

    @Transactional
    public ClaimResult claim(UUID tenantId,
                             UUID actorId,
                             UUID queueId,
                             ClaimCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.idempotencyKey() == null
                || command.recordType() == null || command.recordId() == null) {
            throw new OwnershipDomainException(
                    "recordType, recordId and idempotencyKey are required for queue claim");
        }
        requireQueue(tenantId, queueId);
        String fingerprint = fingerprint(tenantId, actorId, queueId, command);
        Optional<QueueClaimIdempotencyPort.ClaimRecord> existing = idempotencyPort.reserve(
                tenantId, actorId, command.idempotencyKey(), fingerprint);
        if (existing.isPresent()) {
            QueueClaimIdempotencyPort.ClaimRecord replay = existing.get();
            if (!fingerprint.equals(replay.requestFingerprintSha256())) {
                throw new OwnershipDomainException(
                        "Idempotency key was already used with a different queue claim request");
            }
            if (!replay.isComplete()) {
                throw new OwnershipDomainException("Queue claim with this idempotency key is in progress");
            }
            Assignment assignment = assignmentRepository.findById(tenantId, replay.assignmentId())
                    .orElseThrow(() -> new OwnershipDomainException(
                            "Completed idempotent claim references a missing assignment"));
            return new ClaimResult(assignment, true);
        }

        Assignment assignment = ownershipWritePort.claimQueueItem(
                new OwnershipWritePort.QueueClaimCommand(
                        tenantId,
                        queueId,
                        command.recordType(),
                        command.recordId(),
                        actorId,
                        command.correlationId() != null
                                ? command.correlationId() : UUID.randomUUID()));
        idempotencyPort.complete(
                tenantId, actorId, command.idempotencyKey(), fingerprint, assignment.id());
        recordMutation(tenantId, actorId, "CLAIM", "QUEUE_ITEM", command.recordId(),
                null, json(assignment), "crm.queue_item.claimed", "Queue item claimed");
        return new ClaimResult(assignment, false);
    }

    @Transactional
    public Assignment release(UUID tenantId,
                              UUID actorId,
                              UUID queueId,
                              ReleaseCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.recordType() == null || command.recordId() == null) {
            throw new OwnershipDomainException("recordType and recordId are required for release");
        }
        requireQueue(tenantId, queueId);
        ownershipWritePort.releaseQueueItem(new OwnershipWritePort.QueueReleaseCommand(
                tenantId,
                queueId,
                command.recordType(),
                command.recordId(),
                actorId,
                requireText(command.reason(), "reason", 500),
                command.correlationId() != null
                        ? command.correlationId() : UUID.randomUUID()));
        Assignment assignment = assignmentRepository.findActive(
                        tenantId, command.recordType(), command.recordId())
                .orElseThrow(() -> new OwnershipDomainException(
                        "Released queue assignment not found"));
        recordMutation(tenantId, actorId, "RELEASE", "QUEUE_ITEM", command.recordId(),
                null, json(assignment), "crm.queue_item.released", "Queue item released");
        return assignment;
    }

    private Queue requireQueue(UUID tenantId, UUID queueId) {
        requireTenant(tenantId);
        if (queueId == null) {
            throw new QueueNotFoundException(tenantId, null);
        }
        return queueRepository.findById(tenantId, queueId)
                .orElseThrow(() -> new QueueNotFoundException(tenantId, queueId));
    }

    private void validateDefaultOwner(UUID tenantId, UUID defaultOwnerId) {
        if (defaultOwnerId != null && !userValidationPort.isActiveUser(tenantId, defaultOwnerId)) {
            throw new OwnershipDomainException(
                    "Default queue owner must be ACTIVE in the same tenant: " + defaultOwnerId);
        }
    }

    private String fingerprint(UUID tenantId,
                               UUID actorId,
                               UUID queueId,
                               ClaimCommand command) {
        String canonical = tenantId + "|" + actorId + "|" + queueId + "|"
                + command.recordType().name() + "|" + command.recordId();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private void recordMutation(UUID tenantId,
                                UUID actorId,
                                String action,
                                String entityType,
                                UUID entityId,
                                JsonNode before,
                                JsonNode after,
                                String eventType,
                                String summary) {
        Instant now = Instant.now();
        auditPort.record(tenantId, actorId, action, entityType, entityId,
                new AuditChange(before, after), now);
        timelineEventPort.record(
                tenantId,
                entityType,
                entityId,
                eventType,
                summary,
                entityType,
                entityId,
                actorId,
                now);
    }

    private JsonNode json(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private void requireTenantActor(UUID tenantId, UUID actorId) {
        requireTenant(tenantId);
        if (actorId == null) {
            throw new OwnershipDomainException("actorId required");
        }
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new OwnershipDomainException("tenantId required");
        }
    }

    private String normalizeCode(String code) {
        return requireText(code, "code", 64).toUpperCase(java.util.Locale.ROOT);
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new OwnershipDomainException(field + " required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new OwnershipDomainException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new OwnershipDomainException("Value exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public record CreateQueueCommand(
            String code,
            String displayName,
            QueueRecordType recordType,
            String description,
            int maxItemsPerUser,
            Integer slaMinutes,
            UUID escalationTargetQueueId,
            UUID defaultOwnerId
    ) {}

    public record UpdateQueueCommand(
            String displayName,
            boolean descriptionSet,
            String description,
            QueueStatus status,
            Integer maxItemsPerUser,
            boolean slaMinutesSet,
            Integer slaMinutes,
            boolean escalationTargetQueueIdSet,
            UUID escalationTargetQueueId,
            boolean defaultOwnerIdSet,
            UUID defaultOwnerId
    ) {}

    public record ClaimCommand(
            AssignmentRecordType recordType,
            UUID recordId,
            UUID idempotencyKey,
            UUID correlationId
    ) {}

    public record ReleaseCommand(
            AssignmentRecordType recordType,
            UUID recordId,
            String reason,
            UUID correlationId
    ) {}

    public record ClaimResult(Assignment assignment, boolean replayed) {}
}
