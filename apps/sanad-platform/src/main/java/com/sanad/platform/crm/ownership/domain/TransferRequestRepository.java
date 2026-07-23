package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for transfer requests and their approval steps (tenant-scoped). */
public interface TransferRequestRepository {

    TransferRequest save(TransferRequest request);

    Optional<TransferRequest> findById(UUID tenantId, UUID transferId);

    List<TransferRequest> findByRequester(UUID tenantId, UUID requesterUserId);

    List<TransferRequest> findByState(UUID tenantId, TransferState state);

    List<TransferRequest> findByProposedOwner(UUID tenantId, UUID proposedOwnerUserId);

    TransferStep addStep(UUID tenantId, UUID transferRequestId,
                         int stepNumber, UUID approverUserId);

    Optional<TransferStep> findStep(UUID tenantId, UUID transferRequestId, int stepNumber);

    List<TransferStep> findSteps(UUID tenantId, UUID transferRequestId);

    void decideStep(UUID tenantId, UUID transferRequestId, int stepNumber,
                    TransferStepDecision decision, UUID approverUserId, String comment);

    void setWorkflowReference(UUID tenantId, UUID transferRequestId,
                              UUID workflowRunId, int currentApprovalStep);

    void updateState(UUID tenantId, UUID transferRequestId, TransferState newState,
                     UUID executedByUserId, String failureReason);
}
