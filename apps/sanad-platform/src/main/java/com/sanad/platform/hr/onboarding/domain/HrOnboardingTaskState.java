package com.sanad.platform.hr.onboarding.domain;

/**
 * HRM-G1 — HrOnboardingTask states (design §6.4).
 *
 * <p>DONE/WAIVED are terminal; corrections happen via new audit-annotated
 * note tasks — terminal states never reopen.</p>
 */
public enum HrOnboardingTaskState {
    OPEN,
    DONE,
    WAIVED
}
