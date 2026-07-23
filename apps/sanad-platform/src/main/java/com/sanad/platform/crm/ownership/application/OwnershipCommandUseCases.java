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

/** Atomic manual, bulk, rule-driven and transfer ownership commands. */
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
                command.tenantId(), command.recordType(), command.recordId(),
                command.ownerType(), command.ownerId(), command.actorId(), command.reason(),
                command.requestId(), command.correlationId(), command.expectedAssignmentId(),
                command.assignedByRuleId(), null, ChangeType.REASSIGN,
                command.assignedByRuleId() != null ? TriggerSource.RULE : TriggerSource.MANUAL,
                current);
        project(command.tenantId(), command.recordType(), command.recordId(), created);
        mutation(command.tenantId(), command.actorId(), command.recordType(), command.recordId(),
                "REASSIGN", "crm.ownership.reassigned", current, created);
        return created;
    }

    @Transactional
    public List<Assignment> bulkReassign(BulkReassignCommand command) {
        validateRecordIds(command == null ? null : command.recordIds(), "Bulk reassignment");
        validateOwner(command.tenantId(), command.recordType(), command.ownerType(), command.ownerId());
        for (UUID recordId : command.recordIds()) {
            validateRecord(command.tenantId(), command.recordType(), recordId);
        }
        return command.recordIds().stream().map(recordId -> reassign(new ReassignCommand(
                command.tenantId(), command.recordType(), recordId,
                command.ownerType(), command.ownerId(), command.actorId(), command.reason(),
                command.requestId(), UUID.randomUUID(), null, null))).toList();
    }

    /** Atomic multi-record transfer with transfer-specific ledger semantics. */
    @Transactional
    public List<Assignment> transfer(TransferAssignmentCommand command) {
        validateRecordIds(command == null ? null : command.recordIds(), "Transfer");
        if (command.transferRequestId() == null || command.correlationId() == null
                || command.actorId() == null || command.recordType() == null
                || command.tenantId() == null) {
            throw new OwnershipDomainException("Complete transfer ownership command required");
        }
        if (command.effectiveTo() != null && !command.effectiveTo().isAfter(Instant.now())) {
            throw new OwnershipDomainException("Temporary transfer end must be in the future");
        }
        validateOwner(command.tenantId(), command.recordType(), command.ownerType(), command.ownerId());
        for (UUID recordId : command.recordIds()) {
            validateRecord(command.tenantId(), command.recordType(), recordId);
        }
        return command.recordIds().stream().map(recordId -> {
            Assignment current = assignments.findActive(
                            command.tenantId(), command.recordType(), recordId)
                    .orElseThrow(() -> new AssignmentNotFoundException(
                            command.tenantId(), command.recordType(), recordId));
            Assignment created = transition(
                    command.tenantId(), command.recordType(), recordId,
                    command.ownerType(), command.ownerId(), command.actorId(), command.reason(),
                    command.transferRequestId(), command.correlationId(), current.id(),
                    null, command.effectiveTo(), ChangeType.TRANSFER,
                    TriggerSource.TRANSFER_REQUEST, current);
            project(command.tenantId(), command.recordType(), recordId, created);
            mutation(command.tenantId(), command.actorId(), command.recordType(), recordId,
                    "TRANSFER", "crm.ownership.transferred", current, created);
            return created;
        }).toList();
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

    private Assignment transition(UUID tenantId,
                                  AssignmentRecordType recordType,
                                  UUID recordId,
                                  OwnerType ownerType,
                                  UUID ownerId,
                                  UUID actorId,
                                  String reason,
                                  UUID requestId,
                                  UUID correlationId,
                                  UUID expectedAssignmentId,
                                  UUID ruleId,
                                  Instant effectiveTo,
                                  ChangeType changeType,
                                  TriggerSource source,
                                  Assignment current) {
        Instant now = Instant.now();
        Assignment next = new Assignment(
                null, tenantId, 0,
                recordType.name(), recordId,
                ownerType == OwnerType.USER ? ownerId : actorId, "OWNER",
                AssignmentStatus.ACTIVE, now, effectiveTo, normalizeReason(reason),
                ownerType,
                ownerType == OwnerType.USER ? ownerId : null,
                ownerType == OwnerType.TEAM ? ownerId : null,
                ownerType == OwnerType.QUEUE ? ownerId : null,
                recordType, recordId, ruleId, actorId,
                correlationId, null, now, effectiveTo,
                now, now, actorId, actorId);
        OwnerType expectedType = current != null ? current.ownerType() : null;
        UUID expectedOwner = current != null ? ownerId(current) : null;
        return assignments.supersedeAndInsertExpected(
                tenantId, recordType, recordId, next,
                actorId, normalizeReason(reason), changeType,
                source, requestId, expectedAssignmentId, expectedType, expectedOwner);
    }

    private void project(UUID tenantId,
                         AssignmentRecordType recordType,
                         UUID recordId,
                         Assignment assignment) {
        records.updateOwner(tenantId, recordType, recordId,
                assignment.ownerType(), ownerId(assignment));
    }

    private void validateRecord(UUID tenantId, AssignmentRecordType type, UUID id) {
        if (!records.exists(tenantId, type, id)) {
            throw new OwnershipDomainException("CRM record not found in tenant: " + type + "/" + id);
        }
    }

    private void validateRecordIds(List<UUID> recordIds, String operation) {
        if (recordIds == null || recordIds.isEmpty()) {
            throw new OwnershipDomainException(operation + " requires record IDs");
        }
        if (recordIds.size() > 100) {
            throw new OwnershipDomainException(operation + " limit is 100 records");
        }
        if (recordIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new OwnershipDomainException(operation + " contains null record ID");
        }
        if (new HashSet<>(recordIds).size() != recordIds.size()) {
            throw new OwnershipDomainException(operation + " contains duplicate record IDs");
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

    private void validateExpected(ReassignCommand command, Assignment current) {
        if (command.expectedAssignmentId() == null) return;
        if (current == null || !command.expectedAssignmentId().equals(current.id())) {
            throw new ConcurrentClaimConflictException(
                    command.tenantId(), command.recordType(), command.recordId());
        }
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

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) return "OWNERSHIP_CHANGE";
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new OwnershipDomainException("reason exceeds 1000 characters");
        }
        return normalized;
    }

    private void mutation(UUID tenantId,
                          UUID actorId,
                          AssignmentRecordType recordType,
                          UUID recordId,
                          String action,
                          String event,
                          Assignment before,
                          Assignment after) {
        Instant now = Instant.now();
        audit.record(tenantId, actorId, action, "CRM_RECORD",
                recordId, new AuditChange(snapshot(before), snapshot(after)), now);
        timeline.record(tenantId, recordType.name(), recordId,
                event, "CRM record ownership changed",
                "ASSIGNMENT", after.id(), actorId, now);
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
        if (assignment.effectiveTo() != null) {
            node.put("effectiveTo", assignment.effectiveTo().toString());
        }
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

    public record TransferAssignmentCommand(
            UUID tenantId,
            AssignmentRecordType recordType,
            List<UUID> recordIds,
            OwnerType ownerType,
            UUID ownerId,
            UUID actorId,
            UUID transferRequestId,
            UUID correlationId,
            String reason,
            Instant effectiveTo
    ) {
        public TransferAssignmentCommand {
            recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
        }
    }
}
