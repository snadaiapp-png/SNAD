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
 * <p>SELF routes derive employment identity from the authenticated principal;
 * the browser cannot select another employee. TEAM/HR read surfaces live in
 * {@link HrG2ScopedReadController}. PostgreSQL RLS remains authoritative for
 * tenant isolation.</p>
 */
@RestController
@RequestMapping("/api/v2/hr")
@Tag(name = "HRM-G2 Time & Attendance + Leave")
public class HrTimeAttendanceV2Controller {

    private final HrTimeAttendanceService timeService;
    private final HrLeaveService leaveService;
    private final HrScheduleService scheduleService;
    private final HrTimesheetService timesheetService;
    private final HrEmploymentScopeResolver employmentScopeResolver;

    public HrTimeAttendanceV2Controller(
            HrTimeAttendanceService timeService,
            HrLeaveService leaveService,
            HrScheduleService scheduleService,
            HrTimesheetService timesheetService,
            HrEmploymentScopeResolver employmentScopeResolver
    ) {
        this.timeService = timeService;
        this.leaveService = leaveService;
        this.scheduleService = scheduleService;
        this.timesheetService = timesheetService;
        this.employmentScopeResolver = employmentScopeResolver;
    }

    // ==================== Schedules ====================

    @GetMapping("/time/schedules")
    @Operation(operationId = "hrSchedulesList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_ADMIN)
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
            @RequestParam(required = false) String state
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
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
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        return ResponseEntity.ok(new CreateIdResponse(
                timesheetService.createTimesheet(tenantId, employmentId,
                        request.periodStart(), request.periodEnd())
        ));
    }

    @PostMapping("/time/timesheets/{timesheetId}/submit")
    @Operation(operationId = "hrTimesheetSubmit")
    @RequireCapability(TimeAttendanceCapabilities.TIMESHEET_SELF_SUBMIT)
    public ResponseEntity<Void> submitTimesheet(
            Authentication authentication,
            @PathVariable UUID timesheetId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
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
        UUID targetEmploymentId = timesheetService.requireTimesheetEmployment(tenantId, timesheetId);
        employmentScopeResolver.requireManagedEmployment(tenantId, userId, targetEmploymentId);
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
        UUID targetEmploymentId = timesheetService.requireTimesheetEmployment(tenantId, timesheetId);
        employmentScopeResolver.requireManagedEmployment(tenantId, userId, targetEmploymentId);
        timesheetService.reject(tenantId, timesheetId, userId, request.reason());
        return ResponseEntity.noContent().build();
    }

    // ==================== Attendance ====================

    @GetMapping("/time/attendance")
    @Operation(operationId = "hrAttendanceList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_SELF_VIEW)
    public ResponseEntity<List<HrAttendanceRecordResponse>> listAttendance(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
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
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        return ResponseEntity.ok(timeService.clockIn(tenantId, userId, employmentId, request));
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
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        return ResponseEntity.ok(timeService.clockOut(tenantId, userId, employmentId, recordId));
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
            @RequestParam(required = false) String state
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
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
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        return ResponseEntity.ok(leaveService.createLeaveRequest(
                tenantId, userId, employmentId, request));
    }

    @PostMapping("/leave/requests/{requestId}/submit")
    @Operation(operationId = "hrLeaveRequestSubmit")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_REQUEST)
    public ResponseEntity<Void> submitLeaveRequest(
            Authentication authentication,
            @PathVariable UUID requestId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        leaveService.requireOwnedRequest(tenantId, requestId, employmentId);
        leaveService.submitLeaveRequest(tenantId, requestId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/manager-approve")
    @Operation(operationId = "hrLeaveRequestManagerApprove")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE)
    public ResponseEntity<Void> managerApproveLeave(
            Authentication authentication,
            @PathVariable UUID requestId,
            @Valid @RequestBody ApproveLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID targetEmploymentId = leaveService.requireRequestEmployment(tenantId, requestId);
        employmentScopeResolver.requireManagedEmployment(tenantId, userId, targetEmploymentId);
        leaveService.managerApprove(tenantId, requestId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/manager-reject")
    @Operation(operationId = "hrLeaveRequestManagerReject")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE)
    public ResponseEntity<Void> managerRejectLeave(
            Authentication authentication,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID targetEmploymentId = leaveService.requireRequestEmployment(tenantId, requestId);
        employmentScopeResolver.requireManagedEmployment(tenantId, userId, targetEmploymentId);
        leaveService.managerReject(tenantId, requestId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/hr-approve")
    @Operation(operationId = "hrLeaveRequestHrApprove")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_HR_APPROVE)
    public ResponseEntity<Void> hrApproveLeave(
            Authentication authentication,
            @PathVariable UUID requestId,
            @Valid @RequestBody ApproveLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        leaveService.hrApprove(tenantId, requestId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/hr-reject")
    @Operation(operationId = "hrLeaveRequestHrReject")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_HR_APPROVE)
    public ResponseEntity<Void> hrRejectLeave(
            Authentication authentication,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectLeaveRequest request
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        leaveService.hrReject(tenantId, requestId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/withdraw")
    @Operation(operationId = "hrLeaveRequestWithdraw")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_REQUEST)
    public ResponseEntity<Void> withdrawLeaveRequest(
            Authentication authentication,
            @PathVariable UUID requestId
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        leaveService.requireOwnedRequest(tenantId, requestId, employmentId);
        leaveService.withdraw(tenantId, requestId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/requests/{requestId}/cancel")
    @Operation(operationId = "hrLeaveRequestCancel")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_HR_APPROVE)
    public ResponseEntity<Void> cancelLeaveRequest(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestParam String reason
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        leaveService.cancel(tenantId, requestId, userId, reason);
        return ResponseEntity.noContent().build();
    }

    // ==================== Leave Balances ====================

    @GetMapping("/leave/balances")
    @Operation(operationId = "hrLeaveBalancesList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_SELF_VIEW)
    public ResponseEntity<List<HrLeaveBalanceResponse>> listLeaveBalances(
            Authentication authentication,
            @RequestParam(required = false) Integer year
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID userId = SecurityContextUtils.userId(authentication);
        UUID employmentId = employmentScopeResolver.requireSelfEmployment(tenantId, userId);
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.listLeaveBalances(tenantId, employmentId, y));
    }

    // ==================== Backward-compatible monthly report ====================

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
        UUID userId = SecurityContextUtils.userId(authentication);
        if (employmentId == null) {
            return ResponseEntity.ok(List.of());
        }
        employmentScopeResolver.requireManagedEmployment(tenantId, userId, employmentId);
        return ResponseEntity.ok(timeService.monthlyAttendanceReport(tenantId, year, month, employmentId));
    }

    // ==================== DTOs ====================

    public record ClockInRequest(LocalDate recordDate) {}
    public record CreateLeaveRequest(UUID leaveTypeId, LocalDate startDate, LocalDate endDate, String reason, String attachmentUrl) {}
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
    public record CreateTimesheetRequest(LocalDate periodStart, LocalDate periodEnd) {}
    public record ApproveRejectRequest(String comment, String reason) {
        public String comment() { return comment; }
        public String reason() { return reason; }
    }
}
