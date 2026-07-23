package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** CRM-008B WP-07 atomic manual, bulk and rule-driven ownership commands. */
public class OwnershipCommandUseCases {

    private final AssignmentRepository assignments;
    private final OwnershipRecordPort records;
    private final OwnershipUserValidationPort users;
    private final SalesTeamRepository teams;
    private final QueueRepository queues;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public OwnershipCommandUseCases(AssignmentRepository assignments,
                                    OwnershipRecordPort records,
                                    OwnershipUserValidationPort users,
                                    SalesTeamRepository teams,
                                    QueueRepository queues,
                                    AuditPort audit,
                                    TimelineEventPort timeline,
                                    ObjectMapper mapper) {
        this.assignments = assignments;
        this.records = records;
        this.users = users;
        this.teams = teams;
        this.queues = queues;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public Assignment reassign(ReassignCommand command) {
        requireCommand(command);
        validateRecord(command.tenantId(), command.recordType(), command.recordId());
        validateOwner(command.tenantId(), command.recordType(), command.ownerType(), command.ownerId());
        Assignment current = assignments.findActive(
                command.tenantId(), command.recordType(), command.recordId()).orElse(null);
        validateExpected(command, current);
        Assignment created = transition(
                command, current, command.assignedByRuleId(),
                command.assignedByRuleId() != null ? TriggerSource.RULE : TriggerSource.MANUAL);
        project(command, created);
        mutation(command, current, created);
        return created;
    }

    @Transactional
    public List<Assignment> bulkReassign(BulkReassignCommand command) {
        if (command == null || command.recordIds() == null || command.recordIds().isEmpty()) {
            throw new OwnershipDomainException("Bulk reassignment requires record IDs");
        }
        if (command.recordIds().size() > 100) {
            throw new OwnershipDomainException("Bulk reassignment limit is 100 records");
        }
        if (new HashSet<>(command.recordIds()).size() != command.recordIds().size()) {
            throw new OwnershipDomainException("Bulk reassignment contains duplicate record IDs");
        }
        validateOwner(command.tenantId(), command.recordType(), command.ownerType(), command.ownerId());
        for (UUID recordId : command.recordIds()) {
            validateRecord(command.tenantId(), command.recordType(), recordId);
        }
        return command.recordIds().stream().map(recordId -> reassign(new ReassignCommand(
                command.tenantId(), command.recordType(), recordId,
                command.ownerType(), command.ownerId(), command.actorId(), command.reason(),
                command.requestId(), UUID.randomUUID(), null, null))).toList();
    }

    @Transactional
    public Assignment assignByDecision(UUID tenantId,
                                       UUID actorId,
                                       AssignmentRecordType recordType,
                                       UUID recordId,
                                       AssignmentDecision decision,
                                       UUID requestId,
                                       UUID correlationId,
                                       String reason) {
        if (decision == null || !decision.matched()
                || decision.ownerType() == null || decision.ownerId() == null) {
            throw new OwnershipDomainException("Matched assignment decision required");
        }
        return reassign(new ReassignCommand(
                tenantId, recordType, recordId, decision.ownerType(), decision.ownerId(),
                actorId, reason, requestId, correlationId, null, decision.ruleId()));
    }

    private Assignment transition(ReassignCommand command,
                                  Assignment current,
                                  UUID ruleId,
                                  TriggerSource source) {
        Instant now = Instant.now();
        Assignment next = new Assignment(
                null, command.tenantId(), 0,
                command.recordType().name(), command.recordId(), legacyUser(command), "OWNER",
                AssignmentStatus.ACTIVE, now, null, reason(command.reason()),
                command.ownerType(),
                command.ownerType() == OwnerType.USER ? command.ownerId() : null,
                command.ownerType() == OwnerType.TEAM ? command.ownerId() : null,
                command.ownerType() == OwnerType.QUEUE ? command.ownerId() : null,
                command.recordType(), command.recordId(), ruleId, command.actorId(),
                command.correlationId(), null, now, null,
                now, now, command.actorId(), command.actorId());
        OwnerType expectedType = current != null ? current.ownerType() : null;
        UUID expectedOwner = current != null ? ownerId(current) : null;
        return assignments.supersedeAndInsertExpected(
                command.tenantId(), command.recordType(), command.recordId(), next,
                command.actorId(), reason(command.reason()), ChangeType.REASSIGN,
                source, command.requestId(), command.expectedAssignmentId(),
                expectedType, expectedOwner);
    }

    private void project(ReassignCommand command, Assignment assignment) {
        records.updateOwner(
                command.tenantId(), command.recordType(), command.recordId(),
                assignment.ownerType(), ownerId(assignment));
    }

    private void validateRecord(UUID tenantId, AssignmentRecordType type, UUID id) {
        if (!records.exists(tenantId, type, id)) {
            throw new OwnershipDomainException("CRM record not found in tenant: " + type + "/" + id);
        }
    }

    private void validateOwner(UUID tenantId,
                               AssignmentRecordType recordType,
                               OwnerType ownerType,
                               UUID ownerId) {
        if (ownerType == null || ownerId == null) {
            throw new OwnershipDomainException("ownerType and ownerId required");
        }
        switch (ownerType) {
            case USER -> {
                if (!users.isActiveUser(tenantId, ownerId)) {
                    throw new OwnershipDomainException("Owner user must be ACTIVE in same tenant");
                }
            }
            case TEAM -> {
                SalesTeam team = teams.findById(tenantId, ownerId)
                        .filter(SalesTeam::isActive)
                        .orElseThrow(() -> new OwnershipDomainException(
                                "Owner team must be ACTIVE in same tenant"));
                if (team.isArchived()) {
                    throw new OwnershipDomainException("Owner team is archived");
                }
            }
            case QUEUE -> {
                Queue queue = queues.findById(tenantId, ownerId)
                        .filter(Queue::acceptsNewItems)
                        .orElseThrow(() -> new OwnershipDomainException(
                                "Owner queue must be ACTIVE in same tenant"));
                if (!queue.recordType().name().equals(recordType.name())) {
                    throw new OwnershipDomainException("Owner queue record type mismatch");
                }
            }
        }
    }

    /** Fast rejection only; the repository repeats this check under the row lock. */
    private void validateExpected(ReassignCommand command, Assignment current) {
        if (command.expectedAssignmentId() == null) return;
        if (current == null || !command.expectedAssignmentId().equals(current.id())) {
            throw new ConcurrentClaimConflictException(
                    command.tenantId(), command.recordType(), command.recordId());
        }
    }

    private UUID legacyUser(ReassignCommand command) {
        return command.ownerType() == OwnerType.USER ? command.ownerId() : command.actorId();
    }

    private UUID ownerId(Assignment assignment) {
        return switch (assignment.ownerType()) {
            case USER -> assignment.ownerUserId();
            case TEAM -> assignment.ownerTeamId();
            case QUEUE -> assignment.ownerQueueId();
        };
    }

    private void requireCommand(ReassignCommand command) {
        if (command == null || command.tenantId() == null || command.recordType() == null
                || command.recordId() == null || command.actorId() == null
                || command.requestId() == null || command.correlationId() == null) {
            throw new OwnershipDomainException(
                    "Complete reassignment command with requestId and correlationId required");
        }
    }

    private String reason(String value) {
        if (value == null || value.isBlank()) return "MANUAL_REASSIGNMENT";
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new OwnershipDomainException("reason exceeds 1000 characters");
        }
        return normalized;
    }

