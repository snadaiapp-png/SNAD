package com.sanad.platform.hr.time.application;

import com.sanad.platform.hr.time.TimeAttendanceCapabilities;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 V2 API — Time & Attendance + Leave management.
 *
 * <p>Endpoints mounted under {@code /api/v2/hr/time} and {@code /api/v2/hr/leave}.
 * Backed by {@link HrTimeAttendanceService}. Tenant-scoped; every operation
 * requires the appropriate {@code HRM.ATTENDANCE.*} or {@code HRM.LEAVE.*}
 * capability.
 *
 * <p>RLS is enforced at the database level (V20260923_2). Cross-tenant
 * access returns empty set (read) or 42501 (write).
 */
@RestController
@RequestMapping("/api/v2/hr")
@Tag(name = "HRM-G2 Time & Attendance + Leave")
public class HrTimeAttendanceV2Controller {

    private final HrTimeAttendanceService timeService;
    private final HrLeaveService leaveService;
    private final HrScheduleService scheduleService;
    private final HrTimesheetService timesheetService;

    public HrTimeAttendanceV2Controller(
            HrTimeAttendanceService timeService,
            HrLeaveService leaveService,
            HrScheduleService scheduleService,
            HrTimesheetService timesheetService
    ) {
        this.timeService = timeService;
        this.leaveService = leaveService;
        this.scheduleService = scheduleService;
        this.timesheetService = timesheetService;
    }

    // ==================== Schedules ====================

    @GetMapping("/time/schedules")
    @Operation(operationId = "hrSchedulesList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW)
    public ResponseEntity<List<HrScheduleService.ScheduleResponse>> listSchedules(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(scheduleService.listSchedules(tenantId));
    }

    @PostMapping("/time/schedules")
    @Operation(operationId = "hrScheduleCreate")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_ADMIN)
    public ResponseEntity<CreateIdResponse> createSchedule(
            Authentication authentication,
            @Valid @RequestBody HrScheduleService.CreateScheduleRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(new CreateIdResponse(scheduleService.createSchedule(tenantId, request)));
    }

