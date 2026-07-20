package com.sanad.platform.crm.ownership.domain;

/**
 * Port for the central Workflow Engine (CRM-008A design).
 *
 * <p><b>Design-only marker interface.</b> The full method set will be declared
 * in CRM-008D (Transfers) when the transfer approval workflow is implemented.</p>
 *
 * <p>CRM-008 does NOT implement its own workflow engine. Approval chains,
 * escalations, timers, and SLA-driven reassignments are delegated to the
 * central Workflow Engine via this port.</p>
 *
 * <p>Until the central Workflow Engine is built, a synchronous stub adapter
 * will handle single-approver transfers inline. Multi-step approvals are
 * explicitly deferred to CRM-008D and require the real engine.</p>
 *
 * <p><b>Planned methods</b> (to be added in CRM-008D):
 * <pre>
 *   UUID startTransferApproval(TransferApprovalRequest request);
 *   ApprovalState getApprovalState(UUID workflowRunId);
 *   void cancelApproval(UUID workflowRunId, String reason);
 *   boolean isStub();
 * </pre>
 * </p>
 */
public interface WorkflowPort {
    // Marker interface — methods added in CRM-008D.
    // See Javadoc above for the planned contract.
}
