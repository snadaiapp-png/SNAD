package com.sanad.platform.hr.recruitment.domain;

import java.util.Set;

/**
 * HRM-G1 — recruitment reason-code registry (fail-closed validation).
 *
 * <p>Transitions whose matrix entry requires a reason MUST carry a code from
 * this registry; unknown codes are rejected before any state change
 * (G1-T1 task card: "unknown reason codes fail").</p>
 */
public final class RecruitmentReasonCodes {

    /** PENDING_APPROVAL → DRAFT (opening rejected by approver). */
    public static final String OPENING_REJECTION = "OPENING_REJECTION";
    /** Application rejected during any pre-HIRED stage. */
    public static final String APPLICATION_REJECTION = "APPLICATION_REJECTION";
    /** Application withdrawn (candidate- or recruiter-attributed). */
    public static final String APPLICATION_WITHDRAWAL = "APPLICATION_WITHDRAWAL";
    /** Extended offer withdrawn before acceptance. */
    public static final String OFFER_WITHDRAWAL = "OFFER_WITHDRAWAL";

    private static final Set<String> REGISTRY = Set.of(
            OPENING_REJECTION,
            APPLICATION_REJECTION,
            APPLICATION_WITHDRAWAL,
            OFFER_WITHDRAWAL);

    private RecruitmentReasonCodes() {
    }

    public static Set<String> all() {
        return REGISTRY;
    }

    public static boolean isRegistered(String code) {
        return code != null && !code.isBlank() && REGISTRY.contains(code);
    }
}
