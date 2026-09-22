package com.sanad.platform.hr.time;

/**
 * HRM-G2 capability constants for the time/attendance/leave family.
 *
 * <p>Canonical convention: {@code HRM.<DOMAIN>.<ACTION>} — consumed only
 * through the existing scoped authorization machinery; no new role engine.
 *
 * <p>Capabilities are seeded by {@code V20260923_3__hr_g2_seed_leave_types_and_capabilities.sql}.
 */
public final class TimeAttendanceCapabilities {

    public static final String ATTENDANCE_VIEW = "HRM.ATTENDANCE.VIEW";
    public static final String ATTENDANCE_MANAGE = "HRM.ATTENDANCE.MANAGE";
    public static final String ATTENDANCE_APPROVE = "HRM.ATTENDANCE.APPROVE";

    public static final String LEAVE_VIEW = "HRM.LEAVE.VIEW";
    public static final String LEAVE_REQUEST = "HRM.LEAVE.REQUEST";
    public static final String LEAVE_APPROVE = "HRM.LEAVE.APPROVE";
    public static final String LEAVE_MANAGE = "HRM.LEAVE.MANAGE";

    public static final String TIMESHEET_VIEW = "HRM.TIMESHEET.VIEW";
    public static final String TIMESHEET_SUBMIT = "HRM.TIMESHEET.SUBMIT";
    public static final String TIMESHEET_APPROVE = "HRM.TIMESHEET.APPROVE";

    private TimeAttendanceCapabilities() {}
}
