package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Application service for CRM-008B WP-04 queues, memberships and claims. */
public class QueueUseCases {

    private final QueueRepository queues;
    private final QueueMembershipRepository memberships;
    private final AssignmentRepository assignments;
    private final OwnershipReadPort reads;
    private final OwnershipWritePort writes;
    private final QueueClaimIdempotencyPort idempotency;
    private final OwnershipUserValidationPort users;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public QueueUseCases(QueueRepository queues,
                         QueueMembershipRepository memberships,
                         AssignmentRepository assignments,
                         OwnershipReadPort reads,
                         OwnershipWritePort writes,
                         QueueClaimIdempotencyPort idempotency,
                         OwnershipUserValidationPort users,
                         AuditPort audit,
                         TimelineEventPort timeline,
                         ObjectMapper mapper) {
        this.queues = queues;
        this.memberships = memberships;
        this.assignments = assignments;
        this.reads = reads;
        this.writes = writes;
        this.idempotency = idempotency;
        this.users = users;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public Queue createQueue(UUID tenantId, UUID actorId, CreateQueueCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.recordType() == null) {
            throw new OwnershipDomainException("Queue create command and recordType are required");
        }
        validateDefaultOwner(tenantId, command.defaultOwnerId());
        Queue created = queues.save(new Queue(
                null, tenantId, code(command.code()), text(command.displayName(), "displayName", 200),
                command.recordType(), optionalText(command.description(), 1000), QueueStatus.ACTIVE,
                command.maxItemsPerUser(), command.slaMinutes(), command.escalationTargetQueueId(),
                command.defaultOwnerId(), null, null, actorId, actorId));
        mutation(tenantId, actorId, "CREATE", "QUEUE", created.id(), null, snapshot(created),
                "crm.queue.created", "Queue created");
        return created;
    }

    public Queue getQueue(UUID tenantId, UUID queueId) {
        return requireQueue(tenantId, queueId);
    }

    public List<Queue> listQueues(UUID tenantId, QueueStatus status) {
        requireTenant(tenantId);
        return status == null ? queues.findAllByTenant(tenantId) : queues.findByTenant(tenantId, status);
    }

    @Transactional
    public Queue updateQueue(UUID tenantId, UUID actorId, UUID queueId, UpdateQueueCommand command) {
        requireTenantActor(tenantId, actorId);
        Queue current = requireQueue(tenantId, queueId);
        if (current.status() == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Archived queue is immutable: " + queueId);
        }
        if (command == null) throw new OwnershipDomainException("Queue update command required");
        QueueStatus status = command.status() != null ? command.status() : current.status();
        if (status == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Use archiveQueue for the ARCHIVED transition");
        }
        UUID defaultOwner = command.defaultOwnerIdSet() ? command.defaultOwnerId() : current.defaultOwnerId();
        validateDefaultOwner(tenantId, defaultOwner);
        Queue updated = queues.save(new Queue(
                current.id(), current.tenantId(), current.code(),
                command.displayName() != null
                        ? text(command.displayName(), "displayName", 200) : current.displayName(),
                current.recordType(),
                command.descriptionSet() ? optionalText(command.description(), 1000) : current.description(),
                status,
                command.maxItemsPerUser() != null ? command.maxItemsPerUser() : current.maxItemsPerUser(),
                command.slaMinutesSet() ? command.slaMinutes() : current.slaMinutes(),
                command.escalationTargetQueueIdSet()
                        ? command.escalationTargetQueueId() : current.escalationTargetQueueId(),
                defaultOwner, current.createdAt(), current.updatedAt(), current.createdBy(), actorId));
        mutation(tenantId, actorId, "UPDATE", "QUEUE", queueId, snapshot(current), snapshot(updated),
                "crm.queue.updated", "Queue updated");
        return updated;
    }

    @Transactional
    public Queue archiveQueue(UUID tenantId, UUID actorId, UUID queueId) {
        requireTenantActor(tenantId, actorId);
        Queue current = requireQueue(tenantId, queueId);
        if (current.status() == QueueStatus.ARCHIVED) return current;
        long waiting = assignments.countActiveByOwner(tenantId, OwnerType.QUEUE, queueId);
        long members = memberships.countActiveByQueue(tenantId, queueId);
        if (waiting > 0 || members > 0) {
            throw new OwnershipDomainException(
                    "Queue archive requires zero waiting items and zero active memberships: queue="
                            + queueId + " waiting=" + waiting + " memberships=" + members);
        }
        queues.updateStatus(tenantId, queueId, QueueStatus.ARCHIVED, actorId);
        Queue archived = requireQueue(tenantId, queueId);
        mutation(tenantId, actorId, "ARCHIVE", "QUEUE", queueId, snapshot(current), snapshot(archived),
                "crm.queue.archived", "Queue archived");
        return archived;
    }

