package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — HrApplication pipeline states (design §5.1/§6.2).
 *
 * <p>Forward-only stage flow; HIRED is reachable ONLY from OFFER and only
 * through the §7 conversion success (enforced by the transition guard).</p>
 */
public enum HrApplicationState {
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN
}
