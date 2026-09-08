package com.sanad.platform.hr.recruitment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.domain.ComplianceDecision;
import com.sanad.platform.hr.compliance.domain.ComplianceDecisionType;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.domain.HrJobOpening;
import com.sanad.platform.hr.recruitment.domain.HrJobOpeningState;
import com.sanad.platform.hr.recruitment.domain.HrJobOpeningTransitions;
import com.sanad.platform.hr.recruitment.domain.HrTransitionContext;
import com.sanad.platform.hr.recruitment.domain.HrTransitionDecision;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrJobOpeningRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 T3 — HrJobOpening application service (design §5.1, §6.1, §9, §12).
 *
 * <p>Every command: capability check (G0 scoped authorization, fail-closed) →
 * tenant-scoped load → §6.1 domain guard → (publish: G0 compliance gate with
 * a resolver-written decision row) → optimistic in-transaction transition
 * with audit (+ §12 outbox event where the matrix mandates one). Filled
 * headcount is intentionally NOT mutable here — it is derived only from hire
 * conversion linkage (§7, T8).</p>
 */
@Service
public class HrJobOpeningService {

    public static final String OPERATION_OPENING_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";

    static final String ACTION_CREATED = "HRM.RECRUITMENT.OPENING_CREATED";
    static final String ACTION_SUBMITTED = "HRM.RECRUITMENT.OPENING_SUBMITTED";
    static final String ACTION_PUBLISHED = "HRM.RECRUITMENT.OPENING_PUBLISHED";
    static final String ACTION_REJECTED = "HRM.RECRUITMENT.OPENING_REJECTED";
    static final String ACTION_PAUSED = "HRM.RECRUITMENT.OPENING_PAUSED";
    static final String ACTION_RESUMED = "HRM.RECRUITMENT.OPENING_RESUMED";
    static final String ACTION_CLOSED = "HRM.RECRUITMENT.OPENING_CLOSED";
    static final String ACTION_CANCELLED = "HRM.RECRUITMENT.OPENING_CANCELLED";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcHrJobOpeningRepository repository;
    private final RecruitmentAuthorizationPort authorization;
    private final ComplianceEngine complianceEngine;

