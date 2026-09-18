package com.sanad.platform.hr.recruitment;

import java.util.Set;

/**
 * HRM-G1 — capability constants for the recruitment family (design §9).
 *
 * <p>Canonical convention: {@code HRM.<DOMAIN>.<ACTION>} — consumed only
 * through the existing scoped authorization machinery; no new role engine,
 * no permission caching outside the G0 policy. This set is pinned exactly to
 * the §9 matrix by {@code RecruitmentCapabilityNamingContractTest}.</p>
 */
public final class RecruitmentCapabilities {

    public static final String OPENING_VIEW = "HRM.RECRUITMENT.OPENING.VIEW";
    public static final String OPENING_MANAGE = "HRM.RECRUITMENT.OPENING.MANAGE";
    public static final String OPENING_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";
    public static final String CANDIDATE_VIEW = "HRM.RECRUITMENT.CANDIDATE.VIEW";
    public static final String CANDIDATE_MANAGE = "HRM.RECRUITMENT.CANDIDATE.MANAGE";
    public static final String APPLICATION_MANAGE = "HRM.RECRUITMENT.APPLICATION.MANAGE";
    public static final String APPLICATION_ADVANCE = "HRM.RECRUITMENT.APPLICATION.ADVANCE";
    public static final String APPLICATION_REJECT = "HRM.RECRUITMENT.APPLICATION.REJECT";
    public static final String INTERVIEW_MANAGE = "HRM.RECRUITMENT.INTERVIEW.MANAGE";
    public static final String INTERVIEW_SCHEDULE = "HRM.RECRUITMENT.INTERVIEW.SCHEDULE";
    public static final String INTERVIEW_RECORD_OUTCOME = "HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME";
    public static final String OFFER_MANAGE = "HRM.RECRUITMENT.OFFER.MANAGE";
    public static final String OFFER_EXTEND = "HRM.RECRUITMENT.OFFER.EXTEND";
    public static final String OFFER_APPROVE = "HRM.RECRUITMENT.OFFER.APPROVE";
    public static final String HIRE_CONVERT = "HRM.RECRUITMENT.HIRE.CONVERT";

    private static final Set<String> ALL = Set.of(
            OPENING_VIEW, OPENING_MANAGE, OPENING_PUBLISH,
            CANDIDATE_VIEW, CANDIDATE_MANAGE,
            APPLICATION_MANAGE, APPLICATION_ADVANCE, APPLICATION_REJECT,
            INTERVIEW_MANAGE, INTERVIEW_SCHEDULE, INTERVIEW_RECORD_OUTCOME,
            OFFER_MANAGE, OFFER_EXTEND, OFFER_APPROVE,
            HIRE_CONVERT);

    private RecruitmentCapabilities() {
    }

    public static Set<String> all() {
        return ALL;
    }
}
