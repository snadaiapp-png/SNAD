package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.UUID;

/**
 * Boundary to the central Workflow Engine for transfer approvals.
 *
 * <p>The {@link #startTransferApproval} method receives the requesterUserId
 * so the real adapter can create a WorkflowInstance with the correct
 * startedBy FK. The stub adapter ignores it (uses a deterministic UUID).
 */
public interface WorkflowPort {

    UUID startTransferApproval(UUID tenantId,
                               UUID transferRequestId,
                               UUID requesterUserId,
                               List<UUID> approverUserIds);

    void cancelApproval(UUID tenantId, UUID workflowRunId, String reason);

    /** True while only the synchronous single-approver fallback is installed. */
    boolean isStub();
}
