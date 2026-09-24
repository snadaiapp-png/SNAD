package com.sanad.platform.hr.time.application;

import com.sanad.platform.hr.time.TimeAttendanceCapabilities;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Explicit relationship/data-scope read contracts for G2.
 *
 * <p>These routes intentionally separate TEAM and HR administration reads from
 * SELF routes on {@link HrTimeAttendanceV2Controller}. Capability checks gate
 * the route; {@link HrG2ScopedReadService} narrows TEAM data to direct reports.
 * HR reads remain tenant-scoped and PostgreSQL RLS remains authoritative.</p>
 */
@RestController
@RequestMapping("/api/v2/hr")
@Tag(name = "HRM-G2 Scoped Reads")
public class HrG2ScopedReadController {

    private final HrG2ScopedReadService scopedReadService;

    public HrG2ScopedReadController(HrG2ScopedReadService scopedReadService) {
        this.scopedReadService = scopedReadService;
    }

    @GetMapping("/time/attendance/team")
    @Operation(operationId = "hrTeamAttendanceList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse>> listTeamAttendance(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(scopedReadService.listTeamAttendance(tenantId, userId, startDate, endDate));
    }

    @GetMapping("/time/attendance/admin")
    @Operation(operationId = "hrAdminAttendanceList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_ADMIN)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse>> listAdminAttendance(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(scopedReadService.listAdminAttendance(tenantId, employmentId, startDate, endDate));
    }

    @GetMapping("/time/timesheets/team")
    @Operation(operationId = "hrTeamTimesheetsList")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_TEAM_APPROVE)
    public ResponseEntity<List<HrTimesheetService.TimesheetResponse>> listTeamTimesheets(
            Authentication authentication,
            @RequestParam(required = false) String state) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(scopedReadService.listTeamTimesheets(tenantId, userId, state));
    }

    @GetMapping("/leave/requests/team")
    @Operation(operationId = "hrTeamLeaveRequestsList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse>> listTeamLeaveRequests(
            Authentication authentication,
            @RequestParam(required = false) String state) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(scopedReadService.listTeamLeaveRequests(tenantId, userId, state));
    }

    @GetMapping("/leave/requests/hr")
    @Operation(operationId = "hrHrLeaveRequestsList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_HR_APPROVE)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse>> listHrLeaveRequests(
            Authentication authentication,
            @RequestParam(required = false) String state) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(scopedReadService.listHrLeaveRequests(tenantId, state));
    }

    @GetMapping("/leave/types/admin")
    @Operation(operationId = "hrAdminLeaveTypesList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_POLICY_ADMIN)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.HrLeaveTypeResponse>> listAdminLeaveTypes(
            Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(scopedReadService.listAdminLeaveTypes(tenantId));
    }

    @GetMapping("/time/attendance/monthly-report/team")
    @Operation(operationId = "hrTeamAttendanceMonthlyReport")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow>> teamMonthlyAttendanceReport(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(scopedReadService.teamMonthlyAttendanceReport(tenantId, userId, year, month));
    }

    @GetMapping("/time/attendance/monthly-report/admin")
    @Operation(operationId = "hrAdminAttendanceMonthlyReport")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_ADMIN)
    public ResponseEntity<List<HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow>> adminMonthlyAttendanceReport(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) UUID employmentId) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(scopedReadService.adminMonthlyAttendanceReport(
                tenantId, year, month, employmentId));
    }
}
