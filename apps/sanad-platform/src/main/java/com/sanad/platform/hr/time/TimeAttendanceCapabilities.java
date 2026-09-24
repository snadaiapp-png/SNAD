package com.sanad.platform.hr.time;

/**
 * HRM-G2 capability constants for the time/attendance/leave family.
 *
 * <p>Capabilities are scope-separated per G2 corrective directive §15:
 * <ul>
 *   <li>SELF: employee's own attendance/leave (clock-in/out, submit leave, view own records)</li>
 *   <li>TEAM/MANAGER: direct reports' timesheet/leave approval</li>
 *   <li>HR: tenant-wide administration (corrections, policy management, HR approval)</li>
 * </ul>
 *
 * <p>Capabilities are seeded by {@code V20260923_3__hr_g2_seed_leave_types_and_capabilities.sql}.
 */
public final class TimeAttendanceCapabilities {

    // === Attendance — SELF scope ===
    public static final String ATTENDANCE_SELF_RECORD = "HRM.ATTENDANCE.SELF_RECORD";
    public static final String ATTENDANCE_SELF_VIEW = "HRM.ATTENDANCE.SELF_VIEW";

    // === Attendance — TEAM/MANAGER scope ===
    public static final String ATTENDANCE_TEAM_VIEW = "HRM.ATTENDANCE.TEAM_VIEW";

    // === Attendance — HR scope ===
    public static final String ATTENDANCE_ADMIN = "HRM.ATTENDANCE.ADMIN";
    public static final String ATTENDANCE_CORRECT = "HRM.ATTENDANCE.CORRECT";

    // === Leave — SELF scope ===
    public static final String LEAVE_SELF_REQUEST = "HRM.LEAVE.SELF_REQUEST";
    public static final String LEAVE_SELF_VIEW = "HRM.LEAVE.SELF_VIEW";

    // === Leave — TEAM/MANAGER scope ===
    public static final String LEAVE_TEAM_APPROVE = "HRM.LEAVE.TEAM_APPROVE";

    // === Leave — HR scope ===
    public static final String LEAVE_HR_APPROVE = "HRM.LEAVE.HR_APPROVE";
    public static final String LEAVE_POLICY_ADMIN = "HRM.LEAVE.POLICY_ADMIN";

    // === Timesheet — SELF scope ===
    public static final String TIMESHEET_SELF_VIEW = "HRM.TIMESHEET.SELF_VIEW";
    public static final String TIMESHEET_SELF_SUBMIT = "HRM.TIMESHEET.SELF_SUBMIT";

    // === Timesheet — TEAM/MANAGER scope ===
    public static final String TIMESHEET_TEAM_APPROVE = "HRM.TIMESHEET.TEAM_APPROVE";

    private TimeAttendanceCapabilities() {}
}
