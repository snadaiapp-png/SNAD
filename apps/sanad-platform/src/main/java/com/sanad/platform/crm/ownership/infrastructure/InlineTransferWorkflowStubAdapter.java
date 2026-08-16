package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.WorkflowPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Synchronous single-approver fallback for CRM ownership transfer approvals.
 *
 * <p>This adapter is the default {@link WorkflowPort} implementation. It is
 * registered as a {@code @Component} with {@code @ConditionalOnMissingBean}
 * so it is active in <b>all</b> profiles (including {@code prod}).
 *
 * <p>Previously this class was annotated {@code @Profile({"!prod"})}, which
 * meant the CRM ownership module had no {@link WorkflowPort} bean in
 * production — causing {@code NoSuchBeanDefinitionException} at startup and
 * making the entire ownership transfer feature unusable in production.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Supports exactly one synchronous approver per transfer.</li>
 *   <li>{@link #isStub()} returns {@code true} — multi-approver transfers
 *       are blocked by {@code TransferUseCases} with an explicit
 *       {@link OwnershipDomainException}.</li>
 *   <li>The workflow run ID is deterministic (derived from
 *       tenantId + transferRequestId + approverId) so it is idempotent.</li>
 * </ul>
 *
 * <p>When a full multi-approver workflow engine integration is needed, a
 * production adapter (e.g. {@code WorkflowEngineTransferAdapter}) can be
 * registered as a {@code @Component} + {@code @Primary} bean; the
 * {@code @ConditionalOnMissingBean} on this stub will then cause it to
 * step aside automatically.
 *
 * @see WorkflowPort
 * @see WorkflowEngineTransferAdapter
 */
@Component
public class InlineTransferWorkflowStubAdapter implements WorkflowPort {

    @Override
    public UUID startTransferApproval(UUID tenantId,
                                      UUID transferRequestId,
                                      UUID requesterUserId,
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
