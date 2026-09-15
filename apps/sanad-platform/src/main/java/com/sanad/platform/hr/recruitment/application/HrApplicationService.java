package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.domain.HrApplication;
import com.sanad.platform.hr.recruitment.domain.HrApplicationState;
import com.sanad.platform.hr.recruitment.domain.HrApplicationTransitions;
import com.sanad.platform.hr.recruitment.domain.HrTransitionContext;
import com.sanad.platform.hr.recruitment.domain.HrTransitionDecision;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 T5 — HrApplication application service (design §5.1, §6.2, §13.1
 * rows 9-12).
 *
 * <p>Forward-only one-stage pipeline movement with append-only stage-period
 * history; registered mandatory reasons on reject/withdraw; ONE active
 * application per (candidate, opening) — re-application after a terminal
 * state creates a NEW row. HIRED is conversion-only (§7/T8): this service
 * deliberately exposes NO path that reaches HIRED.</p>
 */
@Service
public class HrApplicationService {

    /** Deterministic next-stage map (§6.2); OFFER has NO next stage here. */
    private static final Map<HrApplicationState, HrApplicationState> NEXT_STAGE = Map.of(
            HrApplicationState.APPLIED, HrApplicationState.SCREENING,
            HrApplicationState.SCREENING, HrApplicationState.INTERVIEW,
            HrApplicationState.INTERVIEW, HrApplicationState.OFFER);

    private final JdbcHrApplicationRepository repository;
    private final RecruitmentAuthorizationPort authorization;

    @Autowired
    public HrApplicationService(JdbcHrApplicationRepository repository,
                                RecruitmentAuthorizationPort authorization) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public UUID apply(HrCommandContext ctx, UUID candidateId, UUID jobOpeningId) {
        authorization.requireApplicationManage(ctx, null);
        HrApplication application = new HrApplication(UUID.randomUUID(), ctx.tenantId(),
                candidateId, jobOpeningId, HrApplicationState.APPLIED, 0L);
        HrApplication inserted = repository.insert(application, ctx.actorUserId(), ctx.correlationId());
        return inserted.id();
    }

    public void advance(HrCommandContext ctx, UUID applicationId) {
        authorization.requireApplicationAdvance(ctx, applicationId);
        HrApplication application = load(ctx, applicationId);
        HrApplicationState next = NEXT_STAGE.get(application.state());
        if (next == null) {
            if (application.state() == HrApplicationState.OFFER) {
                // §6.2/T8: OFFER→HIRED happens ONLY inside the hire conversion.
                throw new IllegalStateException("HRM_APPLICATION_CONVERSION_ONLY: "
                        + "the HIRED transition is reserved for the hire conversion (§7)");
            }
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + application.state()
                    + " is terminal or has no forward stage");
        }
        checkGuard(ctx, application.state(), next, null);
        repository.transition(ctx.tenantId(), applicationId, application.state(), next, true,
                JdbcHrApplicationRepository.ACTION_ADVANCED, ctx.actorUserId(), ctx.correlationId());
    }

    public void reject(HrCommandContext ctx, UUID applicationId, String reasonCode) {
        authorization.requireApplicationReject(ctx, applicationId);
        HrApplication application = load(ctx, applicationId);
        checkGuard(ctx, application.state(), HrApplicationState.REJECTED, reasonCode);
        repository.transition(ctx.tenantId(), applicationId, application.state(),
                HrApplicationState.REJECTED, false,
                JdbcHrApplicationRepository.ACTION_REJECTED, ctx.actorUserId(), ctx.correlationId());
    }

    public void withdraw(HrCommandContext ctx, UUID applicationId, String reasonCode) {
        authorization.requireApplicationManage(ctx, applicationId);
        HrApplication application = load(ctx, applicationId);
        checkGuard(ctx, application.state(), HrApplicationState.WITHDRAWN, reasonCode);
        repository.transition(ctx.tenantId(), applicationId, application.state(),
                HrApplicationState.WITHDRAWN, false,
                JdbcHrApplicationRepository.ACTION_WITHDRAWN, ctx.actorUserId(), ctx.correlationId());
    }

    /** Deterministic pipeline history (§13.1 row 12). */
    public List<HrApplication.StagePeriod> stageHistory(HrCommandContext ctx, UUID applicationId) {
        authorization.requireApplicationManage(ctx, applicationId);
        load(ctx, applicationId);
        return repository.stagePeriods(ctx.tenantId(), applicationId);
    }

    // --- internals ---

    private HrApplication load(HrCommandContext ctx, UUID applicationId) {
        return repository.find(ctx.tenantId(), applicationId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_APPLICATION_NOT_FOUND: " + applicationId + " is not visible to this tenant context"));
    }

    private void checkGuard(HrCommandContext ctx, HrApplicationState from, HrApplicationState to,
                            String reasonCode) {
        HrTransitionDecision decision = HrApplicationTransitions.check(
                new HrTransitionContext(ctx.tenantId(), String.valueOf(ctx.actorUserId()), reasonCode), from, to);
        if (!decision.allowed()) {
            throw new IllegalStateException("HRM_TRANSITION_FORBIDDEN: " + from + " → " + to
                    + (decision.violationCode() == null ? "" : " (" + decision.violationCode() + ")"));
        }
    }
}
