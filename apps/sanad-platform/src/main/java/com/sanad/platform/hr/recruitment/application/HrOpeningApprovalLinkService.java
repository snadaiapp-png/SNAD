package com.sanad.platform.hr.recruitment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort.ApprovalOutcome;
import com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort.ApprovalSnapshot;
import com.sanad.platform.hr.recruitment.domain.HrJobOpeningState;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T7 — {@code HrOpeningApprovalLinkService}: the authoritative bridge
 * between the HrJobOpening lifecycle and Workflow Y2 (directive §2 — the
 * missing T3 workflow hook).
 *
 * <p>Authoritative semantics implemented here:</p>
 * <ul>
 *   <li><b>submit</b> creates or finds (idempotently) the Y2 approval
 *       instance/work item for the opening — one open workflow approval per
 *       (opening_id, submitted-state-period); duplicate submissions return
 *       the SAME logical reference;</li>
 *   <li><b>apply</b> moves PENDING_APPROVAL → OPEN ONLY after the
 *       authoritative Workflow Y2 state proves APPROVED — and that proof is
 *       re-verified INSIDE the governed transactional mutation boundary
 *       (FOR SHARE on the instance row + conditional UPDATE), so a stale
 *       pre-read cannot race; the {@code OPENING.PUBLISH} capability is
 *       re-checked at apply-time by the caller before this verification;</li>
 *   <li><b>REJECTED</b> returns the opening to DRAFT (mandatory registered
 *       reason, history retained);</li>
 *   <li><b>CANCELLED</b> cancels the Y2 instance and every pending work
 *       item — orphan work items never remain actionable;</li>
 *   <li><b>TIMEOUT</b> is Y2-native escalation (SLA on the approval step) —
 *       there is NO auto-approval path in HRM.</li>
 * </ul>
 *
 * <p>NO executable service/repository/API path may transition
 * PENDING_APPROVAL → OPEN unless the authoritative Workflow Y2 state proves
 * APPROVED inside the mutation transaction (pinned by the bypass tests).</p>
 */
@Service
public class HrOpeningApprovalLinkService {

    private final OpeningApprovalWorkflowPort openingApprovalWorkflow;
    private final JdbcHrJobOpeningRepository repository;

