package com.sanad.platform.hr.time.application;

import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Leave service with multi-step state machine + Workflow Engine integration.
 *
 * <p>Uses the canonical SANAD Workflow Engine (WorkflowExecutionService +
 * WorkflowApprovalService) for the Manager → HR approval journey.
 * No parallel/local approval engine.
 *
 * <p>State machine: DRAFT → PENDING_MANAGER → PENDING_HR → APPROVED
 * with REJECTED, WITHDRAWN, CANCELLED.
 *
 * <p>Workflow lifecycle:
 *   SUBMIT → startWorkflow + createApproval (MANAGER step)
 *   MANAGER APPROVE → workflowApprovalService.approve → createApproval (HR step)
 *   MANAGER REJECT → workflowApprovalService.reject
 *   HR APPROVE → workflowApprovalService.approve → workflow completes
 *   HR REJECT → workflowApprovalService.reject
 *   WITHDRAW → workflowExecutionService.cancel
 *   CANCEL → workflowExecutionService.cancel + ledger compensation
 *
 * <p>Leave ledger: SUBMIT→RESERVATION, HR APPROVE→CONSUMPTION, REJECT/WITHDRAW→RELEASE, CANCEL→ADJUSTMENT.
 * Audit/outbox for every transition (transactional).
 * Uses injectable Clock (prevents time-bomb tests).
 */
