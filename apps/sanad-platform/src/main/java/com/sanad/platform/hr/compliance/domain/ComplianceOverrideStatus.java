package com.sanad.platform.hr.compliance.domain;

/**
 * Governed override request state machine (WS3 Task 4).
 *
 * <pre>
 * PENDING_APPROVAL -> APPROVED | REJECTED
 * APPROVED         -> EXECUTED | REVOKED | EXPIRED
 * </pre>
 * All transitions are tenant-bound, race-safe (conditional UPDATE) and
 * audited. Illegal transitions (e.g. REJECTED -> APPROVED, REVOKED ->
 * EXECUTED, EXPIRED -> EXECUTED, EXECUTED -> APPROVED) are rejected with
 * deterministic domain outcomes.
 */
public enum ComplianceOverrideStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXECUTED,
    REVOKED,
    EXPIRED
}
