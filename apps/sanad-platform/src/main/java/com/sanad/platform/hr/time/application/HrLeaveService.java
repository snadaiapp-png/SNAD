package com.sanad.platform.hr.time.application;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G2 Leave service with multi-step state machine + Workflow Engine integration.
 */
@Service
public class HrLeaveService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final HrLeaveLedgerService ledgerService;
    private final HrLeaveWorkflowAdapter workflowAdapter;

    public HrLeaveService(JdbcTemplate jdbc, Clock clock, HrLeaveLedgerService ledgerService,
                         HrLeaveWorkflowAdapter workflowAdapter) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ledgerService = ledgerService;
        this.workflowAdapter = workflowAdapter;
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

    // ==================== Leave Requests ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse> listLeaveRequests(
            UUID tenantId, UUID employmentId, String state) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employment_id, leave_type_id, start_date, end_date, days_count, " +
                "reason, state, submitted_at, approved_at, approver_comment " +
                "FROM hr_leave_requests WHERE tenant_id = ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        if (employmentId != null) { sql.append(" AND employment_id = ?"); params.add(employmentId); }
        if (state != null && !state.isBlank()) { sql.append(" AND state = ?"); params.add(state); }
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

    @Transactional
    public HrTimeAttendanceV2Controller.CreateLeaveRequestResponse createLeaveRequest(
            UUID tenantId,
            UUID userId,
            UUID employmentId,
            HrTimeAttendanceV2Controller.CreateLeaveRequest request) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        long days = java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        BigDecimal daysCount = BigDecimal.valueOf(days);
        jdbc.update(
                "INSERT INTO hr_leave_requests (id, tenant_id, employment_id, leave_type_id, " +
                "start_date, end_date, days_count, reason, attachment_url, state, submitted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)",
                id, tenantId, employmentId, request.leaveTypeId(),
                request.startDate(), request.endDate(), daysCount,
                request.reason(), request.attachmentUrl(), Timestamp.from(now));
        writeAuditAndOutbox(tenantId, "LeaveRequested", id, userId);
        return new HrTimeAttendanceV2Controller.CreateLeaveRequestResponse(id);
    }

    /** Return the request employment inside the current tenant or fail closed. */
    @Transactional(readOnly = true)
    public UUID requireRequestEmployment(UUID tenantId, UUID requestId) {
        List<UUID> matches = jdbc.query(
                "SELECT employment_id FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> UUID.fromString(rs.getString("employment_id")),
                requestId,
                tenantId);
        if (matches.size() != 1) {
            throw new IllegalStateException("HRM_LEAVE_REQUEST_NOT_FOUND_IN_TENANT");
        }
        return matches.get(0);
    }

    /** SELF lifecycle guard: request must belong to the resolved authenticated employment. */
    @Transactional(readOnly = true)
    public void requireOwnedRequest(UUID tenantId, UUID requestId, UUID employmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_leave_requests WHERE id = ? AND tenant_id = ? AND employment_id = ?",
                Integer.class,
                requestId,
                tenantId,
                employmentId);
        if (count == null || count != 1) {
            throw new IllegalStateException("HRM_LEAVE_REQUEST_NOT_FOUND_IN_SELF_SCOPE");
        }
    }

    @Transactional
    public void submitLeaveRequest(UUID tenantId, UUID requestId, UUID userId) {
        Map<String, Object> lockedRow = jdbc.queryForMap(
                "SELECT state, workflow_instance_id, leave_type_id, employment_id, days_count " +
                "FROM hr_leave_requests WHERE id = ? AND tenant_id = ? FOR UPDATE",
                requestId, tenantId);

        String currentState = (String) lockedRow.get("state");

        if ("PENDING_MANAGER".equals(currentState) && lockedRow.get("workflow_instance_id") != null) {
            return;
        }

        if (!"DRAFT".equals(currentState)) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not in DRAFT state, got " + currentState);
        }

        UUID leaveTypeId = (UUID) lockedRow.get("leave_type_id");
        UUID employmentId = (UUID) lockedRow.get("employment_id");
        BigDecimal daysCount = (java.math.BigDecimal) lockedRow.get("days_count");

        UUID workflowInstanceId = workflowAdapter.startLeaveApproval(tenantId, requestId, userId);

        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'PENDING_MANAGER', " +
                "workflow_instance_id = ?, current_workflow_step = 'MANAGER_APPROVAL', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'DRAFT'",
                workflowInstanceId, requestId, tenantId);
        if (updated == 0) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not in DRAFT state");
        }

        ledgerService.reserve(tenantId, employmentId, leaveTypeId, daysCount, requestId);
        writeAuditAndOutbox(tenantId, "LeaveRequested", requestId, userId);
    }

    @Transactional
    public void managerApprove(UUID tenantId, UUID requestId, UUID managerId,
                               HrTimeAttendanceV2Controller.ApproveLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        String currentStep = workflowAdapter.getCurrentStepKey(tenantId, workflowInstanceId);
        if (!"manager_approval".equals(currentStep)) {
            throw new IllegalStateException(
                "HRM_INVALID_STATE: expected manager_approval step, got " + currentStep);
        }

        Optional<WorkflowApprovalRequest> pending = workflowAdapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");
        if (pending.isEmpty()) {
            throw new IllegalStateException("HRM_INVALID_STATE: no pending Manager workflow approval found");
        }
        workflowAdapter.approveApproval(tenantId, pending.get().id(), managerId,
                pending.get().version(), request.comment());

        if (!workflowAdapter.isRunningAt(tenantId, workflowInstanceId, "hr_approval")) {
            throw new IllegalStateException(
                "HRM_WORKFLOW_INCONSISTENT: expected RUNNING at hr_approval after manager approve");
        }

        transitionState(tenantId, requestId, "PENDING_MANAGER", "PENDING_HR",
                managerId, request.comment(), clock.instant());
        updateWorkflowStep(tenantId, requestId, "HR_APPROVAL");
        writeAuditAndOutbox(tenantId, "LeaveManagerApproved", requestId, managerId);
    }

    @Transactional
    public void managerReject(UUID tenantId, UUID requestId, UUID managerId,
                              HrTimeAttendanceV2Controller.RejectLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        String currentStep = workflowAdapter.getCurrentStepKey(tenantId, workflowInstanceId);
        if (!"manager_approval".equals(currentStep)) {
            throw new IllegalStateException(
                "HRM_INVALID_STATE: expected manager_approval step, got " + currentStep);
        }

        Optional<WorkflowApprovalRequest> pending = workflowAdapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");
        if (pending.isEmpty()) {
            throw new IllegalStateException("HRM_INVALID_STATE: no pending Manager workflow approval found");
        }
        workflowAdapter.rejectApproval(tenantId, pending.get().id(), managerId,
                pending.get().version(), request.reason());

        if (!workflowAdapter.isCompletedAt(tenantId, workflowInstanceId, "end_rejected")) {
            throw new IllegalStateException(
                "HRM_WORKFLOW_INCONSISTENT: expected COMPLETED at end_rejected after manager reject");
        }

        transitionState(tenantId, requestId, "PENDING_MANAGER", "REJECTED",
                managerId, request.reason(), clock.instant());
        var reqData = getRequestData(tenantId, requestId);
        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);
        writeAuditAndOutbox(tenantId, "LeaveRejected", requestId, managerId);
    }

    @Transactional
    public void hrApprove(UUID tenantId, UUID requestId, UUID hrUserId,
                          HrTimeAttendanceV2Controller.ApproveLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        String currentStep = workflowAdapter.getCurrentStepKey(tenantId, workflowInstanceId);
        if (!"hr_approval".equals(currentStep)) {
            throw new IllegalStateException(
                "HRM_INVALID_STATE: expected hr_approval step, got " + currentStep);
        }

        Optional<WorkflowApprovalRequest> pending = workflowAdapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");
        if (pending.isEmpty()) {
            throw new IllegalStateException("HRM_INVALID_STATE: no pending HR workflow approval found");
        }
        workflowAdapter.approveApproval(tenantId, pending.get().id(), hrUserId,
                pending.get().version(), request.comment());

        if (!workflowAdapter.isCompletedAt(tenantId, workflowInstanceId, "end_approved")) {
            throw new IllegalStateException(
                "HRM_WORKFLOW_INCONSISTENT: expected COMPLETED at end_approved after HR approve");
        }

        transitionState(tenantId, requestId, "PENDING_HR", "APPROVED",
                hrUserId, request.comment(), clock.instant());
        var reqData = getRequestData(tenantId, requestId);
        ledgerService.consume(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);
        writeAuditAndOutbox(tenantId, "LeaveHrApproved", requestId, hrUserId);
    }

    @Transactional
    public void hrReject(UUID tenantId, UUID requestId, UUID hrUserId,
                         HrTimeAttendanceV2Controller.RejectLeaveRequest request) {
        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        String currentStep = workflowAdapter.getCurrentStepKey(tenantId, workflowInstanceId);
        if (!"hr_approval".equals(currentStep)) {
            throw new IllegalStateException(
                "HRM_INVALID_STATE: expected hr_approval step, got " + currentStep);
        }

        Optional<WorkflowApprovalRequest> pending = workflowAdapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");
        if (pending.isEmpty()) {
            throw new IllegalStateException("HRM_INVALID_STATE: no pending HR workflow approval found");
        }
        workflowAdapter.rejectApproval(tenantId, pending.get().id(), hrUserId,
                pending.get().version(), request.reason());

        if (!workflowAdapter.isCompletedAt(tenantId, workflowInstanceId, "end_rejected")) {
            throw new IllegalStateException(
                "HRM_WORKFLOW_INCONSISTENT: expected COMPLETED at end_rejected after HR reject");
        }

        transitionState(tenantId, requestId, "PENDING_HR", "REJECTED",
                hrUserId, request.reason(), clock.instant());
        var reqData = getRequestData(tenantId, requestId);
        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);
        writeAuditAndOutbox(tenantId, "LeaveRejected", requestId, hrUserId);
    }

    @Transactional
    public void withdraw(UUID tenantId, UUID requestId, UUID userId) {
        var reqData = getRequestData(tenantId, requestId);
        String currentState = (String) reqData[3];
        if (!"SUBMITTED".equals(currentState) && !"PENDING_MANAGER".equals(currentState) && !"PENDING_HR".equals(currentState)) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: cannot withdraw from " + currentState);
        }

        UUID workflowInstanceId = getWorkflowInstanceId(tenantId, requestId);
        workflowAdapter.cancelIfRunning(tenantId, workflowInstanceId, userId, "Employee withdrew");

        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'WITHDRAWN', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state IN ('SUBMITTED','PENDING_MANAGER','PENDING_HR')",
                requestId, tenantId);
        if (updated == 0) throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: withdraw failed");

        ledgerService.release(tenantId, (UUID) reqData[1], (UUID) reqData[0], (BigDecimal) reqData[2], requestId);
        writeAuditAndOutbox(tenantId, "LeaveWithdrawn", requestId, userId);
    }

    @Transactional
    public void cancel(UUID tenantId, UUID requestId, UUID userId, String reason) {
        var reqData = getRequestData(tenantId, requestId);
        if (!"APPROVED".equals(reqData[3])) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: can only cancel APPROVED requests");
        }

        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = 'CANCELLED', approver_comment = ?, updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND state = 'APPROVED'",
                reason, requestId, tenantId);
        if (updated == 0) throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: cancel failed");

        UUID leaveTypeId = (UUID) reqData[0];
        UUID employmentId = (UUID) reqData[1];
        BigDecimal daysCount = (BigDecimal) reqData[2];
        int year = LocalDate.now(clock).getYear();
        jdbc.update(
                "INSERT INTO hr_leave_ledger_entries " +
                "(id, tenant_id, employment_id, leave_type_id, entry_type, year, days, reference_type, reference_id, reason) " +
                "VALUES (?, ?, ?, ?, 'ADJUSTMENT', ?, ?, 'LEAVE_REQUEST', ?, ?)",
                UUID.randomUUID(), tenantId, employmentId, leaveTypeId, year,
                daysCount, requestId, "Cancellation compensation");
        writeAuditAndOutbox(tenantId, "LeaveCancelled", requestId, userId);
    }

    // ==================== Leave Balances ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveBalanceResponse> listLeaveBalances(
            UUID tenantId, UUID employmentId, int year) {
        StringBuilder sql = new StringBuilder(
                "SELECT lt.id as leave_type_id, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type IN ('OPENING','ACCRUAL','ADJUSTMENT','RELEASE','CARRYOVER') THEN le.days ELSE 0 END), 0) as entitled, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'CONSUMPTION' THEN le.days ELSE 0 END), 0) as used, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'RESERVATION' THEN le.days ELSE 0 END), 0) as pending, " +
                "  COALESCE(SUM(CASE WHEN le.entry_type = 'CARRYOVER' THEN le.days ELSE 0 END), 0) as carried " +
                "FROM hr_leave_types lt " +
                "LEFT JOIN hr_leave_ledger_entries le ON le.leave_type_id = lt.id AND le.tenant_id = lt.tenant_id AND le.year = ? " +
                "WHERE lt.tenant_id = ? AND lt.state = 'ACTIVE'");
        List<Object> params = new java.util.ArrayList<>();
        params.add(year); params.add(tenantId);
        if (employmentId != null) { sql.append(" AND (le.employment_id = ? OR le.employment_id IS NULL)"); params.add(employmentId); }
        sql.append(" GROUP BY lt.id, lt.code ORDER BY lt.code");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveBalanceResponse(
                UUID.randomUUID(), employmentId != null ? employmentId : UUID.randomUUID(),
                UUID.fromString(rs.getString("leave_type_id")), year,
                rs.getBigDecimal("entitled"), rs.getBigDecimal("used"),
                rs.getBigDecimal("pending"), rs.getBigDecimal("carried")
        ), params.toArray());
    }

    // ==================== Helpers ====================

    private void transitionState(UUID tenantId, UUID requestId, String fromState, String toState,
                                  UUID actorId, String comment, Instant now) {
        int updated = jdbc.update(
                "UPDATE hr_leave_requests SET state = ?, approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() WHERE id = ? AND tenant_id = ? AND state = ?",
                toState, actorId, Timestamp.from(now), comment, requestId, tenantId, fromState);
        if (updated == 0) throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: " + fromState + " → " + toState + " failed");
    }

    private Object[] getRequestData(UUID tenantId, UUID requestId) {
        return jdbc.queryForObject(
                "SELECT leave_type_id, employment_id, days_count, state FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new Object[]{
                        UUID.fromString(rs.getString("leave_type_id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getBigDecimal("days_count"),
                        rs.getString("state")
                }, requestId, tenantId);
    }

    private UUID getWorkflowInstanceId(UUID tenantId, UUID requestId) {
        try {
            return jdbc.queryForObject(
                    "SELECT workflow_instance_id FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                    UUID.class, requestId, tenantId);
        } catch (Exception e) { return null; }
    }

    private void updateWorkflowStep(UUID tenantId, UUID requestId, String step) {
        jdbc.update("UPDATE hr_leave_requests SET current_workflow_step = ?, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                step, requestId, tenantId);
    }

    private void writeAuditAndOutbox(UUID tenantId, String eventType, UUID resourceId, UUID actorId) {
        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO hr_audit_ledger " +
                "(id, tenant_id, actor_user_id, action, resource_type, resource_id, data_classification, after_state, result, occurred_at) " +
                "VALUES (?, ?, ?, ?, 'LEAVE_REQUEST', ?, 'OPERATIONAL', ?::jsonb, 'SUCCESS', ?)",
                auditId, tenantId, actorId, eventType, resourceId,
                "{\"resourceId\":\"" + resourceId + "\",\"eventType\":\"" + eventType + "\"}",
                Timestamp.from(now));
        jdbc.update(
                "INSERT INTO hr_domain_event_outbox (id, tenant_id, event_type, resource_type, resource_id, payload, created_at) " +
                "VALUES (?, ?, ?, 'LEAVE_REQUEST', ?, ?::jsonb, ?)",
                outboxId, tenantId, eventType, resourceId,
                "{\"eventType\":\"" + eventType + "\",\"resourceId\":\"" + resourceId + "\"}",
                Timestamp.from(now));
    }
}