    public List<QueueMembership> listMemberships(UUID tenantId, UUID queueId) {
        requireQueue(tenantId, queueId);
        return memberships.findActiveByQueue(tenantId, queueId);
    }

    @Transactional
    public QueueMembership addMembership(UUID tenantId, UUID actorId, UUID queueId, UUID userId) {
        requireTenantActor(tenantId, actorId);
        Queue queue = requireQueue(tenantId, queueId);
        if (queue.status() == QueueStatus.ARCHIVED) {
            throw new OwnershipDomainException("Cannot add membership to archived queue: " + queueId);
        }
        if (!users.isActiveUser(tenantId, userId)) {
            throw new OwnershipDomainException("Queue member must be ACTIVE in the same tenant: " + userId);
        }
        QueueMembership created = memberships.save(new QueueMembership(
                null, tenantId, queueId, userId, MembershipStatus.ACTIVE,
                Instant.now(), null, null, null, null, actorId, actorId));
        mutation(tenantId, actorId, "CREATE", "QUEUE_MEMBERSHIP", created.id(), null, snapshot(created),
                "crm.queue_membership.created", "Queue membership created");
        return created;
    }

    @Transactional
    public QueueMembership removeMembership(UUID tenantId,
                                            UUID actorId,
                                            UUID queueId,
                                            UUID membershipId,
                                            String reason) {
        requireTenantActor(tenantId, actorId);
        requireQueue(tenantId, queueId);
        QueueMembership current = memberships.findById(tenantId, membershipId)
                .filter(value -> queueId.equals(value.queueId()) && value.isActive())
                .orElseThrow(() -> new OwnershipDomainException(
                        "Active queue membership not found on queue path: " + membershipId));
        long activeClaims = assignments.countActiveQueueClaims(tenantId, queueId, current.userId());
        if (activeClaims > 0) {
            throw new OwnershipDomainException(
                    "Release active claimed items before removing queue membership: " + activeClaims);
        }
        memberships.remove(tenantId, membershipId, text(reason, "removedReason", 100), actorId);
        QueueMembership removed = memberships.findById(tenantId, membershipId)
                .orElseThrow(() -> new OwnershipDomainException("Removed membership disappeared"));
        mutation(tenantId, actorId, "REMOVE", "QUEUE_MEMBERSHIP", membershipId,
                snapshot(current), snapshot(removed),
                "crm.queue_membership.removed", "Queue membership removed");
        return removed;
    }

    public QueueItemPage listItems(UUID tenantId, UUID queueId, UUID cursor, int pageSize) {
        requireQueue(tenantId, queueId);
        return reads.findQueueItems(tenantId, queueId, cursor, pageSize);
    }

