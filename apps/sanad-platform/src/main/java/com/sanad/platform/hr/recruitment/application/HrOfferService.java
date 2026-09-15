package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort.ApprovalOutcome;
import com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort.ApprovalSnapshot;
import com.sanad.platform.hr.recruitment.domain.HrOffer;
import com.sanad.platform.hr.recruitment.domain.HrOfferState;
import com.sanad.platform.hr.recruitment.domain.HrOfferTransitions;
import com.sanad.platform.hr.recruitment.domain.HrOfferVersion;
import com.sanad.platform.hr.recruitment.domain.HrTransitionContext;
import com.sanad.platform.hr.recruitment.domain.HrTransitionDecision;
import com.sanad.platform.hr.recruitment.domain.RecruitmentReasonCodes;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T7 — HrOffer application authority (design §5.1/§6.3/§11.2, restored
 * semantics).
 *
 * <p>ALL offer lifecycle transitions pass through this service and the
 * {@link HrOfferTransitions} domain guard — never through controllers and
 * never through the JDBC repository. The DRAFT→EXTENDED approval bypass is
 * eliminated: {@code EXTENDED} is reachable ONLY from {@code PENDING_APPROVAL}
 * and ONLY after the authoritative Workflow Y2 APPROVED outcome has been
 * verified against the persisted correlation (same tenant, same offer, same
 * offer version, same authoritative workflow instance, valid final outcome).
 * Fail-closed on invalid transitions; terminal states remain terminal.</p>
 *
 * <p>Application consistency (§T7.10): an offer can only be created while its
 * application is in the OFFER recruiting stage, and NOTHING in this service
 * moves an application to HIRED — the governed hire conversion (T8) remains
 * the only writer.</p>
 */
@Service
public class HrOfferService {

    private static final String APPLICATION_STATE_OFFER = "OFFER";

    private final JdbcHrOfferRepository repository;
    private final RecruitmentAuthorizationPort authorization;
    private final OfferApprovalWorkflowPort offerApprovalWorkflow;

