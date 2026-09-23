package com.sanad.platform.hr.time.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Leave service.
 *
 * <p>Manages leave types, leave requests (with approval workflow), and
 * leave balances. Tenant-scoped: every method takes {@code tenantId}.
 *
 * <p>Leave request workflow: PENDING → APPROVED → COMPLETED (or REJECTED).
 * On APPROVED: the leave balance's {@code pending_days} is decremented
 * and {@code used_days} is incremented. On REJECTED: pending_days is
 * decremented only.
 */
@Service
public class HrLeaveService {

    private final JdbcTemplate jdbc;

    public HrLeaveService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    @Transactional
    public HrTimeAttendanceV2Controller.CreateLeaveRequestResponse createLeaveRequest(
            UUID tenantId, UUID userId,
            HrTimeAttendanceV2Controller.CreateLeaveRequest request
    ) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        // Calculate days count (inclusive of both start and end dates)
        long days = java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        BigDecimal daysCount = BigDecimal.valueOf(days);

        jdbc.update(
                "INSERT INTO hr_leave_requests (id, tenant_id, employment_id, leave_type_id, " +
                "start_date, end_date, days_count, reason, attachment_url, state, submitted_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                id, tenantId, request.employmentId(), request.leaveTypeId(),
                request.startDate(), request.endDate(), daysCount,
                request.reason(), request.attachmentUrl(),
                Timestamp.from(now)
        );

        // Increment pending_days on the leave balance
        incrementPendingDays(tenantId, request.employmentId(), request.leaveTypeId(), daysCount);

        return new HrTimeAttendanceV2Controller.CreateLeaveRequestResponse(id);
    }

    @Transactional
    public HrTimeAttendanceV2Controller.HrLeaveRequestResponse approveLeaveRequest(
            UUID tenantId, UUID requestId, UUID approverId,
            HrTimeAttendanceV2Controller.ApproveLeaveRequest request
    ) {
        Instant now = Instant.now();

        // Get the request to find the leave_type_id and days_count
        var reqData = jdbc.queryForObject(
                "SELECT leave_type_id, employment_id, days_count, state FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new Object[]{
                        UUID.fromString(rs.getString("leave_type_id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getBigDecimal("days_count"),
                        rs.getString("state")
                },
                requestId, tenantId
        );

        if (reqData == null || !"PENDING".equals(reqData[3])) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not PENDING");
        }

        UUID leaveTypeId = (UUID) reqData[0];
        UUID employmentId = (UUID) reqData[1];
        BigDecimal daysCount = (BigDecimal) reqData[2];

        // Update the request
        jdbc.update(
                "UPDATE hr_leave_requests SET state = 'APPROVED', approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                approverId, Timestamp.from(now), request.comment(), requestId, tenantId
        );

        // Move days from pending to used on the leave balance
        transferPendingToUsed(tenantId, employmentId, leaveTypeId, daysCount);

        return getLeaveRequest(tenantId, requestId);
    }

    @Transactional
    public HrTimeAttendanceV2Controller.HrLeaveRequestResponse rejectLeaveRequest(
            UUID tenantId, UUID requestId, UUID approverId,
            HrTimeAttendanceV2Controller.RejectLeaveRequest request
    ) {
        Instant now = Instant.now();

        // Get the request to find the leave_type_id and days_count
        var reqData = jdbc.queryForObject(
                "SELECT leave_type_id, employment_id, days_count, state FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new Object[]{
                        UUID.fromString(rs.getString("leave_type_id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getBigDecimal("days_count"),
                        rs.getString("state")
                },
                requestId, tenantId
        );

        if (reqData == null || !"PENDING".equals(reqData[3])) {
            throw new IllegalStateException("HRM_INVALID_STATE_TRANSITION: leave request not PENDING");
        }

        UUID leaveTypeId = (UUID) reqData[0];
        UUID employmentId = (UUID) reqData[1];
        BigDecimal daysCount = (BigDecimal) reqData[2];

        // Update the request
        jdbc.update(
                "UPDATE hr_leave_requests SET state = 'REJECTED', approver_id = ?, approved_at = ?, " +
                "approver_comment = ?, updated_at = NOW() WHERE id = ? AND tenant_id = ?",
                approverId, Timestamp.from(now), request.reason(), requestId, tenantId
        );

        // Decrement pending_days (the leave was never taken)
        decrementPendingDays(tenantId, employmentId, leaveTypeId, daysCount);

        return getLeaveRequest(tenantId, requestId);
    }

    // ==================== Leave Balances ====================

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveBalanceResponse> listLeaveBalances(
            UUID tenantId, UUID employmentId, int year
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employment_id, leave_type_id, year, entitled_days, used_days, pending_days, carried_over_days " +
                "FROM hr_leave_balances WHERE tenant_id = ? AND year = ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        params.add(year);
        if (employmentId != null) {
            sql.append(" AND employment_id = ?");
            params.add(employmentId);
        }
        sql.append(" ORDER BY leave_type_id");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveBalanceResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("employment_id")),
                UUID.fromString(rs.getString("leave_type_id")),
                rs.getInt("year"),
                rs.getBigDecimal("entitled_days"),
                rs.getBigDecimal("used_days"),
                rs.getBigDecimal("pending_days"),
                rs.getBigDecimal("carried_over_days")
        ), params.toArray());
    }

    // ==================== Balance helpers ====================

    private void incrementPendingDays(UUID tenantId, UUID employmentId, UUID leaveTypeId, BigDecimal days) {
        int year = LocalDate.now().getYear();
        // Upsert the balance row
        jdbc.update(
                "INSERT INTO hr_leave_balances (id, tenant_id, employment_id, leave_type_id, year, entitled_days, pending_days) " +
                "VALUES (?, ?, ?, ?, ?, 0, ?) " +
                "ON CONFLICT (tenant_id, employment_id, leave_type_id, year) DO UPDATE " +
                "SET pending_days = hr_leave_balances.pending_days + EXCLUDED.pending_days, updated_at = NOW()",
                UUID.randomUUID(), tenantId, employmentId, leaveTypeId, year, days
        );
    }

    private void transferPendingToUsed(UUID tenantId, UUID employmentId, UUID leaveTypeId, BigDecimal days) {
        int year = LocalDate.now().getYear();
        jdbc.update(
                "UPDATE hr_leave_balances SET pending_days = pending_days - ?, used_days = used_days + ?, updated_at = NOW() " +
                "WHERE tenant_id = ? AND employment_id = ? AND leave_type_id = ? AND year = ?",
                days, days, tenantId, employmentId, leaveTypeId, year
        );
    }

    private void decrementPendingDays(UUID tenantId, UUID employmentId, UUID leaveTypeId, BigDecimal days) {
        int year = LocalDate.now().getYear();
        jdbc.update(
                "UPDATE hr_leave_balances SET pending_days = GREATEST(pending_days - ?, 0), updated_at = NOW() " +
                "WHERE tenant_id = ? AND employment_id = ? AND leave_type_id = ? AND year = ?",
                days, tenantId, employmentId, leaveTypeId, year
        );
    }

    private HrTimeAttendanceV2Controller.HrLeaveRequestResponse getLeaveRequest(UUID tenantId, UUID requestId) {
        List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse> records = jdbc.query(
                "SELECT id, employment_id, leave_type_id, start_date, end_date, days_count, " +
                "reason, state, submitted_at, approved_at, approver_comment " +
                "FROM hr_leave_requests WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrLeaveRequestResponse(
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
                ),
                requestId, tenantId
        );
        return records.isEmpty() ? null : records.get(0);
    }
}
