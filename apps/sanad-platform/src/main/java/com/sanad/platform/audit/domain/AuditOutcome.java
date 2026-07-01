package com.sanad.platform.audit.domain;

/**
 * Stage 05 §4 — The outcome of an audited action.
 *
 * <p>Every audit event records whether the action succeeded, failed,
 * was denied, or completed partially. This drives audit queries and
 * integrity dashboards.</p>
 */
public enum AuditOutcome {

    /** The action completed successfully. */
    SUCCESS,

    /** The action failed (business rule violation, unexpected error). */
    FAILURE,

    /** The action was denied (authentication or authorization failure). */
    DENIED,

    /** The action completed partially (some steps succeeded, some failed). */
    PARTIAL
}
