package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Request to transfer ownership of one or more CRM records. */
public record TransferRequest(
        UUID id,
        UUID tenantId,
        AssignmentRecordType recordType,
        java.util.List<UUID> recordIds,
        UUID requesterUserId,
        UUID currentOwnerUserId,
        UUID proposedOwnerUserId,
        UUID proposedOwnerTeamId,
        TransferType transferType,
        Instant temporaryEndDate,
        String reason,
        TransferPolicy policy,
        TransferState state,
        Integer currentApprovalStep,
        UUID workflowRunId,
        Instant executedAt,
        UUID executedByUserId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public TransferRequest {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (recordType == null) throw new IllegalArgumentException("recordType required");
        if (recordIds == null || recordIds.isEmpty()) throw new IllegalArgumentException("recordIds required");
        if (requesterUserId == null) throw new IllegalArgumentException("requesterUserId required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        if (policy == null) policy = TransferPolicy.SINGLE_APPROVER;
        if (state == null) state = TransferState.DRAFT;
        if (transferType == null) transferType = TransferType.PERMANENT;
        if (transferType == TransferType.TEMPORARY && temporaryEndDate == null) {
            throw new IllegalArgumentException("temporaryEndDate required for TEMPORARY transfer");
        }
        if (transferType == TransferType.PERMANENT && temporaryEndDate != null) {
            throw new IllegalArgumentException("temporaryEndDate must be null for PERMANENT transfer");
        }
        if (policy != TransferPolicy.NO_APPROVAL_REQUIRED
                && proposedOwnerUserId != null
                && proposedOwnerUserId.equals(requesterUserId)) {
            throw new IllegalArgumentException("Requester cannot be the proposed owner (separation of duties)");
        }
        recordIds = java.util.List.copyOf(recordIds);
    }

    public boolean isTerminal() {
        return state == TransferState.COMPLETED || state == TransferState.REJECTED
                || state == TransferState.CANCELLED || state == TransferState.FAILED;
    }
}