    @Transactional
    public ClaimResult claim(UUID tenantId, UUID actorId, UUID queueId, ClaimCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.idempotencyKey() == null
                || command.recordType() == null || command.recordId() == null) {
            throw new OwnershipDomainException(
                    "recordType, recordId and idempotencyKey are required for queue claim");
        }
        requireQueue(tenantId, queueId);
        String fingerprint = fingerprint(tenantId, actorId, queueId, command);
        Optional<QueueClaimIdempotencyPort.ClaimRecord> existing = idempotency.reserve(
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
            Assignment assignment = assignments.findById(tenantId, replay.assignmentId())
                    .orElseThrow(() -> new OwnershipDomainException(
                            "Completed idempotent claim references a missing assignment"));
            return new ClaimResult(assignment, true);
        }
        Assignment assignment = writes.claimQueueItem(new OwnershipWritePort.QueueClaimCommand(
                tenantId, queueId, command.recordType(), command.recordId(), actorId,
                command.correlationId() != null ? command.correlationId() : UUID.randomUUID()));
        idempotency.complete(tenantId, actorId, command.idempotencyKey(), fingerprint, assignment.id());
        mutation(tenantId, actorId, "CLAIM", "QUEUE_ITEM", command.recordId(), null, snapshot(assignment),
                "crm.queue_item.claimed", "Queue item claimed");
        return new ClaimResult(assignment, false);
    }

    @Transactional
    public Assignment release(UUID tenantId, UUID actorId, UUID queueId, ReleaseCommand command) {
        requireTenantActor(tenantId, actorId);
        if (command == null || command.recordType() == null || command.recordId() == null) {
            throw new OwnershipDomainException("recordType and recordId are required for release");
        }
        requireQueue(tenantId, queueId);
        writes.releaseQueueItem(new OwnershipWritePort.QueueReleaseCommand(
                tenantId, queueId, command.recordType(), command.recordId(), actorId,
                text(command.reason(), "reason", 500),
                command.correlationId() != null ? command.correlationId() : UUID.randomUUID()));
        Assignment assignment = assignments.findActive(tenantId, command.recordType(), command.recordId())
                .orElseThrow(() -> new OwnershipDomainException("Released queue assignment not found"));
        mutation(tenantId, actorId, "RELEASE", "QUEUE_ITEM", command.recordId(), null, snapshot(assignment),
                "crm.queue_item.released", "Queue item released");
        return assignment;
    }

    private Queue requireQueue(UUID tenantId, UUID queueId) {
        requireTenant(tenantId);
        if (queueId == null) throw new QueueNotFoundException(tenantId, null);
        return queues.findById(tenantId, queueId)
                .orElseThrow(() -> new QueueNotFoundException(tenantId, queueId));
    }

    private void validateDefaultOwner(UUID tenantId, UUID userId) {
        if (userId != null && !users.isActiveUser(tenantId, userId)) {
            throw new OwnershipDomainException(
                    "Default queue owner must be ACTIVE in the same tenant: " + userId);
        }
    }

    private String fingerprint(UUID tenantId, UUID actorId, UUID queueId, ClaimCommand command) {
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

    /** Explicit audit snapshots avoid mapper/module-dependent transaction failures. */
    private JsonNode snapshot(Object value) {
        if (value == null) return null;
        ObjectNode node = mapper.createObjectNode();
        if (value instanceof Queue queue) {
            put(node, "id", queue.id());
            put(node, "tenantId", queue.tenantId());
            node.put("code", queue.code());
            node.put("displayName", queue.displayName());
            node.put("recordType", queue.recordType().name());
            node.put("status", queue.status().name());
            node.put("maxItemsPerUser", queue.maxItemsPerUser());
            if (queue.slaMinutes() != null) node.put("slaMinutes", queue.slaMinutes());
            put(node, "escalationTargetQueueId", queue.escalationTargetQueueId());
            put(node, "defaultOwnerId", queue.defaultOwnerId());
            return node;
        }
        if (value instanceof QueueMembership membership) {
            put(node, "id", membership.id());
            put(node, "tenantId", membership.tenantId());
            put(node, "queueId", membership.queueId());
            put(node, "userId", membership.userId());
            node.put("status", membership.status().name());
            if (membership.removedReason() != null) node.put("removedReason", membership.removedReason());
            return node;
        }
        if (value instanceof Assignment assignment) {
            put(node, "id", assignment.id());
            put(node, "tenantId", assignment.tenantId());
            node.put("recordType", assignment.recordType().name());
            put(node, "recordId", assignment.recordId());
            node.put("ownerType", assignment.ownerType().name());
            put(node, "ownerUserId", assignment.ownerUserId());
            put(node, "ownerTeamId", assignment.ownerTeamId());
            put(node, "ownerQueueId", assignment.ownerQueueId());
            node.put("status", assignment.status().name());
            put(node, "correlationId", assignment.correlationId());
            return node;
        }
        node.put("value", String.valueOf(value));
        return node;
    }

    private void put(ObjectNode node, String field, UUID value) {
        if (value == null) node.putNull(field); else node.put(field, value.toString());
    }

    private void mutation(UUID tenantId,
                          UUID actorId,
                          String action,
                          String entityType,
                          UUID entityId,
                          JsonNode before,
                          JsonNode after,
                          String eventType,
                          String summary) {
        Instant now = Instant.now();
        audit.record(tenantId, actorId, action, entityType, entityId,
                new AuditChange(before, after), now);
        timeline.record(tenantId, entityType, entityId, eventType, summary,
                entityType, entityId, actorId, now);
    }

    private void requireTenantActor(UUID tenantId, UUID actorId) {
        requireTenant(tenantId);
        if (actorId == null) throw new OwnershipDomainException("actorId required");
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) throw new OwnershipDomainException("tenantId required");
    }

    private String code(String value) {
        return text(value, "code", 64).toUpperCase(Locale.ROOT);
    }

    private String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new OwnershipDomainException(field + " required");
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new OwnershipDomainException(field + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private String optionalText(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new OwnershipDomainException("Value exceeds " + max + " characters");
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