    @Autowired
    public HrJobOpeningService(JdbcHrJobOpeningRepository repository,
                               RecruitmentAuthorizationPort authorization,
                               ComplianceEngine complianceEngine) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.complianceEngine = Objects.requireNonNull(complianceEngine, "complianceEngine");
    }

    public UUID create(HrCommandContext ctx, UUID jobId, UUID jobVersionId, UUID orgUnitId,
                       UUID positionId, int requestedHeadcount,
                       OffsetDateTime opensAt, OffsetDateTime closesAt) {
        authorization.requireOpeningManage(ctx, null);
        validateReferences(ctx, jobId, jobVersionId, orgUnitId, positionId);
        HrJobOpening draft = HrJobOpening.draft(ctx.tenantId(), jobId, jobVersionId, orgUnitId,
                positionId, requestedHeadcount, opensAt, closesAt);
        HrJobOpening inserted = repository.insert(draft, ctx.actorUserId(), ctx.correlationId(), null);
        return inserted.id();
    }

    public void submit(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningManage(ctx, openingId);
        transition(ctx, openingId, HrJobOpeningState.DRAFT, HrJobOpeningState.PENDING_APPROVAL,
                null, ACTION_SUBMITTED, null);
    }

    public void approve(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningPublish(ctx, openingId);
        HrJobOpening opening = load(ctx, openingId);
        checkGuard(ctx, opening.state(), HrJobOpeningState.OPEN);
        enforceSeparationOfDuties(ctx, openingId);
        ComplianceDecision decision = complianceEngine.evaluateOpeningPublish(
                ctx, opening.orgUnitId(), resource(openingId), LocalDate.now());
        if (decision.type() != ComplianceDecisionType.COMPLIANT
                && decision.type() != ComplianceDecisionType.GLOBAL_MODE_ALLOWED) {
            // The decision row is already persisted by the engine (resolver-written).
            throw new IllegalStateException("HRM_COMPLIANCE_BLOCKED: opening publish denied by compliance ("
                    + decision.type().name() + " / " + decision.reasonCode() + ")");
        }
        transition(ctx, openingId, HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.OPEN,
                decision.type().name(), ACTION_PUBLISHED, ACTION_PUBLISHED);
    }

    public void reject(HrCommandContext ctx, UUID openingId, String reasonCode) {
        authorization.requireOpeningPublish(ctx, openingId);
        HrJobOpening opening = load(ctx, openingId);
        // The §6.1 matrix requires a REGISTERED reason code for PENDING_APPROVAL→DRAFT;
        // the guard (not the service) is the fail-closed validator.
        HrTransitionDecision decision = HrJobOpeningTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), reasonCode),
                opening.state(), HrJobOpeningState.DRAFT);
        if (!decision.allowed()) {
            throw new IllegalStateException("HRM_REASON_REJECTED: reject requires a registered reason code ("
                    + decision.violationCode() + ")");
        }
        repository.transition(ctx.tenantId(), openingId, opening.state(), HrJobOpeningState.DRAFT,
                opening.version(), null, ACTION_REJECTED, null, null,
                ctx.actorUserId(), ctx.correlationId(), null);
    }

    public void pause(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningManage(ctx, openingId);
        transition(ctx, openingId, HrJobOpeningState.OPEN, HrJobOpeningState.PAUSED,
                null, ACTION_PAUSED, ACTION_PAUSED);
    }

    public void resume(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningManage(ctx, openingId);
        transition(ctx, openingId, HrJobOpeningState.PAUSED, HrJobOpeningState.OPEN,
                null, ACTION_RESUMED, ACTION_RESUMED);
    }

    public void close(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningManage(ctx, openingId);
        HrJobOpening opening = load(ctx, openingId);
        // §6.1: OPEN→CLOSED and PAUSED→CLOSED are both allowed.
        checkGuard(ctx, opening.state(), HrJobOpeningState.CLOSED);
        repository.transition(ctx.tenantId(), openingId, opening.state(), HrJobOpeningState.CLOSED,
                opening.version(), null, ACTION_CLOSED, ACTION_CLOSED, payload(ctx, opening, ACTION_CLOSED),
                ctx.actorUserId(), ctx.correlationId(), null);
    }

    public void cancel(HrCommandContext ctx, UUID openingId) {
        authorization.requireOpeningManage(ctx, openingId);
        HrJobOpening opening = load(ctx, openingId);
        checkGuard(ctx, opening.state(), HrJobOpeningState.CANCELLED);
        repository.transition(ctx.tenantId(), openingId, opening.state(), HrJobOpeningState.CANCELLED,
                opening.version(), null, ACTION_CANCELLED, ACTION_CANCELLED,
                payload(ctx, opening, ACTION_CANCELLED), ctx.actorUserId(), ctx.correlationId(), null);
    }

    // --- internals ---

    private void transition(HrCommandContext ctx, UUID openingId,
                            HrJobOpeningState from, HrJobOpeningState to,
                            String complianceDecision, String auditAction, String outboxEventType) {
        HrJobOpening opening = load(ctx, openingId);
        checkGuard(ctx, opening.state(), to);
        ObjectNode payload = outboxEventType == null ? null : payload(ctx, opening, outboxEventType);
        repository.transition(ctx.tenantId(), openingId, opening.state(), to, opening.version(),
                complianceDecision, auditAction, outboxEventType, payload,
                ctx.actorUserId(), ctx.correlationId(), null);
    }

    private HrJobOpening load(HrCommandContext ctx, UUID openingId) {
        return repository.find(ctx.tenantId(), openingId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_OPENING_NOT_FOUND: " + openingId + " is not visible to this tenant context"));
    }

    private void checkGuard(HrCommandContext ctx, HrJobOpeningState from, HrJobOpeningState to) {
        HrTransitionDecision decision = HrJobOpeningTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), null), from, to);
        if (!decision.allowed()) {
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + from + " → " + to
                    + (decision.violationCode() == null ? "" : " (" + decision.violationCode() + ")"));
        }
    }

    /**
     * §6.1 separation of duties: the approver must not be the actor who
     * submitted the opening for approval. Attribution comes from the
     * transactional audit ledger (G0 authority), never from request data.
     */
    private void enforceSeparationOfDuties(HrCommandContext ctx, UUID openingId) {
        repository.lastActionActor(ctx.tenantId(), openingId, ACTION_SUBMITTED)
                .filter(submitter -> submitter.equals(ctx.actorUserId()))
                .ifPresent(submitter -> {
                    throw new IllegalStateException(
                            "HRM_SOD_DENIED: the submitter of an opening may not approve its publication");
                });
    }

    private void validateReferences(HrCommandContext ctx, UUID jobId, UUID jobVersionId,
                                    UUID orgUnitId, UUID positionId) {
        if (!repository.jobExistsInTenant(ctx.tenantId(), jobId)) {
            throw new IllegalStateException("HRM_OPENING_JOB_NOT_IN_TENANT: " + jobId);
        }
        if (!repository.orgUnitExistsInTenant(ctx.tenantId(), orgUnitId)) {
            throw new IllegalStateException("HRM_OPENING_ORG_UNIT_NOT_IN_TENANT: " + orgUnitId);
        }
        if (positionId != null && !repository.positionExistsInTenant(ctx.tenantId(), positionId)) {
            throw new IllegalStateException("HRM_OPENING_POSITION_NOT_IN_TENANT: " + positionId);
        }
        if (jobVersionId != null && !repository.jobVersionExistsInTenant(ctx.tenantId(), jobId, jobVersionId)) {
            throw new IllegalStateException("HRM_OPENING_JOB_VERSION_NOT_IN_TENANT: " + jobVersionId);
        }
    }

    private ComplianceResource resource(UUID openingId) {
        return new ComplianceResource(JdbcHrJobOpeningRepository.RESOURCE_TYPE, openingId);
    }

    private ObjectNode payload(HrCommandContext ctx, HrJobOpening opening, String eventType) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("event_type", eventType);
        payload.put("opening_id", opening.id().toString());
        payload.put("job_id", opening.jobId().toString());
        payload.put("org_unit_id", opening.orgUnitId().toString());
        payload.put("requested_headcount", opening.requestedHeadcount());
        if (opening.complianceDecision() != null) {
            payload.put("compliance_decision", opening.complianceDecision());
        }
        return payload;
    }
}
