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

    public HrTimeAttendanceV2Controller(HrTimeAttendanceService timeService, HrLeaveService leaveService) {
        this.timeService = timeService;
        this.leaveService = leaveService;
    }

    // ==================== Attendance ====================

    @GetMapping("/time/attendance")
    @Operation(operationId = "hrAttendanceList")
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_VIEW)
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
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_MANAGE)
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
    @RequireCapability(TimeAttendanceCapabilities.ATTENDANCE_MANAGE)
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
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_VIEW)
    public ResponseEntity<List<HrLeaveTypeResponse>> listLeaveTypes(Authentication authentication) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        return ResponseEntity.ok(leaveService.listLeaveTypes(tenantId));
    }

    // ==================== Leave Requests ====================

    @GetMapping("/leave/requests")
    @Operation(operationId = "hrLeaveRequestsList")
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_VIEW)
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
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_REQUEST)
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
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_APPROVE)
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
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_APPROVE)
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
    @RequireCapability(TimeAttendanceCapabilities.LEAVE_VIEW)
    public ResponseEntity<List<HrLeaveBalanceResponse>> listLeaveBalances(
            Authentication authentication,
            @RequestParam(required = false) UUID employmentId,
            @RequestParam(required = false) Integer year
    ) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        int y = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.listLeaveBalances(tenantId, employmentId, y));
    }

    // ==================== DTOs ====================

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
}
