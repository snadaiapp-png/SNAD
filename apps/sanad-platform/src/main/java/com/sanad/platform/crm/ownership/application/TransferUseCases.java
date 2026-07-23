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

/** CRM-008B WP-08 transfer request and approval service. */
public class TransferUseCases {

    private final TransferRequestRepository transfers;
    private final AssignmentRepository assignments;
    private final OwnershipRecordPort records;
    private final OwnershipCommandUseCases ownershipCommands;
    private final OwnershipUserValidationPort users;
    private final SalesTeamRepository teams;
    private final WorkflowPort workflow;
    private final HrmPort hrm;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper mapper;

    public TransferUseCases(TransferRequestRepository transfers,
                            AssignmentRepository assignments,
                            OwnershipRecordPort records,
                            OwnershipCommandUseCases ownershipCommands,
                            OwnershipUserValidationPort users,
                            SalesTeamRepository teams,
                            WorkflowPort workflow,
                            HrmPort hrm,
                            AuditPort audit,
                            TimelineEventPort timeline,
                            ObjectMapper mapper) {
        this.transfers = transfers;
        this.assignments = assignments;
        this.records = records;
        this.ownershipCommands = ownershipCommands;
        this.users = users;
        this.teams = teams;
        this.workflow = workflow;
        this.hrm = hrm;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
    }

