package com.sanad.platform.hr.onboarding.domain;

import java.util.Set;

/**
 * HRM-G1 — capability constants for the onboarding family (design §9).
 *
 * <p>Pinned exactly to the §9 matrix by
 * {@code OnboardingCapabilityNamingContractTest}. COMPLETE ≤ WAIVE privilege
 * ordering is a grant-level concern in the G0 binding tables.</p>
 */
public final class OnboardingCapabilities {

    public static final String PLAN_MANAGE = "HRM.ONBOARDING.PLAN.MANAGE";
    public static final String TASK_COMPLETE = "HRM.ONBOARDING.TASK.COMPLETE";
    public static final String TASK_WAIVE = "HRM.ONBOARDING.TASK.WAIVE";

    private static final Set<String> ALL = Set.of(PLAN_MANAGE, TASK_COMPLETE, TASK_WAIVE);

    private OnboardingCapabilities() {
    }

    public static Set<String> all() {
        return ALL;
    }
}
