package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.application.HireApprovalWorkflowPort.ApprovalOutcome;
import com.sanad.platform.hr.recruitment.application.HireApprovalWorkflowPort.ApprovalSnapshot;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T8 — {@code HrHireApprovalLinkService}: the authoritative bridge
 * between the governed hire conversion and Workflow Y2 for the OPTIONAL hire
 * approval (design §11.3; directive T8.8).
 *
 * <p>Policy semantics (§T8.8): the tenant policy is resolved ONLY from the
 * authoritative tenant configuration ({@code hr_tenant_policies},
 * {@code HRM.RECRUITMENT.HIRE_APPROVAL = ON}). A missing or any other value
 * resolves to the documented default OFF — ON is never inferred. When ON:</p>
 * <ul>
 *   <li>the FIRST conversion attempt starts (or reuses) the Y2 approval and
 *       is REFUSED with {@code HRM_HIRE_APPROVAL_REQUIRED} — approval is a
 *       precondition, never a post-action;</li>
 *   <li>a RUNNING approval ⇒ {@code HRM_HIRE_APPROVAL_PENDING};</li>
 *   <li>a REJECTED/CANCELLED approval ⇒ conversion refused fail-closed
 *       ({@code HRM_HIRE_APPROVAL_NOT_APPROVED}); the offer stays ACCEPTED
 *       and a human decides the next step;</li>
 *   <li>only an APPROVED instance correlated to THIS tenant + offer
 *       authorizes the conversion; a foreign offer's grant never applies
 *       (the grant is keyed by (offer, approval-instance));</li>
 *   <li>SoD where enabled: converter ≠ approver is enforced by the engine
 *       (requiredCapability + SelfApproval.DENY) — never by HRM.</li>
 * </ul>
 */
@Service
public class HrHireApprovalLinkService {

    private final HireApprovalWorkflowPort hireApprovalWorkflow;
    private final JdbcHrHireConversionRepository repository;

    @Autowired
    public HrHireApprovalLinkService(HireApprovalWorkflowPort hireApprovalWorkflow,
                                     JdbcHrHireConversionRepository repository) {
        this.hireApprovalWorkflow = Objects.requireNonNull(hireApprovalWorkflow, "hireApprovalWorkflow");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** §T8.8: policy ON only via the authoritative tenant configuration. */
    public boolean isApprovalRequired(UUID tenantId) {
        return repository.isHireApprovalPolicyEnabled(tenantId);
    }

    /**
     * Verifies the §11.3 approval precondition for the conversion command.
     * When no approval cycle exists yet, one is STARTED and the conversion
     * is refused (the approval is the trigger's outcome, not a side quest).
     * Returns silently ONLY with an APPROVED, correlated grant.
     */
    public void requireApprovedForConversion(HrCommandContext ctx, UUID offerId) {
        Optional<UUID> latest = hireApprovalWorkflow.findLatestApproval(ctx.tenantId(), offerId);
        if (latest.isEmpty()) {
            UUID instanceId = hireApprovalWorkflow.startHireApproval(
                    ctx.tenantId(), offerId, ctx.actorUserId());
            requireCorrelatedInstance(ctx, offerId, instanceId,
                    "HRM_HIRE_APPROVAL_REQUIRED: hire approval is enabled for this tenant; "
                            + "an authoritative Workflow Y2 approval was started for offer " + offerId
                            + " and must reach APPROVED before the conversion can run");
        }
        UUID instanceId = latest.get();
        ApprovalSnapshot snapshot = requireCorrelatedInstance(ctx, offerId, instanceId, null);
        if (snapshot.outcome() == ApprovalOutcome.APPROVED) {
            return;
        }
        if ("COMPLETED".equals(snapshot.status())) {
            throw new IllegalStateException("HRM_HIRE_APPROVAL_NOT_APPROVED: the authoritative hire approval "
                    + "resolved to " + snapshot.outcome() + "; the conversion is refused fail-closed and "
                    + "the offer remains ACCEPTED");
        }
        throw new IllegalStateException("HRM_HIRE_APPROVAL_PENDING: the authoritative hire approval is "
                + snapshot.status() + "; no conversion without a final APPROVED outcome");
    }

    /**
     * Cancels (idempotently) the open hire approval for an offer — used when
     * the offer leaves the convertible state; pending work items never stay
     * actionable.
     */
    public void cancelOpenApproval(HrCommandContext ctx, UUID offerId) {
        hireApprovalWorkflow.findLatestApproval(ctx.tenantId(), offerId)
                .ifPresent(instanceId -> hireApprovalWorkflow.cancelHireApproval(
                        ctx.tenantId(), instanceId, ctx.actorUserId(), "HRM_HIRE_APPROVAL_CANCELLED"));
    }

    private ApprovalSnapshot requireCorrelatedInstance(HrCommandContext ctx, UUID offerId,
                                                       UUID workflowInstanceId, String requiredMessage) {
        ApprovalSnapshot snapshot = hireApprovalWorkflow.loadHireApprovalOutcome(
                ctx.tenantId(), workflowInstanceId);
        if (snapshot == null
                || !HireApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                || !offerId.equals(snapshot.businessEntityId())
                || snapshot.definitionVersionId() == null) {
            throw new IllegalStateException(requiredMessage != null ? requiredMessage
                    : "HRM_HIRE_APPROVAL_LINK_INVALID: the workflow instance " + workflowInstanceId
                    + " does not correlate this tenant and offer " + offerId);
        }
        return snapshot;
    }
}
