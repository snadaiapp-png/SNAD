package com.sanad.platform.hr.performance;

/**
 * Canonical HRM-G3 performance capability codes.
 *
 * <p>SELF and TEAM authority stay deliberately separate. These constants do
 * not imply any role grant; in particular HR_MANAGER is never broadened by
 * this API slice.</p>
 */
public final class PerformanceCapabilities {

    public static final String GOAL_SELF_VIEW = "HRM.PERFORMANCE.GOAL.SELF_VIEW";
    public static final String GOAL_SELF_UPDATE = "HRM.PERFORMANCE.GOAL.SELF_UPDATE";
    public static final String GOAL_TEAM_MANAGE = "HRM.PERFORMANCE.GOAL.TEAM_MANAGE";

    public static final String REVIEW_SELF_VIEW = "HRM.PERFORMANCE.REVIEW.SELF_VIEW";
    public static final String REVIEW_SELF_SUBMIT = "HRM.PERFORMANCE.REVIEW.SELF_SUBMIT";
    public static final String REVIEW_TEAM_MANAGE = "HRM.PERFORMANCE.REVIEW.TEAM_MANAGE";

    public static final String ADMIN = "HRM.PERFORMANCE.ADMIN";

    private PerformanceCapabilities() {}
}
