package com.sanad.platform.hr.onboarding.domain;

import java.util.Set;

/**
 * HRM-G1 — onboarding reason-code registry (fail-closed validation).
 */
public final class OnboardingReasonCodes {

    /** Task waived with capability HRM.ONBOARDING.TASK.WAIVE (§6.4). */
    public static final String TASK_WAIVER = "TASK_WAIVER";
    /** Plan cancelled (cascades cancel of OPEN tasks). */
    public static final String PLAN_CANCELLATION = "PLAN_CANCELLATION";

    private static final Set<String> REGISTRY = Set.of(
            TASK_WAIVER,
            PLAN_CANCELLATION);

    private OnboardingReasonCodes() {
    }

    public static Set<String> all() {
        return REGISTRY;
    }

    public static boolean isRegistered(String code) {
        return code != null && !code.isBlank() && REGISTRY.contains(code);
    }
}