    private void mutation(ReassignCommand command, Assignment before, Assignment after) {
        Instant now = Instant.now();
        audit.record(command.tenantId(), command.actorId(), "REASSIGN", "CRM_RECORD",
                command.recordId(), new AuditChange(snapshot(before), snapshot(after)), now);
        timeline.record(command.tenantId(), command.recordType().name(), command.recordId(),
                "crm.ownership.reassigned", "CRM record ownership reassigned",
                "ASSIGNMENT", after.id(), command.actorId(), now);
    }

    private JsonNode snapshot(Assignment assignment) {
        if (assignment == null) return null;
        ObjectNode node = mapper.createObjectNode();
        put(node, "id", assignment.id());
        node.put("recordType", assignment.recordType().name());
        put(node, "recordId", assignment.recordId());
        node.put("ownerType", assignment.ownerType().name());
        put(node, "ownerId", ownerId(assignment));
        put(node, "correlationId", assignment.correlationId());
        node.put("status", assignment.status().name());
        return node;
    }

    private void put(ObjectNode node, String field, UUID value) {
        if (value == null) node.putNull(field); else node.put(field, value.toString());
    }

    public record ReassignCommand(
            UUID tenantId,
            AssignmentRecordType recordType,
            UUID recordId,
            OwnerType ownerType,
            UUID ownerId,
            UUID actorId,
            String reason,
            UUID requestId,
            UUID correlationId,
            UUID expectedAssignmentId,
            UUID assignedByRuleId
    ) {}

    public record BulkReassignCommand(
            UUID tenantId,
            AssignmentRecordType recordType,
            List<UUID> recordIds,
            OwnerType ownerType,
            UUID ownerId,
            UUID actorId,
            String reason,
            UUID requestId
    ) {
        public BulkReassignCommand {
            recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
        }
    }
}
