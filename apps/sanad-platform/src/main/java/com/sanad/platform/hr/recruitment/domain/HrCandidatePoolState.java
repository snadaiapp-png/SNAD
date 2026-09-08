package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — HrCandidate pool states (design §5.1).
 *
 * <p>Recruitment-scoped candidate identity, NOT hr_people. History retained;
 * pool movement is state-based archive only (no physical delete).</p>
 */
public enum HrCandidatePoolState {
    ACTIVE,
    HIRED,
    WITHDRAWN,
    ARCHIVED
}
