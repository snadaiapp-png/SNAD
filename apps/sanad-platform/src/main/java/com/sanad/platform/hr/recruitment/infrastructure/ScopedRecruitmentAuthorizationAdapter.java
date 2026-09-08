package com.sanad.platform.hr.recruitment.infrastructure;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.application.RecruitmentAuthorizationPort;
import com.sanad.platform.hr.security.HrAuthorizationResourceContext;
import com.sanad.platform.security.scope.ScopedAuthorizationRequest;
import com.sanad.platform.security.scope.ScopedAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Scoped-authorization adapter for the G1 recruitment family (design §9).
 *
 * <p>Binds the canonical {@code HRM.RECRUITMENT.OPENING.*} capabilities to
 * the G0 scoped authorization service. Deny-by-default: any missing or
 * non-ALLOW decision raises {@code HRM_SCOPE_DENIED} before the command
 * touches state.</p>
 */
@Component
public class ScopedRecruitmentAuthorizationAdapter implements RecruitmentAuthorizationPort {

    public static final String CAPABILITY_OPENING_VIEW = "HRM.RECRUITMENT.OPENING.VIEW";
    public static final String CAPABILITY_OPENING_MANAGE = "HRM.RECRUITMENT.OPENING.MANAGE";
    public static final String CAPABILITY_OPENING_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";
    public static final String CAPABILITY_CANDIDATE_VIEW = "HRM.RECRUITMENT.CANDIDATE.VIEW";
    public static final String CAPABILITY_CANDIDATE_MANAGE = "HRM.RECRUITMENT.CANDIDATE.MANAGE";
    public static final String CAPABILITY_APPLICATION_MANAGE = "HRM.RECRUITMENT.APPLICATION.MANAGE";
    public static final String CAPABILITY_APPLICATION_ADVANCE = "HRM.RECRUITMENT.APPLICATION.ADVANCE";
    public static final String CAPABILITY_APPLICATION_REJECT = "HRM.RECRUITMENT.APPLICATION.REJECT";
    public static final String CAPABILITY_INTERVIEW_MANAGE = "HRM.RECRUITMENT.INTERVIEW.MANAGE";
    public static final String CAPABILITY_INTERVIEW_SCHEDULE = "HRM.RECRUITMENT.INTERVIEW.SCHEDULE";
    public static final String CAPABILITY_INTERVIEW_OUTCOME = "HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME";

    private final ScopedAuthorizationService scopedAuthorizationService;

    @Autowired
    public ScopedRecruitmentAuthorizationAdapter(ScopedAuthorizationService scopedAuthorizationService) {
        this.scopedAuthorizationService = Objects.requireNonNull(scopedAuthorizationService,
                "scopedAuthorizationService");
    }

    @Override
    public void requireOpeningView(HrCommandContext ctx, UUID openingId) {
        require(ctx, openingId, CAPABILITY_OPENING_VIEW);
    }

    @Override
    public void requireOpeningManage(HrCommandContext ctx, UUID openingId) {
        require(ctx, openingId, CAPABILITY_OPENING_MANAGE);
    }

    @Override
    public void requireOpeningPublish(HrCommandContext ctx, UUID openingId) {
        require(ctx, openingId, CAPABILITY_OPENING_PUBLISH);
    }

    @Override
    public void requireCandidateView(HrCommandContext ctx, UUID candidateId) {
        require(ctx, candidateId, CAPABILITY_CANDIDATE_VIEW);
    }

    @Override
    public void requireCandidateManage(HrCommandContext ctx, UUID candidateId) {
        require(ctx, candidateId, CAPABILITY_CANDIDATE_MANAGE);
    }

    @Override
    public void requireApplicationManage(HrCommandContext ctx, UUID applicationId) {
        require(ctx, applicationId, CAPABILITY_APPLICATION_MANAGE);
    }

    @Override
    public void requireApplicationAdvance(HrCommandContext ctx, UUID applicationId) {
        require(ctx, applicationId, CAPABILITY_APPLICATION_ADVANCE);
    }

    @Override
    public void requireApplicationReject(HrCommandContext ctx, UUID applicationId) {
        require(ctx, applicationId, CAPABILITY_APPLICATION_REJECT);
    }

    @Override
    public void requireInterviewSchedule(HrCommandContext ctx, UUID interviewId) {
        require(ctx, interviewId, CAPABILITY_INTERVIEW_SCHEDULE);
    }

    @Override
    public void requireInterviewRecordOutcome(HrCommandContext ctx, UUID interviewId) {
        require(ctx, interviewId, CAPABILITY_INTERVIEW_OUTCOME);
    }

    @Override
    public void requireInterviewManage(HrCommandContext ctx, UUID interviewId) {
        require(ctx, interviewId, CAPABILITY_INTERVIEW_MANAGE);
    }

    private void require(HrCommandContext ctx, UUID openingId, String capability) {
        HrAuthorizationResourceContext resource = new HrAuthorizationResourceContext(
                ctx.tenantId(), JdbcHrJobOpeningRepository.RESOURCE_TYPE, openingId, null, null,
                null, null, null, null, "RECRUITMENT", null);
        ScopedAuthorizationRequest request = new ScopedAuthorizationRequest(
                ctx.tenantId(), ctx.actorUserId(), capability, resource, Instant.now());
        var decision = scopedAuthorizationService.authorize(request);
        if (decision == null || !decision.allowed()) {
            throw new IllegalStateException("HRM_SCOPE_DENIED: " + capability
                    + " denied for the requested recruitment scope");
        }
    }
}
