package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Port for the central Workflow Engine.
 *
 * <p>CRM-008 does NOT implement its own workflow engine. Approval chains,
 * escalations, timers, and SLA-driven reassignments are delegated to the
 * central Workflow Engine via this port.</p>
 *
 * <p>Until the central Workflow Engine is built, a synchronous stub adapter
 * will handle single-approver transfers inline. Multi-step approvals are
 * explicitly deferred to CRM-008D and require the real engine.</p>
 */
public interface WorkflowPort {

    /**
     * Starts an approval workflow for a transfer request.
     *
     * @return the workflow run id, used to correlate callbacks and audit events
     */
    UUID startTransferApproval(TransferApprovalRequest request);

    /**
     * Returns the current state of an approval workflow.
     */
    ApprovalState getApprovalState(UUID workflowRunId);

    /**
     * Cancels an in-progress approval workflow (e.g. when the transfer request
     * is CANCELLED by the requester before approval completes).
     */
    void cancelApproval(UUID workflowRunId, String reason);

    /**
     * Stub indicator: returns true when the implementation is a placeholder
     * (synchronous single-approver only). Used by health checks and the
     * capabilities catalog to flag that multi-step workflows are not yet
     * production-ready.
     */
    boolean isStub();
}