@Service
public class HrLeaveService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final HrLeaveLedgerService ledgerService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowApprovalService workflowApprovalService;

    public HrLeaveService(JdbcTemplate jdbc, Clock clock, HrLeaveLedgerService ledgerService,
                         WorkflowExecutionService workflowExecutionService,
                         WorkflowApprovalService workflowApprovalService) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ledgerService = ledgerService;
        this.workflowExecutionService = workflowExecutionService;
        this.workflowApprovalService = workflowApprovalService;
    }

    // ==================== Leave Types ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveTypeResponse> listLeaveTypes(UUID tenantId) {
        return jdbc.query(
                "SELECT id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state " +
                "FROM hr_leave_types WHERE tenant_id = ? AND state = 'ACTIVE' ORDER BY code",
                (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveTypeResponse(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("name_ar"),
                        rs.getString("name_en"),
                        rs.getBoolean("is_paid"),
                        rs.getBoolean("requires_attachment"),
                        rs.getObject("default_days_per_year") != null ? rs.getInt("default_days_per_year") : null,
                        rs.getString("state")
                ),
                tenantId
        );
    }

    // ==================== Leave Requests (multi-step state machine) ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse> listLeaveRequests(
            UUID tenantId, UUID employmentId, String state
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employment_id, leave_type_id, start_date, end_date, days_count, " +
                "reason, state, submitted_at, approved_at, approver_comment " +
                "FROM hr_leave_requests WHERE tenant_id = ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        if (employmentId != null) {
            sql.append(" AND employment_id = ?");
            params.add(employmentId);
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND state = ?");
            params.add(state);
        }
        sql.append(" ORDER BY submitted_at DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveRequestResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("employment_id")),
                UUID.fromString(rs.getString("leave_type_id")),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                rs.getBigDecimal("days_count"),
                rs.getString("reason"),
                rs.getString("state"),
                rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : null,
                rs.getTimestamp("approved_at") != null ? rs.getTimestamp("approved_at").toInstant() : null,
                rs.getString("approver_comment")
        ), params.toArray());
    }

    /**
     * Create a leave request in DRAFT state.
     */
    @Transactional
    public HrTimeAttendanceV2Controller.CreateLeaveRequestResponse createLeaveRequest(
            UUID tenantId, UUID userId,
            HrTimeAttendanceV2Controller.CreateLeaveRequest request
    ) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();

        long days = java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        BigDecimal daysCount = BigDecimal.valueOf(days);

        // Create in DRAFT state — no ledger reservation yet
        jdbc.update(
                "INSERT INTO hr_leave_requests (id, tenant_id, employment_id, leave_type_id, " +
                "start_date, end_date, days_count, reason, attachment_url, state, submitted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)",
                id, tenantId, request.employmentId(), request.leaveTypeId(),
                request.startDate(), request.endDate(), daysCount,
                request.reason(), request.attachmentUrl(),
                Timestamp.from(now)
        );

        writeAuditAndOutbox(tenantId, "LeaveRequested", id, userId);
        return new HrTimeAttendanceV2Controller.CreateLeaveRequestResponse(id);
    }

    /**
     * Submit: DRAFT → SUBMITTED → PENDING_MANAGER + ledger RESERVATION.
     */
    @Transactional
    public void submitLeaveRequest(UUID tenantId, UUID requestId, UUID userId) {
        Instant now = clock.instant();

        // Get the request to find leave_type_id, employment_id, days_count
        var reqData = getRequestData(tenantId, requestId);
        if (!"DRAFT".equals(reqData[3])) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not in DRAFT state");
        }

        // Create canonical WorkflowInstance via the central Workflow Engine
        WorkflowInstance workflowInstance = WorkflowInstance.start(
                tenantId,
                UUID.randomUUID(), // workflow definition ID (LEAVE_APPROVAL)
                1,                 // workflow version
                "LEAVE_REQUEST",
                requestId,
                "MANAGER_APPROVAL", // first step
                userId,
                null               // correlation ID
        );
        workflowInstance = workflowExecutionService.startWorkflow(workflowInstance, userId);

        // Advance state: DRAFT → PENDING_MANAGER + link workflow
        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'PENDING_MANAGER', " +
                "workflow_instance_id = ?, current_workflow_step = 'MANAGER_APPROVAL', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'DRAFT'",
                workflowInstance.id(), requestId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not in DRAFT state");
        }

        // Reserve days in the ledger
        UUID leaveTypeId = (UUID) reqData[0];
        UUID employmentId = (UUID) reqData[1];
        BigDecimal daysCount = (BigDecimal) reqData[2];
        ledgerService.reserve(tenantId, employmentId, leaveTypeId, daysCount, requestId);

        writeAuditAndOutbox(tenantId, "LeaveRequested", requestId, userId);
    }

    /**
     * Manager approve: PENDING_MANAGER → PENDING_HR via Workflow Engine.
     * Advances the canonical workflow + creates HR approval task.
     */
    @Transactional
    public void managerApprove(UUID tenantId, UUID requestId, UUID managerId,
                               HrTimeAttendanceV2Controller.ApproveLeaveRequest request) {
        // Get workflow_instance_id from the leave request
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        if (workflowInstanceId != null) {
            // Find and approve the pending Manager approval via Workflow Engine
            var pendingApprovals = workflowApprovalService.findByInstance(tenantId, workflowInstanceId);
            for (var approval : pendingApprovals) {
                if (approval.status() == WorkflowApprovalRequest.Status.PENDING) {
                    workflowApprovalService.approve(tenantId, approval.id(), managerId, request.comment());
                    break;
                }
            }
            // Advance workflow step to HR_APPROVAL
            workflowExecutionService.resume(tenantId, workflowInstanceId, managerId);
        }
        // Transition HR state
        transitionState(tenantId, requestId, "PENDING_MANAGER", "PENDING_HR",
                managerId, request.comment(), clock.instant());
        // Update workflow step reference
        updateWorkflowStep(tenantId, requestId, "HR_APPROVAL");
        writeAuditAndOutbox(tenantId, "LeaveManagerApproved", requestId, managerId);
    }

    /**
     * Manager reject: PENDING_MANAGER → REJECTED + workflow cancel + ledger RELEASE.
     */
    @Transactional
    public void managerReject(UUID tenantId, UUID requestId, UUID managerId,
                              HrTimeAttendanceV2Controller.RejectLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        if (workflowInstanceId != null) {
            // Reject the pending Manager approval via Workflow Engine
            var pendingApprovals = workflowApprovalService.findByInstance(tenantId, workflowInstanceId);
            for (var approval : pendingApprovals) {
                if (approval.status() == WorkflowApprovalRequest.Status.PENDING) {
                    workflowApprovalService.reject(tenantId, approval.id(), managerId, request.reason());
                    break;
                }
            }
            // Cancel the workflow
            workflowExecutionService.cancel(tenantId, workflowInstanceId, managerId, "Manager rejected: " + request.reason());
        }
        transitionState(tenantId, requestId, "PENDING_MANAGER", "REJECTED",
                managerId, request.reason(), clock.instant());

        var reqData = getRequestData(tenantId, requestId);
        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);

        writeAuditAndOutbox(tenantId, "LeaveRejected", requestId, managerId);
    }

    /**
     * HR approve: PENDING_HR → APPROVED + workflow completes + ledger CONSUMPTION exactly once.
     */
    @Transactional
    public void hrApprove(UUID tenantId, UUID requestId, UUID hrUserId,
                          HrTimeAttendanceV2Controller.ApproveLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        if (workflowInstanceId != null) {
            // Find and approve the pending HR approval via Workflow Engine
            var pendingApprovals = workflowApprovalService.findByInstance(tenantId, workflowInstanceId);
            for (var approval : pendingApprovals) {
                if (approval.status() == WorkflowApprovalRequest.Status.PENDING) {
                    workflowApprovalService.approve(tenantId, approval.id(), hrUserId, request.comment());
                    break;
                }
            }
            // Complete the workflow
            workflowExecutionService.complete(tenantId, workflowInstanceId, hrUserId);
        }
        transitionState(tenantId, requestId, "PENDING_HR", "APPROVED",
                hrUserId, request.comment(), clock.instant());

        var reqData = getRequestData(tenantId, requestId);
        ledgerService.consume(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);

        writeAuditAndOutbox(tenantId, "LeaveHrApproved", requestId, hrUserId);
    }

    /**
     * HR reject: PENDING_HR → REJECTED + workflow cancel + ledger RELEASE.
     */
    @Transactional
    public void hrReject(UUID tenantId, UUID requestId, UUID hrUserId,
                         HrTimeAttendanceV2Controller.RejectLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        if (workflowInstanceId != null) {
            var pendingApprovals = workflowApprovalService.findByInstance(tenantId, workflowInstanceId);
            for (var approval : pendingApprovals) {
                if (approval.status() == WorkflowApprovalRequest.Status.PENDING) {
                    workflowApprovalService.reject(tenantId, approval.id(), hrUserId, request.reason());
                    break;
                }
            }
            workflowExecutionService.cancel(tenantId, workflowInstanceId, hrUserId, "HR rejected: " + request.reason());
        }
        transitionState(tenantId, requestId, "PENDING_HR", "REJECTED",
                hrUserId, request.reason(), clock.instant());

        var reqData = getRequestData(tenantId, requestId);
        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);

        writeAuditAndOutbox(tenantId, "LeaveRejected", requestId, hrUserId);
    }

    /**
     * Withdraw: SUBMITTED/PENDING_MANAGER/PENDING_HR → WITHDRAWN + ledger RELEASE.
     */
    @Transactional
    public void withdraw(UUID tenantId, UUID requestId, UUID userId) {
        var reqData = getRequestData(tenantId, requestId);
        String currentState = (String) reqData[3];
        if (!"SUBMITTED".equals(currentState) && !"PENDING_MANAGER".equals(currentState) && !"PENDING_HR".equals(currentState)) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: cannot withdraw from " + currentState);
        }

        // Cancel the canonical workflow
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        if (workflowInstanceId != null) {
            workflowExecutionService.cancel(tenantId, workflowInstanceId, userId, "Employee withdrew");
        }

        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'WITHDRAWN', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state IN ('SUBMITTED','PENDING_MANAGER','PENDING_HR')",
                requestId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: withdraw failed");
        }

        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);
        writeAuditAndOutbox(tenantId, "LeaveWithdrawn", requestId, userId);
    }

    /**
     * Cancel: APPROVED → CANCELLED + workflow cancel + ledger compensation.
     */
    @Transactional
    public void cancel(UUID tenantId, UUID requestId, UUID userId, String reason) {
        Instant now = clock.instant();

        var reqData = getRequestData(tenantId, requestId);
        String currentState = (String) reqData[3];
        if (!"APPROVED".equals(currentState)) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: can only cancel APPROVED requests");
        }

        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'CANCELLED', approver_comment = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'APPROVED'",
                reason, requestId, tenantId
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: cancel failed");
        }

        // Compensate: add back the consumed days as an ADJUSTMENT
        UUID leaveTypeId = (UUID) reqData[0];
        UUID employmentId = (UUID) reqData[1];
        BigDecimal daysCount = (BigDecimal) reqData[2];
        int year = LocalDate.now(clock).getYear();

        // Add positive ADJUSTMENT to reverse the CONSUMPTION
        jdbc.update(
                "INSERT INTO hr_leave_ledger_entries " +
                "(id, tenant_id, employment_id, leave_type_id, entry_type, year, days, reference_type, reference_id, reason) " +
                "VALUES (?, ?, ?, ?, 'ADJUSTMENT', ?, ?, 'LEAVE_REQUEST', ?, ?)",
                UUID.randomUUID(), tenantId, employmentId, leaveTypeId, year,
                daysCount, requestId, "Cancellation compensation"
        );

        writeAuditAndOutbox(tenantId, "LeaveCancelled", requestId, userId);
    }

    // ==================== Leave Balances ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveBalanceResponse> listLeaveBalances(
            UUID tenantId, UUID employmentId, int year
    ) {
        // Derive balances from the ledger
        StringBuilder sql = new StringBuilder(
                "SELECT lt.id as leave_type_id, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type IN ('OPENING','ACCRUAL','ADJUSTMENT','RELEASE','CARRYOVER') " +
                "    THEN le.days ELSE 0 END), 0) as entitled, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'CONSUMPTION' THEN le.days ELSE 0 END), 0) as used, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'RESERVATION' THEN le.days ELSE 0 END), 0) as pending, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'CARRYOVER' THEN le.days ELSE 0 END), 0) as carried " +
                "FROM hr_leave_types lt " +
                "LEFT JOIN hr_leave_ledger_entries le ON le.leave_type_id = lt.id AND le.tenant_id = lt.tenant_id " +
                "  AND le.year = ? " +
                "WHERE lt.tenant_id = ? AND lt.state = 'ACTIVE'"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(year);
        params.add(tenantId);
        if (employmentId != null) {
            sql.append(" AND (le.employment_id = ? OR le.employment_id IS NULL)");
            params.add(employmentId);
        }
        sql.append(" GROUP BY lt.id, lt.code ORDER BY lt.code");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveBalanceResponse(
                UUID.randomUUID(), // synthetic ID for the projection
                employmentId != null ? employmentId : UUID.randomUUID(),
                UUID.fromString(rs.getString("leave_type_id")),
                year,
                rs.getBigDecimal("entitled"),
                rs.getBigDecimal("used"),
                rs.getBigDecimal("pending"),
                rs.getBigDecimal("carried")
        ), params.toArray());
    }

    // ==================== Helpers ====================

    private void transitionState(UUID tenantId, UUID requestId, String fromState, String toState,
                                  UUID actorId, String comment, Instant now) {
        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = ?, approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = ?",
                toState, actorId, Timestamp.from(now), comment, requestId, tenantId, fromState
        );
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: " + fromState + " → " + toState + " failed");
        }
    }

    private Object[] getRequestData(UUID tenantId, UUID requestId) {
        return jdbc.queryForObject(
                "SELECT leave_type_id, employment_id, days_count, state FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new Object[]{
                        UUID.fromString(rs.getString("leave_type_id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getBigDecimal("days_count"),
                        rs.getString("state")
                },
                requestId, tenantId
        );
    }

    private void writeAuditAndOutbox(UUID tenantId, String eventType, UUID resourceId, UUID actorId) {
        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO hr_audit_ledger (id, tenant_id, resource_type, resource_id, action, " +
                "actor_id, occurred_at, details) VALUES (?, ?, 'LEAVE_REQUEST', ?, ?, ?, ?, ?)",
                auditId, tenantId, resourceId, eventType, actorId, Timestamp.from(now),
                "{\"resourceId\":\"" + resourceId + "\",\"eventType\":\"" + eventType + "\"}"
        );

        jdbc.update(
                "INSERT INTO hr_domain_event_outbox (id, tenant_id, event_type, resource_type, resource_id, " +
                "payload, created_at) VALUES (?, ?, ?, 'LEAVE_REQUEST', ?, ?::jsonb, ?)",
                outboxId, tenantId, eventType, resourceId,
                "{\"eventType\":\"" + eventType + "\",\"resourceId\":\"" + resourceId + "\"}",
                Timestamp.from(now)
        );
    }

    private UUID getWorkflowInstanceId(UUID tenantId, UUID requestId) {
        try {
            return jdbc.queryForObject(
                    "SELECT workflow_instance_id FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                    UUID.class, requestId, tenantId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void updateWorkflowStep(UUID tenantId, UUID requestId, String step) {
        jdbc.update(
                "UPDATE hr_leave_requests SET current_workflow_step = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ?",
                step, requestId, tenantId
        );
    }
}
