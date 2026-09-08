package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — HrJobOpening lifecycle states (design §5.1/§6.1).
 *
 * <p>Uppercase domain states per G0 convention; persisted with effective-dated
 * period rows and attributed transitions (implemented in G1-T2/T3 — this
 * contract is persistence-free per G1-T1).</p>
 */
public enum HrJobOpeningState {
    DRAFT,
    PENDING_APPROVAL,
    OPEN,
    PAUSED,
    CLOSED,
    CANCELLED
}