    @Autowired
    public HrOfferService(JdbcHrOfferRepository repository,
                          RecruitmentAuthorizationPort authorization,
                          OfferApprovalWorkflowPort offerApprovalWorkflow) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.offerApprovalWorkflow = Objects.requireNonNull(offerApprovalWorkflow, "offerApprovalWorkflow");
    }

    // ==================== aggregate + versions ====================

    public UUID createOffer(HrCommandContext ctx, UUID applicationId, String contractTerms,
                            String compensation, OffsetDateTime expiresAt) {
        authorization.requireOfferManage(ctx, null);
        requireMaterialTerms(contractTerms, compensation);
        String applicationState = repository.applicationState(ctx.tenantId(), applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_APPLICATION_NOT_FOUND: " + applicationId + " is not visible to this tenant context"));
        if (!APPLICATION_STATE_OFFER.equals(applicationState)) {
            // §T7.10: the application must be in the legally allowed recruiting stage.
            throw new IllegalStateException("HRM_APPLICATION_STAGE_CONFLICT: offers require the application "
                    + "to be in stage OFFER (was " + applicationState + ")");
        }
        UUID offerId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        HrOfferVersion version = new HrOfferVersion(versionId, ctx.tenantId(), offerId, 1,
                null, contractTerms, compensation, ctx.actorUserId(), null);
        HrOffer offer = new HrOffer(offerId, ctx.tenantId(), applicationId, nextOfferNumber(),
                HrOfferState.DRAFT, null, null, null, null, expiresAt, null, 0L);
        try {
            repository.insertOffer(offer, version, ctx.actorUserId(), ctx.correlationId());
        } catch (IllegalStateException conflict) {
            if (!"HRM_OFFER_NUMBER_CONFLICT".equals(codeOf(conflict))) {
                throw conflict;
            }
            // regenerate the tenant-unique business number exactly once
            offer = new HrOffer(offerId, ctx.tenantId(), applicationId, nextOfferNumber(),
                    HrOfferState.DRAFT, null, null, null, null, expiresAt, null, 0L);
            repository.insertOffer(offer, version, ctx.actorUserId(), ctx.correlationId());
        }
        return offerId;
    }

    /**
     * Creates the NEXT immutable version. DRAFT revisions swap the current
     * draft terms; EXTENDED revisions stage a pending candidate that must
     * repeat the governed approval path before re-extension (§T7.9) — the
     * previously extended version remains immutable.
     */
    public UUID reviseOffer(HrCommandContext ctx, UUID offerId, String contractTerms,
                            String compensation, OffsetDateTime expiresAt) {
        authorization.requireOfferManage(ctx, offerId);
        requireMaterialTerms(contractTerms, compensation);
        HrOffer offer = load(ctx, offerId);
        switch (offer.state()) {
            case DRAFT -> {
                HrOfferVersion version = repository.insertVersion(ctx.tenantId(), offerId, contractTerms,
                        compensation, expiresAt, true, JdbcHrOfferRepository.ACTION_REVISED,
                        ctx.actorUserId(), ctx.correlationId());
                return version.id();
            }
            case EXTENDED -> {
                HrOfferVersion version = repository.insertVersion(ctx.tenantId(), offerId, contractTerms,
                        compensation, expiresAt, false, JdbcHrOfferRepository.ACTION_REVISED,
                        ctx.actorUserId(), ctx.correlationId());
                return version.id();
            }
            case PENDING_APPROVAL -> throw new IllegalStateException(
                    "HRM_OFFER_EDIT_LOCKED: an approval is in flight; resolve it before revising");
            default -> throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: "
                    + offer.state() + " is terminal; a new offer revision cycle is not available");
        }
    }

    // ==================== T7.6 — approval submission ====================

    /**
     * Canonical flow: validate → freeze the submitted version → start the
     * authoritative Workflow Y2 approval instance → transition the offer to
     * PENDING_APPROVAL → persist the workflow correlation → audit.
     *
     * <p>Idempotent: repeated submission while an approval is open returns the
     * SAME authoritative workflow instance (no duplicate approval instances).
     * If workflow creation fails, the offer remains DRAFT with no
     * recoverable-less correlation.</p>
     */
    public UUID submitForApproval(HrCommandContext ctx, UUID offerId) {
        authorization.requireOfferExtend(ctx, offerId);
        HrOffer offer = load(ctx, offerId);

        if (offer.state() == HrOfferState.PENDING_APPROVAL) {
            if (offer.pendingWorkflowInstanceId() == null) {
                throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: PENDING_APPROVAL without correlation");
            }
            return offer.pendingWorkflowInstanceId();
        }

        UUID submittedVersionId;
        if (offer.state() == HrOfferState.DRAFT) {
            if (offer.pendingWorkflowInstanceId() != null) {
                throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: DRAFT offer retains an approval "
                        + "correlation — close the cycle before resubmitting");
            }
            submittedVersionId = offer.currentVersionId();
            if (submittedVersionId == null) {
                throw new IllegalStateException("HRM_OFFER_NO_TERMS: the offer has no version to submit");
            }
        } else if (offer.state() == HrOfferState.EXTENDED) {
            // §T7.9 re-extension: requires a staged pending revision.
            submittedVersionId = offer.pendingOfferVersionId();
            if (submittedVersionId == null) {
                throw new IllegalStateException("HRM_OFFER_NO_PENDING_REVISION: revise the extended offer "
                        + "to stage a new version before resubmitting");
            }
        } else {
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + offer.state()
                    + " cannot be submitted for approval");
        }

        UUID workflowInstanceId = offerApprovalWorkflow.startOfferApproval(
                ctx.tenantId(), offerId, submittedVersionId, ctx.actorUserId());
        ApprovalSnapshot snapshot = offerApprovalWorkflow.loadApprovalOutcome(ctx.tenantId(), workflowInstanceId);
        if (snapshot == null
                || !OfferApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                || !offerId.equals(snapshot.businessEntityId())
                || snapshot.definitionVersionId() == null) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_LINK_INVALID: the started workflow instance "
                    + "does not correlate this tenant and offer");
        }
        HrOfferState targetState = offer.state() == HrOfferState.EXTENDED
                ? HrOfferState.EXTENDED : HrOfferState.PENDING_APPROVAL;
        repository.persistSubmission(ctx.tenantId(), offerId, offer.state(), targetState, workflowInstanceId,
                snapshot.definitionVersionId(), submittedVersionId, ctx.actorUserId(), ctx.correlationId());
        return workflowInstanceId;
    }

    // ==================== T7.7 — approval success ====================

    /**
     * PENDING_APPROVAL → EXTENDED (exactly once) after verifying the
     * authoritative Workflow Y2 APPROVED result: same tenant, same offer,
     * same offer version, same authoritative workflow instance, valid final
     * approval outcome. Repeated/replayed completion is side-effect-free.
     * Extension of an EXTENDED offer's pending revision swaps the current
     * version without demoting the offer (§T7.9).
     */
    public void extendFromApproval(HrCommandContext ctx, UUID offerId, UUID workflowInstanceId) {
        authorization.requireOfferExtend(ctx, offerId);
        HrOffer offer = load(ctx, offerId);
        boolean openCorrelation = workflowInstanceId != null
                && workflowInstanceId.equals(offer.pendingWorkflowInstanceId());
        if (!openCorrelation) {
            if (workflowInstanceId != null && repository.hasActionEvidence(ctx.tenantId(), offerId,
                    JdbcHrOfferRepository.ACTION_EXTENDED, workflowInstanceId)) {
                return; // idempotent duplicate callback for an already-applied cycle
            }
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance " + workflowInstanceId
                    + " is not the open approval for offer " + offerId);
        }
        verifyApprovalLink(offer, workflowInstanceId);
        ApprovalSnapshot snapshot = requireCompletedSnapshot(ctx, offer, workflowInstanceId);
        if (snapshot.outcome() != ApprovalOutcome.APPROVED) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_MISMATCH: the authoritative outcome is "
                    + snapshot.outcome() + "; extension requires APPROVED");
        }
        if (offer.state() == HrOfferState.PENDING_APPROVAL) {
            repository.persistExtension(ctx.tenantId(), offerId, workflowInstanceId, offer.expiresAt(),
                    ctx.actorUserId(), ctx.correlationId());
            return;
        }
        repository.persistRevisionExtension(ctx.tenantId(), offerId, workflowInstanceId, offer.expiresAt(),
                ctx.actorUserId(), ctx.correlationId());
    }

    // ==================== T7.8 — rejection ====================

    /**
     * Workflow Y2 REJECTED → the restored HRM rejection semantics: the offer
     * returns to DRAFT with a registered reason as governed evidence; a
     * rejected EXTENDED revision only closes the correlation. The rejected
     * submitted version remains immutable historical evidence; resubmission
     * starts a NEW approval cycle that cannot reuse the obsolete result.
     */
    public void rejectFromApproval(HrCommandContext ctx, UUID offerId, UUID workflowInstanceId, String reasonCode) {
        authorization.requireOfferManage(ctx, offerId);
        HrOffer offer = load(ctx, offerId);
        boolean openCorrelation = workflowInstanceId != null
                && workflowInstanceId.equals(offer.pendingWorkflowInstanceId());
        if (!openCorrelation) {
            if (workflowInstanceId != null && repository.hasActionEvidence(ctx.tenantId(), offerId,
                    JdbcHrOfferRepository.ACTION_REJECTED, workflowInstanceId)) {
                return; // idempotent duplicate callback for an already-applied cycle
            }
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance " + workflowInstanceId
                    + " is not the open approval for offer " + offerId);
        }
        // Governed rejection reason/evidence — the same registered-reason bar
        // as the PENDING_APPROVAL → DRAFT matrix entry.
        HrTransitionDecision reason = HrOfferTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), reasonCode),
                HrOfferState.PENDING_APPROVAL, HrOfferState.DRAFT);
        if (!reason.allowed() && !RecruitmentReasonCodes.isRegistered(reasonCode)) {
            throw new IllegalStateException("HRM_REASON_REJECTED: a registered rejection reason is mandatory");
        }
        ApprovalSnapshot snapshot = requireCompletedSnapshot(ctx, offer, workflowInstanceId);
        if (snapshot.outcome() != ApprovalOutcome.REJECTED) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_MISMATCH: the authoritative outcome is "
                    + snapshot.outcome() + "; rejection reconciliation requires REJECTED");
        }
        if (offer.state() == HrOfferState.PENDING_APPROVAL) {
            repository.persistRejection(ctx.tenantId(), offerId, workflowInstanceId, reasonCode,
                    ctx.actorUserId(), ctx.correlationId());
            return;
        }
        repository.persistRevisionRejection(ctx.tenantId(), offerId, workflowInstanceId, reasonCode,
                ctx.actorUserId(), ctx.correlationId());
    }

    // ==================== terminal outcomes ====================

    public void accept(HrCommandContext ctx, UUID offerId) {
        authorization.requireOfferManage(ctx, offerId);
        HrOffer offer = load(ctx, offerId);
        HrTransitionDecision decision = HrOfferTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), null),
                offer.state(), HrOfferState.ACCEPTED);
        if (!decision.allowed()) {
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + offer.state() + " \u2192 "
                    + HrOfferState.ACCEPTED
                    + (decision.violationCode() == null ? "" : " (" + decision.violationCode() + ")"));
        }
        // §8: the expiry gate uses the DATABASE clock inside the governed
        // mutation — acceptance after expiry fails closed, no client clock.
        repository.acceptIfNotExpired(ctx.tenantId(), offerId, ctx.actorUserId(), ctx.correlationId());
        // §T7.10: the application is NOT touched — HIRED is conversion-only (T8).
    }

    public void decline(HrCommandContext ctx, UUID offerId) {
        transition(ctx, offerId, HrOfferState.DECLINED, null,
                JdbcHrOfferRepository.ACTION_DECLINED, null);
    }

    public void withdraw(HrCommandContext ctx, UUID offerId, String reasonCode) {
        transition(ctx, offerId, HrOfferState.WITHDRAWN, null,
                JdbcHrOfferRepository.ACTION_WITHDRAWN, reasonCode);
    }

    public void expire(HrCommandContext ctx, UUID offerId) {
        transition(ctx, offerId, HrOfferState.EXPIRED, null,
                JdbcHrOfferRepository.ACTION_EXPIRED, null);
    }

    // ==================== reads ====================

    public Optional<HrOffer> find(HrCommandContext ctx, UUID offerId) {
        authorization.requireOfferManage(ctx, offerId);
        return repository.find(ctx.tenantId(), offerId);
    }

    public List<HrOfferVersion> versions(HrCommandContext ctx, UUID offerId) {
        authorization.requireOfferManage(ctx, offerId);
        load(ctx, offerId);
        List<HrOfferVersion> history = repository.versions(ctx.tenantId(), offerId);
        // Design §14: reads of the offer compensation draft are sensitive-read
        // audited (HRM.COMPENSATION.VIEW semantics); the evidence payload carries
        // no compensation values.
        repository.recordCompensationRead(ctx.tenantId(), offerId, ctx.actorUserId(), ctx.correlationId());
        return history;
    }

    /**
     * §7/§9 APPROVAL CYCLE CANCELLATION: an open approval cycle may be
     * cancelled (governed reason). The authoritative Y2 instance is cancelled
     * through the engine (pending work items become non-actionable), and the
     * offer returns to DRAFT with the correlation closed. This is the ONLY
     * non-outcome way a PENDING_APPROVAL offer leaves its state — and it
     * NEVER produces EXTENDED.
     */
    public void cancelApprovalCycle(HrCommandContext ctx, UUID offerId, UUID workflowInstanceId,
                                    String reasonCode) {
        authorization.requireOfferExtend(ctx, offerId);
        HrOffer offer = load(ctx, offerId);
        if (!offer.hasOpenApproval() || !workflowInstanceId.equals(offer.pendingWorkflowInstanceId())) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance " + workflowInstanceId
                    + " is not the open approval for offer " + offerId);
        }
        if (!RecruitmentReasonCodes.isRegistered(reasonCode)) {
            throw new IllegalStateException("HRM_REASON_REJECTED: a registered reason is mandatory");
        }
        offerApprovalWorkflow.cancelOfferApproval(ctx.tenantId(), workflowInstanceId,
                ctx.actorUserId(), "HRM_OFFER_APPROVAL_CANCELLED:" + reasonCode);
        repository.persistApprovalCancellation(ctx.tenantId(), offerId, workflowInstanceId, reasonCode,
                ctx.actorUserId(), ctx.correlationId());
    }

    // ==================== internals ====================

    private void transition(HrCommandContext ctx, UUID offerId, HrOfferState to, OffsetDateTime expiresAt,
                            String auditAction, String reasonCode) {
        authorization.requireOfferManage(ctx, offerId);
        HrOffer offer = load(ctx, offerId);
        HrTransitionDecision decision = HrOfferTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), reasonCode),
                offer.state(), to);
        if (!decision.allowed()) {
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + offer.state() + " → " + to
                    + (decision.violationCode() == null ? "" : " (" + decision.violationCode() + ")"));
        }
        repository.transition(ctx.tenantId(), offerId, offer.state(), to, expiresAt,
                auditAction, ctx.actorUserId(), ctx.correlationId());
    }

    private void verifyApprovalLink(HrOffer offer, UUID workflowInstanceId) {
        if (offer.pendingWorkflowDefinitionVersionId() == null
                || offer.pendingOfferVersionId() == null) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_LINK_INVALID: the open approval correlation "
                    + "is incomplete");
        }
    }

    private ApprovalSnapshot requireCompletedSnapshot(HrCommandContext ctx, HrOffer offer, UUID workflowInstanceId) {
        UUID offerId = offer.id();
        ApprovalSnapshot snapshot = offerApprovalWorkflow.loadApprovalOutcome(ctx.tenantId(), workflowInstanceId);
        if (snapshot == null) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: workflow instance " + workflowInstanceId
                    + " does not exist in this tenant");
        }
        if (!OfferApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                || !offerId.equals(snapshot.businessEntityId())) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_LINK_INVALID: the workflow instance correlates "
                    + snapshot.businessEntityType() + "/" + snapshot.businessEntityId()
                    + "; a foreign or stale workflow result can never move this offer");
        }
        if (snapshot.definitionVersionId() == null
                || !snapshot.definitionVersionId().equals(offer.pendingWorkflowDefinitionVersionId())) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_STALE: the workflow instance was not started "
                    + "on the correlated authoritative definition version");
        }
        if (!"COMPLETED".equals(snapshot.status())) {
            throw new IllegalStateException("HRM_OFFER_APPROVAL_OUTCOME_PENDING: the authoritative workflow "
                    + "instance is " + snapshot.status() + "; no extension without a final outcome");
        }
        return snapshot;
    }

    private HrOffer load(HrCommandContext ctx, UUID offerId) {
        return repository.find(ctx.tenantId(), offerId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_OFFER_NOT_FOUND: " + offerId + " is not visible to this tenant context"));
    }

    private static void requireMaterialTerms(String contractTerms, String compensation) {
        if (contractTerms == null || contractTerms.isBlank() || compensation == null || compensation.isBlank()) {
            throw new IllegalStateException("HRM_OFFER_TERMS_REQUIRED: material terms are mandatory");
        }
    }

    private static String nextOfferNumber() {
        return "OFF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String codeOf(IllegalStateException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        int colon = message.indexOf(':');
        return colon < 0 ? message : message.substring(0, colon);
    }
}