    @Autowired
    public HrOpeningApprovalLinkService(OpeningApprovalWorkflowPort openingApprovalWorkflow,
                                        JdbcHrJobOpeningRepository repository) {
        this.openingApprovalWorkflow = Objects.requireNonNull(openingApprovalWorkflow, "openingApprovalWorkflow");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Creates or finds the idempotent Y2 approval for the submitted opening
     * and returns the authoritative workflow instance id. Workflow creation
     * happens BEFORE the opening leaves DRAFT: a failing start leaves the
     * opening untouched (fail-closed, directive §T7.6 semantics).
     */
    public UUID startApprovalForSubmission(HrCommandContext ctx, UUID openingId) {
        UUID workflowInstanceId = openingApprovalWorkflow.startOpeningApproval(
                ctx.tenantId(), openingId, ctx.actorUserId());
        ApprovalSnapshot snapshot = openingApprovalWorkflow.loadOpeningApprovalOutcome(
                ctx.tenantId(), workflowInstanceId);
        if (snapshot == null
                || !OpeningApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                || !openingId.equals(snapshot.businessEntityId())
                || snapshot.definitionVersionId() == null) {
            throw new IllegalStateException("HRM_OPENING_APPROVAL_LINK_INVALID: the started workflow instance "
                    + "does not correlate this tenant and opening");
        }
        return workflowInstanceId;
    }

    /**
     * Self-heals a PENDING_APPROVAL opening that has no persisted correlation
     * (e.g. a crash between transition and correlation persistence): reuses
     * the OPEN Y2 instance instead of creating a duplicate.
     */
    public UUID healMissingCorrelation(HrCommandContext ctx, UUID openingId) {
        UUID workflowInstanceId = openingApprovalWorkflow.startOpeningApproval(
                ctx.tenantId(), openingId, ctx.actorUserId());
        ApprovalSnapshot snapshot = openingApprovalWorkflow.loadOpeningApprovalOutcome(
                ctx.tenantId(), workflowInstanceId);
        if (snapshot == null || snapshot.definitionVersionId() == null) {
            throw new IllegalStateException("HRM_OPENING_APPROVAL_LINK_INVALID: cannot heal the correlation");
        }
        repository.persistSubmissionWorkflow(ctx.tenantId(), openingId, workflowInstanceId,
                snapshot.definitionVersionId());
        return workflowInstanceId;
    }

    /**
     * PENDING_APPROVAL → OPEN. The {@code OPENING.PUBLISH} capability and the
     * compliance gate are enforced by the caller AT APPLY-TIME; this method
     * then verifies the authoritative Y2 APPROVED outcome INSIDE the mutation
     * transaction and performs the conditional transition. Without a linked
     * approval (or with a non-APPROVED outcome) the transition is impossible.
     */
    public void verifyApprovedAndApplyOpen(HrCommandContext ctx, UUID openingId,
                                           String complianceDecision, JsonNode eventPayload) {
        UUID workflowInstanceId = requireLinkedApproval(ctx, openingId);
        repository.approveWithVerifiedWorkflow(ctx.tenantId(), openingId, workflowInstanceId,
                complianceDecision, eventPayload, ctx.actorUserId(), ctx.correlationId(), null);
    }

    /**
     * Workflow Y2 REJECTED → opening DRAFT (registered reason mandatory,
     * history retained): verified IN-TRANSACTION against the authoritative
     * outcome, correlation closed.
     */
    public void verifyRejectedAndApplyDraft(HrCommandContext ctx, UUID openingId, UUID workflowInstanceId,
                                            String reasonCode) {
        requireLinkedApprovalForInstance(ctx, openingId, workflowInstanceId);
        repository.rejectWithVerifiedWorkflow(ctx.tenantId(), openingId, workflowInstanceId, reasonCode,
                ctx.actorUserId(), ctx.correlationId(), null);
    }

    /**
     * §9 orphan cleanup: before an opening becomes CANCELLED (or returns to
     * DRAFT via operator rejection) its open Y2 approval is cancelled so no
     * pending work item remains actionable. Idempotent.
     */
    public void cancelOpenApproval(HrCommandContext ctx, UUID openingId) {
        Optional<UUID> open = repository.findPendingWorkflowInstanceId(ctx.tenantId(), openingId);
        open.ifPresent(instanceId -> openingApprovalWorkflow.cancelOpeningApproval(ctx.tenantId(), instanceId,
                ctx.actorUserId(), "HRM_OPENING_APPROVAL_CANCELLED"));
    }

    public Optional<UUID> findOpenApproval(HrCommandContext ctx, UUID openingId) {
        return repository.findPendingWorkflowInstanceId(ctx.tenantId(), openingId);
    }

    public ApprovalSnapshot loadOutcome(HrCommandContext ctx, UUID workflowInstanceId) {
        return openingApprovalWorkflow.loadOpeningApprovalOutcome(ctx.tenantId(), workflowInstanceId);
    }

    // ==================== internals ====================

    private UUID requireLinkedApproval(HrCommandContext ctx, UUID openingId) {
        Optional<UUID> open = repository.findPendingWorkflowInstanceId(ctx.tenantId(), openingId);
        if (open.isEmpty()) {
            // THE BYPASS IS GONE: PENDING_APPROVAL → OPEN without an
            // authoritative, linked Y2 approval is impossible.
            throw new IllegalStateException("HRM_OPENING_APPROVAL_NOT_LINKED: opening " + openingId
                    + " has no authoritative Workflow Y2 approval; PENDING_APPROVAL → OPEN is impossible "
                    + "without one");
        }
        return open.get();
    }

    private void requireLinkedApprovalForInstance(HrCommandContext ctx, UUID openingId, UUID workflowInstanceId) {
        UUID linked = requireLinkedApproval(ctx, openingId);
        if (!linked.equals(workflowInstanceId)) {
            throw new IllegalStateException("HRM_OPENING_APPROVAL_STALE: workflow instance " + workflowInstanceId
                    + " is not the open approval for opening " + openingId);
        }
    }

    /** Guard helper shared with the service (keeps the matrix authority local). */
    public boolean isOpenState(HrJobOpeningState state) {
        return state == HrJobOpeningState.OPEN;
    }
}
