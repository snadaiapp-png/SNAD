package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.UUID;

/** Boundary to the central Workflow Engine for transfer approvals. */
public interface WorkflowPort {

    UUID startTransferApproval(UUID tenantId,
                               UUID transferRequestId,
                               List<UUID> approverUserIds);

    void cancelApproval(UUID tenantId, UUID workflowRunId, String reason);

    /** True while only the synchronous single-approver fallback is installed. */
    boolean isStub();
}
