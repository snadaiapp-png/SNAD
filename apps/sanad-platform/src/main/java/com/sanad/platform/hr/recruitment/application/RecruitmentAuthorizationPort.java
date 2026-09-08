package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;

import java.util.UUID;

/**
 * HRM-G1 — recruitment authorization port (design §9).
 *
 * <p>All G1 capability checks flow through the G0 scoped authorization
 * machinery; no new role engine and no permission caching outside the G0
 * policy. The implementation binds the canonical
 * {@code HRM.RECRUITMENT.OPENING.*} capabilities to
 * {@code ScopedAuthorizationService} and FAILS CLOSED: any denial raises
 * {@code HRM_SCOPE_DENIED} before the command touches state.</p>
 */
public interface RecruitmentAuthorizationPort {

    /** OPENING.VIEW — read paths. */
    void requireOpeningView(HrCommandContext ctx, UUID openingId);

    /** OPENING.MANAGE — create/submit/edit/pause/resume/close/cancel. */
    void requireOpeningManage(HrCommandContext ctx, UUID openingId);

    /** OPENING.PUBLISH — approve (PENDING_APPROVAL → OPEN). */
    void requireOpeningPublish(HrCommandContext ctx, UUID openingId);

    /** CANDIDATE.VIEW — candidate reads (masked contact). */
    void requireCandidateView(HrCommandContext ctx, UUID candidateId);

    /** CANDIDATE.MANAGE — candidate create/update/archive + full contact reads. */
    void requireCandidateManage(HrCommandContext ctx, UUID candidateId);

    /** APPLICATION.MANAGE — apply (operator path) + withdraw. */
    void requireApplicationManage(HrCommandContext ctx, UUID applicationId);

    /** APPLICATION.ADVANCE — forward stage movement. */
    void requireApplicationAdvance(HrCommandContext ctx, UUID applicationId);

    /** APPLICATION.REJECT — terminal rejection with registered reason. */
    void requireApplicationReject(HrCommandContext ctx, UUID applicationId);

    /** INTERVIEW.SCHEDULE — interview scheduling with panel. */
    void requireInterviewSchedule(HrCommandContext ctx, UUID interviewId);

    /** INTERVIEW.RECORD_OUTCOME — outcome recording (terminal). */
    void requireInterviewRecordOutcome(HrCommandContext ctx, UUID interviewId);

    /** INTERVIEW.MANAGE — detail/feedback administration. */
    void requireInterviewManage(HrCommandContext ctx, UUID interviewId);
}