    @PostMapping("/time/schedules/assign")
    @Operation(operationId = "hrScheduleAssign")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_ADMIN)
    public ResponseEntity<CreateIdResponse> assignSchedule(
            Authentication authentication,
            @Valid @RequestBody HrScheduleService.AssignScheduleRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(new CreateIdResponse(scheduleService.assignSchedule(tenantId, request)));
    }

    // ==================== Timesheets ====================

    @GetMapping("/time/timesheets")
    @Operation(operationId = "hrTimesheetsList")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_SELF_VIEW)
    public ResponseEntity<List<HrTimesheetService.TimesheetResponse>> listTimesheets(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) String state
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(timesheetService.listTimesheets(tenantId, employmentId, state));
    }

    @PostMapping("/time/timesheets")
    @Operation(operationId = "hrTimesheetCreate")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_SELF_SUBMIT)
    public ResponseEntity<CreateIdResponse> createTimesheet(
            Authentication authentication,
            @Valid @RequestBody CreateTimesheetRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(new CreateIdResponse(
                timesheetService.createTimesheet(tenantId, request.employmentId(),
                        request.periodStart(), request.periodEnd())
        ));
    }

    @PostMapping("/time/timesheets/{timesheetId}/submit")
    @Operation(operationId = "hrTimesheetSubmit")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_SELF_SUBMIT)
    public ResponseEntity<Void> submitTimesheet(
            Authentication authentication,
            @PathVariable UUID timesheetId,
            @RequestParam UUID employmentId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        timesheetService.submit(tenantId, timesheetId, employmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/time/timesheets/{timesheetId}/approve")
    @Operation(operationId = "hrTimesheetApprove")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_TEAM_APPROVE)
    public ResponseEntity<Void> approveTimesheet(
            Authentication authentication,
            @PathVariable UUID timesheetId,
            @Valid @RequestBody ApproveRejectRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        timesheetService.approve(tenantId, timesheetId, userId, request.comment());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/time/timesheets/{timesheetId}/reject")
    @Operation(operationId = "hrTimesheetReject")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_TEAM_APPROVE)
    public ResponseEntity<Void> rejectTimesheet(
            Authentication authentication,
            @PathVariable UUID timesheetId,
            @Valid @RequestBody ApproveRejectRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        timesheetService.reject(tenantId, timesheetId, userId, request.reason());
        return ResponseEntity.noContent().build();
    }

    // ==================== Attendance ====================

    @GetMapping("/time/attendance")
    @Operation(operationId = "hrAttendanceList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_SELF_VIEW)
    public ResponseEntity<List<HrAttendanceRecordResponse>> listAttendance(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(timeService.listAttendance(tenantId, employmentId, startDate, endDate));
    }

    @PostMapping("/time/attendance/clock-in")
    @Operation(operationId = "hrAttendanceClockIn")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_SELF_RECORD)
    public ResponseEntity<HrAttendanceRecordResponse> clockIn(
            Authentication authentication,
            @Valid @RequestBody ClockInRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(timeService.clockIn(tenantId, userId, request));
    }

    @PostMapping("/time/attendance/{recordId}/clock-out")
    @Operation(operationId = "hrAttendanceClockOut")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_SELF_RECORD)
    public ResponseEntity<HrAttendanceRecordResponse> clockOut(
            Authentication authentication,
            @PathVariable UUID recordId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(timeService.clockOut(tenantId, userId, recordId));
    }

    // ==================== Leave Types ====================

    @GetMapping("/leave/types")
    @Operation(operationId = "hrLeaveTypesList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_VIEW)
    public ResponseEntity<List<HrLeaveTypeResponse>> listLeaveTypes(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(leaveService.listLeaveTypes(tenantId));
    }

    // ==================== Leave Requests ====================

    @GetMapping("/leave/requests")
    @Operation(operationId = "hrLeaveRequestsList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_VIEW)
    public ResponseEntity<List<HrLeaveRequestResponse>> listLeaveRequests(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) String state
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(leaveService.listLeaveRequests(tenantId, employmentId, state));
    }

    @PostMapping("/leave/requests")
    @Operation(operationId = "hrLeaveRequestCreate")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_REQUEST)
    public ResponseEntity<CreateLeaveRequestResponse> createLeaveRequest(
            Authentication authentication,
            @Valid @RequestBody CreateLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(leaveService.createLeaveRequest(tenantId, userId, request));
    }

    @PostMapping("/leave/requests/{requestId}/approve")
    @Operation(operationId = "hrLeaveRequestApprove")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE)
    public ResponseEntity<HrLeaveRequestResponse> approveLeaveRequest(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestBody ApproveLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(leaveService.approveLeaveRequest(tenantId, requestId, userId, request));
    }

    @PostMapping("/leave/requests/{requestId}/reject")
    @Operation(operationId = "hrLeaveRequestReject")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE)
    public ResponseEntity<HrLeaveRequestResponse> rejectLeaveRequest(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestBody RejectLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        return ResponseEntity.ok(leaveService.rejectLeaveRequest(tenantId, requestId, userId, request));
    }

    // ==================== Leave Balances ====================

    @GetMapping("/leave/balances")
    @Operation(operationId = "hrLeaveBalancesList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_VIEW)
    public ResponseEntity<List<HrLeaveBalanceResponse>> listLeaveBalances(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) Integer year
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.listLeaveBalances(tenantId, employmentId, y));
    }

    // ==================== G2-T05: Monthly Attendance Report ====================

    @GetMapping("/time/attendance/monthly-report")
    @Operation(operationId = "hrAttendanceMonthlyReport")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW)
    public ResponseEntity<List<MonthlyAttendanceReportRow>> monthlyAttendanceReport(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) UUID employmentId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(timeService.monthlyAttendanceReport(tenantId, year, month, employmentId));
    }

    // ==================== DTOs (continued) ====================

    public record ClockInRequest(UUID employmentId, LocalDate recordDate) {}
    public record CreateLeaveRequest(UUID employmentId, UUID leaveTypeId, LocalDate startDate, LocalDate endDate, String reason, String attachmentUrl) {}
    public record ApproveLeaveRequest(String comment) {}
    public record RejectLeaveRequest(String reason) {}

    public record CreateLeaveRequestResponse(UUID requestId) {}

    public record HrAttendanceRecordResponse(
            UUID id, UUID employmentId, LocalDate recordDate,
            java.time.Instant clockIn, java.time.Instant clockOut,
            Integer breakMinutes, Integer workedMinutes, String source, String state
    ) {}

    public record HrLeaveTypeResponse(
            UUID id, String code, String nameAr, String nameEn,
            Boolean isPaid, Boolean requiresAttachment, Integer defaultDaysPerYear, String state
    ) {}

    public record HrLeaveRequestResponse(
            UUID id, UUID employmentId, UUID leaveTypeId,
            LocalDate startDate, LocalDate endDate, java.math.BigDecimal daysCount,
            String reason, String state, java.time.Instant submittedAt,
            java.time.Instant approvedAt, String approverComment
    ) {}

    public record HrLeaveBalanceResponse(
            UUID id, UUID employmentId, UUID leaveTypeId, Integer year,
            java.math.BigDecimal entitledDays, java.math.BigDecimal usedDays,
            java.math.BigDecimal pendingDays, java.math.BigDecimal carriedOverDays
    ) {}

    /** G2-T05: Monthly attendance report row per employee. */
    public record MonthlyAttendanceReportRow(
            UUID employmentId,
            Integer scheduledDays,
            Integer scheduledMinutes,
            Integer workedMinutes,
            Integer absentDays,
            Integer leaveDays,
            Integer lateOccurrences,
            Integer earlyDepartures,
            Integer missingPunches,
            String attendanceStatus
    ) {}

    public record CreateIdResponse(UUID id) {}
    public record CreateTimesheetRequest(UUID employmentId, LocalDate periodStart, LocalDate periodEnd) {}
    public record ApproveRejectRequest(String comment, String reason) {
        public String comment() { return comment; }
        public String reason() { return reason; }
    }
}