    @Transactional
    public TransferRequest createDraft(UUID tenantId,
                                       UUID requesterUserId,
                                       CreateTransferCommand command) {
        requireActiveUser(tenantId, requesterUserId, "requester");
        validateCreate(tenantId, requesterUserId, command);
        UUID currentOwner = currentCommonOwner(tenantId, command.recordType(), command.recordIds());
        TransferRequest created = transfers.save(new TransferRequest(
                null,
                tenantId,
                command.recordType(),
                command.recordIds(),
                requesterUserId,
                currentOwner,
                command.proposedOwnerType() == OwnerType.USER ? command.proposedOwnerId() : null,
                command.proposedOwnerType() == OwnerType.TEAM ? command.proposedOwnerId() : null,
                command.transferType(),
                command.temporaryEndDate(),
                reason(command.reason()),
                command.policy(),
                TransferState.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        mutation(tenantId, requesterUserId, "CREATE", created.id(), null, snapshot(created),
                "crm.transfer.created", "Transfer request created");
        return created;
    }

    public TransferRequest get(UUID tenantId, UUID transferId) {
        return requireTransfer(tenantId, transferId);
    }

    public List<TransferRequest> listByState(UUID tenantId, TransferState state) {
        if (tenantId == null || state == null) {
            throw new OwnershipDomainException("tenantId and transfer state required");
        }
        return transfers.findByState(tenantId, state);
    }

    @Transactional
    public TransferRequest submit(UUID tenantId,
                                  UUID actorId,
                                  UUID transferId,
                                  List<UUID> approverUserIds) {
        TransferRequest current = requireTransfer(tenantId, transferId);
        if (!current.requesterUserId().equals(actorId)) {
            throw new OwnershipDomainException("Only the requester may submit a transfer");
        }
        if (current.state() != TransferState.DRAFT) {
            throw new TransferStateConflictException(
                    tenantId, transferId, current.state(), TransferState.SUBMITTED);
        }
        transfers.updateState(tenantId, transferId, TransferState.SUBMITTED, null, null);

        if (current.policy() == TransferPolicy.NO_APPROVAL_REQUIRED) {
            transfers.updateState(tenantId, transferId, TransferState.APPROVED, null, null);
            return executeApproved(tenantId, transferId, actorId);
        }

        List<UUID> approvers = approverUserIds == null ? List.of() : List.copyOf(approverUserIds);
        if (current.policy() == TransferPolicy.SINGLE_APPROVER && approvers.size() != 1) {
            throw new OwnershipDomainException("Single-approver transfer requires exactly one approver");
        }
        if (current.policy() == TransferPolicy.MULTI_APPROVER && workflow.isStub()) {
            throw new OwnershipDomainException(
                    "Multi-step transfer approval is blocked while WorkflowPort is a stub");
        }
        if (approvers.isEmpty() || new HashSet<>(approvers).size() != approvers.size()) {
            throw new OwnershipDomainException("Distinct transfer approvers are required");
        }
        for (UUID approver : approvers) {
            requireActiveUser(tenantId, approver, "approver");
            if (approver.equals(current.requesterUserId())
                    || approver.equals(current.proposedOwnerUserId())) {
                throw new UnauthorizedTransferApproverException(tenantId, transferId, approver);
            }
        }

        UUID workflowRunId = workflow.startTransferApproval(tenantId, transferId, approvers);
        transfers.setWorkflowReference(tenantId, transferId, workflowRunId, 1);
        for (int index = 0; index < approvers.size(); index++) {
            transfers.addStep(tenantId, transferId, index + 1, approvers.get(index));
        }
        transfers.updateState(tenantId, transferId, TransferState.UNDER_REVIEW, null, null);
        TransferRequest submitted = requireTransfer(tenantId, transferId);
        mutation(tenantId, actorId, "SUBMIT", transferId, snapshot(current), snapshot(submitted),
                "crm.transfer.submitted", "Transfer submitted for approval");
        return submitted;
    }

    @Transactional
    public TransferRequest decide(UUID tenantId,
                                  UUID approverUserId,
                                  UUID transferId,
                                  TransferStepDecision decision,
                                  String comment) {
        requireActiveUser(tenantId, approverUserId, "approver");
        TransferRequest current = requireTransfer(tenantId, transferId);
        if (current.state() != TransferState.UNDER_REVIEW
                || current.currentApprovalStep() == null) {
            throw new TransferStateConflictException(
                    tenantId, transferId, current.state(), TransferState.APPROVED);
        }
        if (decision != TransferStepDecision.APPROVED
                && decision != TransferStepDecision.REJECTED) {
            throw new OwnershipDomainException("Only APPROVED or REJECTED decisions are accepted");
        }
        transfers.decideStep(
                tenantId, transferId, current.currentApprovalStep(),
                decision, approverUserId, bounded(comment, 1000));

        if (decision == TransferStepDecision.REJECTED) {
            transfers.updateState(tenantId, transferId, TransferState.REJECTED, null, null);
            TransferRequest rejected = requireTransfer(tenantId, transferId);
            mutation(tenantId, approverUserId, "REJECT", transferId,
                    snapshot(current), snapshot(rejected),
                    "crm.transfer.rejected", "Transfer rejected");
            return rejected;
        }

        if (current.policy() == TransferPolicy.MULTI_APPROVER) {
            throw new OwnershipDomainException(
                    "Multi-step execution remains blocked until the real Workflow Engine is installed");
        }
        transfers.updateState(tenantId, transferId, TransferState.APPROVED, null, null);
        return executeApproved(tenantId, transferId, approverUserId);
    }

    @Transactional
    public TransferRequest cancel(UUID tenantId,
                                  UUID actorId,
                                  UUID transferId,
                                  String reason) {
        TransferRequest current = requireTransfer(tenantId, transferId);
        if (!current.requesterUserId().equals(actorId)) {
            throw new OwnershipDomainException("Only the requester may cancel a transfer");
        }
        if (current.workflowRunId() != null) {
            workflow.cancelApproval(tenantId, current.workflowRunId(), bounded(reason, 500));
        }
        transfers.updateState(tenantId, transferId, TransferState.CANCELLED, null, null);
        TransferRequest cancelled = requireTransfer(tenantId, transferId);
        mutation(tenantId, actorId, "CANCEL", transferId, snapshot(current), snapshot(cancelled),
                "crm.transfer.cancelled", "Transfer cancelled");
        return cancelled;
    }

    public void requestAbsenceDrivenReassignment(UUID tenantId, UUID absentUserId) {
        if (hrm.isStub()) {
            throw new OwnershipDomainException(
                    "HRM absence-driven reassignment is disabled until real integration is approved");
        }
        if (!hrm.isAbsent(tenantId, absentUserId)) {
            throw new OwnershipDomainException("User is not absent according to HRM");
        }
        throw new OwnershipDomainException("Absence-driven transfer requires separate authorization");
    }

    private TransferRequest executeApproved(UUID tenantId,
                                            UUID transferId,
                                            UUID actorId) {
        TransferRequest approved = requireTransfer(tenantId, transferId);
        if (approved.state() != TransferState.APPROVED) {
            throw new TransferStateConflictException(
                    tenantId, transferId, approved.state(), TransferState.COMPLETED);
        }
        OwnerType ownerType = approved.proposedOwnerUserId() != null
                ? OwnerType.USER : OwnerType.TEAM;
        UUID ownerId = approved.proposedOwnerUserId() != null
                ? approved.proposedOwnerUserId() : approved.proposedOwnerTeamId();
        ownershipCommands.transfer(new OwnershipCommandUseCases.TransferAssignmentCommand(
                tenantId,
                approved.recordType(),
                approved.recordIds(),
                ownerType,
                ownerId,
                actorId,
                approved.id(),
                UUID.randomUUID(),
                approved.reason(),
                approved.transferType() == TransferType.TEMPORARY
                        ? approved.temporaryEndDate() : null));
        transfers.updateState(tenantId, transferId, TransferState.COMPLETED, actorId, null);
        TransferRequest completed = requireTransfer(tenantId, transferId);
        mutation(tenantId, actorId, "EXECUTE", transferId,
                snapshot(approved), snapshot(completed),
                "crm.transfer.completed", "Transfer completed");
        return completed;
    }

    private void validateCreate(UUID tenantId,
                                UUID requesterUserId,
                                CreateTransferCommand command) {
        if (command == null || command.recordType() == null
                || command.transferType() == null || command.policy() == null
                || command.proposedOwnerType() == null || command.proposedOwnerId() == null) {
            throw new OwnershipDomainException("Complete transfer command required");
        }
        if (command.recordType() != AssignmentRecordType.ACCOUNT
                && command.recordType() != AssignmentRecordType.LEAD
                && command.recordType() != AssignmentRecordType.OPPORTUNITY) {
            throw new OwnershipDomainException("Formal transfers support ACCOUNT, LEAD and OPPORTUNITY only");
        }
        validateRecordIds(command.recordIds());
        if (command.proposedOwnerType() == OwnerType.QUEUE) {
            throw new OwnershipDomainException("Formal transfer target must be USER or TEAM");
        }
        validateTarget(tenantId, command.proposedOwnerType(), command.proposedOwnerId());
        if (command.policy() != TransferPolicy.NO_APPROVAL_REQUIRED
                && command.proposedOwnerType() == OwnerType.USER
                && requesterUserId.equals(command.proposedOwnerId())) {
            throw new OwnershipDomainException("Requester cannot be proposed owner under approval policy");
        }
        if (command.transferType() == TransferType.TEMPORARY) {
            if (command.temporaryEndDate() == null
                    || !command.temporaryEndDate().isAfter(Instant.now())) {
                throw new OwnershipDomainException("Temporary transfer requires a future end date");
            }
        } else if (command.temporaryEndDate() != null) {
            throw new OwnershipDomainException("Permanent transfer cannot have temporary end date");
        }
        if (command.policy() == TransferPolicy.MULTI_APPROVER && workflow.isStub()) {
            throw new OwnershipDomainException(
                    "Multi-step transfer approval is blocked while WorkflowPort is a stub");
        }
    }

    private UUID currentCommonOwner(UUID tenantId,
                                    AssignmentRecordType recordType,
                                    List<UUID> recordIds) {
        UUID owner = null;
        for (UUID recordId : recordIds) {
            if (!records.exists(tenantId, recordType, recordId)) {
                throw new OwnershipDomainException(
                        "Transfer record not found in tenant: " + recordType + "/" + recordId);
            }
            Assignment assignment = assignments.findActive(tenantId, recordType, recordId)
                    .orElseThrow(() -> new AssignmentNotFoundException(
                            tenantId, recordType, recordId));
            if (assignment.ownerType() != OwnerType.USER || assignment.ownerUserId() == null) {
                throw new OwnershipDomainException(
                        "Formal transfer records must currently have USER ownership");
            }
            if (owner == null) owner = assignment.ownerUserId();
            else if (!owner.equals(assignment.ownerUserId())) {
                throw new OwnershipDomainException(
                        "Bulk transfer records must share the same current user owner");
            }
        }
        return owner;
    }

    private void validateTarget(UUID tenantId, OwnerType type, UUID id) {
        if (type == OwnerType.USER) {
            requireActiveUser(tenantId, id, "proposed owner");
            return;
        }
        SalesTeam team = teams.findById(tenantId, id)
                .filter(SalesTeam::isActive)
                .orElseThrow(() -> new OwnershipDomainException(
                        "Proposed team must be ACTIVE in the same tenant"));
        if (team.isArchived()) throw new OwnershipDomainException("Proposed team is archived");
    }

    private void validateRecordIds(List<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty() || recordIds.size() > 100) {
            throw new OwnershipDomainException("Transfer requires between 1 and 100 record IDs");
        }
        if (recordIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(recordIds).size() != recordIds.size()) {
            throw new OwnershipDomainException("Transfer record IDs must be distinct and non-null");
        }
    }

    private TransferRequest requireTransfer(UUID tenantId, UUID transferId) {
        if (tenantId == null || transferId == null) {
            throw new OwnershipDomainException("tenantId and transferId required");
        }
        return transfers.findById(tenantId, transferId)
                .orElseThrow(() -> new OwnershipDomainException("Transfer not found: " + transferId));
    }

    private void requireActiveUser(UUID tenantId, UUID userId, String role) {
        if (!users.isActiveUser(tenantId, userId)) {
            throw new OwnershipDomainException(role + " must be ACTIVE in the same tenant");
        }
    }

    private String reason(String value) {
        if (value == null || value.isBlank()) throw new OwnershipDomainException("Transfer reason required");
        return bounded(value, 500);
    }

    private String bounded(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new OwnershipDomainException("Value exceeds " + max);
        return normalized;
    }

    private JsonNode snapshot(TransferRequest transfer) {
        if (transfer == null) return null;
        ObjectNode node = mapper.createObjectNode();
        put(node, "id", transfer.id());
        node.put("recordType", transfer.recordType().name());
        node.put("recordCount", transfer.recordIds().size());
        put(node, "requesterUserId", transfer.requesterUserId());
        put(node, "proposedOwnerUserId", transfer.proposedOwnerUserId());
        put(node, "proposedOwnerTeamId", transfer.proposedOwnerTeamId());
        node.put("policy", transfer.policy().name());
        node.put("state", transfer.state().name());
        put(node, "workflowRunId", transfer.workflowRunId());
        return node;
    }

    private void put(ObjectNode node, String field, UUID value) {
        if (value == null) node.putNull(field); else node.put(field, value.toString());
    }

    private void mutation(UUID tenantId, UUID actorId, String action, UUID id,
                          JsonNode before, JsonNode after, String event, String summary) {
        Instant now = Instant.now();
        audit.record(tenantId, actorId, action, "TRANSFER_REQUEST", id,
                new AuditChange(before, after), now);
        timeline.record(tenantId, "TRANSFER_REQUEST", id, event, summary,
                "TRANSFER_REQUEST", id, actorId, now);
    }

    public record CreateTransferCommand(
            AssignmentRecordType recordType,
            List<UUID> recordIds,
            OwnerType proposedOwnerType,
            UUID proposedOwnerId,
            TransferType transferType,
            Instant temporaryEndDate,
            String reason,
            TransferPolicy policy
    ) {
        public CreateTransferCommand {
            recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
        }
    }
}
