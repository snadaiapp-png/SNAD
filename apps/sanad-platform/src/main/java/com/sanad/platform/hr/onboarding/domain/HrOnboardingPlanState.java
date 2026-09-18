package com.sanad.platform.hr.onboarding.domain;

/**
 * HRM-G1 — HrOnboardingPlan lifecycle states (design §5.1/§6.4).
 *
 * <p>COMPLETED is DERIVED (no OPEN tasks remain) — never set manually; no
 * percent fields exist.</p>
 */
public enum HrOnboardingPlanState {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
