package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — Interview lifecycle states (design §5.1 HrInterview).
 *
 * <p>DONE carries a structured per-participant outcome (PASSED | FAILED) via
 * the versioned scorecard — not a state of its own.</p>
 */
public enum HrInterviewState {
    SCHEDULED,
    DONE,
    CANCELLED,
    NO_SHOW
}
