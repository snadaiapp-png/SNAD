package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.WorkflowPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Explicit temporary adapter: supports exactly one synchronous approver. */
@Component
@Profile({"!prod"})
public class InlineTransferWorkflowStubAdapter implements WorkflowPort {

    @Override
    public UUID startTransferApproval(UUID tenantId,
                                      UUID transferRequestId,
                                      List<UUID> approverUserIds) {
        if (tenantId == null || transferRequestId == null
                || approverUserIds == null || approverUserIds.size() != 1
                || approverUserIds.get(0) == null) {
            throw new OwnershipDomainException(
                    "Workflow stub supports exactly one explicit approver");
        }
        return UUID.nameUUIDFromBytes(
                (tenantId + ":" + transferRequestId + ":" + approverUserIds.get(0))
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void cancelApproval(UUID tenantId, UUID workflowRunId, String reason) {
        if (tenantId == null || workflowRunId == null) {
            throw new OwnershipDomainException("Complete workflow cancellation required");
        }
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
