package com.sanad.platform.crm.cases.domain;

/**
 * Case lifecycle status constants.
 * <p>
 * Lifecycle: OPEN → IN_PROGRESS → RESOLVED → CLOSED
 * A CLOSED case may be REOPENED back to IN_PROGRESS.
 */
public final class CaseStatus {
    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private CaseStatus() {}
}
