package com.sanad.platform.hr.contract.domain;

/**
 * Canonical employment contract version lifecycle (WS6 Task 2).
 * SUPERSEDED is the terminal state of a version replaced by an amendment.
 */
public enum EmploymentContractStatus {
    DRAFT,
    PENDING_SIGNATURE,
    ACTIVE,
    EXPIRED,
    TERMINATED,
    SUPERSEDED,
    VOIDED
}
