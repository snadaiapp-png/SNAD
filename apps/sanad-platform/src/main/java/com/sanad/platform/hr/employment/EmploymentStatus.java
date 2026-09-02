package com.sanad.platform.hr.employment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical Employment status per HRM-G0 Task 2 acceptance contract.
 *
 * <p>Classification:
 * <ul>
 *   <li>NON_TERMINAL: DRAFT, PENDING_ONBOARDING, ACTIVE, ON_LEAVE, SUSPENDED</li>
 *   <li>TERMINAL: TERMINATED, VOIDED</li>
 * </ul>
 * </p>
 *
 * <p>Terminal states cannot transition back to non-terminal states.
 * Rehire of a TERMINATED Person creates a NEW Employment (not a reactivation).</p>
 */
public enum EmploymentStatus {
    DRAFT,
    PENDING_ONBOARDING,
    ACTIVE,
    ON_LEAVE,
    SUSPENDED,
    TERMINATED,
    VOIDED;

    public boolean isTerminal() {
        return this == TERMINATED || this == VOIDED;
    }
}
