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
 * HRM-G2 Time & Attendance service.
 *
 * <p>Manages attendance records (clock-in/clock-out) and leave requests/balances.
 * Tenant-scoped: every method takes {@code tenantId} as its first parameter.
 *
 * <p>Idempotency: clock-in is idempotent per (tenant, employment, record_date)
 * — if a record already exists for the given date, it returns the existing
 * record instead of creating a duplicate. Leave requests use a separate
 * idempotency key passed by the controller.
 *
 * <p>RLS is enforced at the database level (V20260923_2). The service
 * connects as the {@code sanad} role (NOSUPERUSER NOBYPASSRLS) with
 * {@code SET app.tenant_id} set by the JWT filter.
 */
@Service
public class HrTimeAttendanceService {

    private final JdbcTemplate jdbc;

    public HrTimeAttendanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== Attendance ====================

    @Transactional
    public HrTimeAttendanceV2Controller.HrAttendanceRecordResponse clockIn(
            UUID tenantId, UUID userId,
            HrTimeAttendanceV2Controller.ClockInRequest request
    ) {
        // Idempotent: if a record already exists for this date, return it.
        List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> existing =
                listAttendance(tenantId, request.employmentId(), request.recordDate(), request.recordDate());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO hr_attendance_records (id, tenant_id, employment_id, record_date, clock_in, source, state) " +
                "VALUES (?, ?, ?, ?, ?, 'MANUAL', 'OPEN')",
                id, tenantId, request.employmentId(), request.recordDate(), Timestamp.from(now)
        );
        return getAttendanceRecord(tenantId, id);
    }

    @Transactional
    public HrTimeAttendanceV2Controller.HrAttendanceRecordResponse clockOut(
            UUID tenantId, UUID userId, UUID recordId
    ) {
        Instant now = Instant.now();
        // Calculate worked minutes
        Timestamp clockOutTs = Timestamp.from(now);
        jdbc.update(
                "UPDATE hr_attendance_records SET clock_out = ?, worked_minutes = " +
                "EXTRACT(EPOCH FROM (? - clock_in))::int / 60, state = 'COMPLETED', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ?",
                clockOutTs, clockOutTs, recordId, tenantId
        );
        return getAttendanceRecord(tenantId, recordId);
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> listAttendance(
            UUID tenantId, UUID employmentId, LocalDate startDate, LocalDate endDate
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employment_id, record_date, clock_in, clock_out, break_minutes, worked_minutes, source, state " +
                "FROM hr_attendance_records WHERE tenant_id = ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        if (employmentId != null) {
            sql.append(" AND employment_id = ?");
            params.add(employmentId);
        }
        if (startDate != null) {
            sql.append(" AND record_date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND record_date <= ?");
            params.add(endDate);
        }
        sql.append(" ORDER BY record_date DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrAttendanceRecordResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("employment_id")),
                rs.getDate("record_date").toLocalDate(),
                rs.getTimestamp("clock_in") != null ? rs.getTimestamp("clock_in").toInstant() : null,
                rs.getTimestamp("clock_out") != null ? rs.getTimestamp("clock_out").toInstant() : null,
                rs.getInt("break_minutes"),
                rs.getObject("worked_minutes") != null ? rs.getInt("worked_minutes") : null,
                rs.getString("source"),
                rs.getString("state")
        ), params.toArray());
    }

    private HrTimeAttendanceV2Controller.HrAttendanceRecordResponse getAttendanceRecord(UUID tenantId, UUID id) {
        List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> records = jdbc.query(
                "SELECT id, employment_id, record_date, clock_in, clock_out, break_minutes, worked_minutes, source, state " +
                "FROM hr_attendance_records WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new HrTimeAttendanceV2Controller.HrAttendanceRecordResponse(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getDate("record_date").toLocalDate(),
                        rs.getTimestamp("clock_in") != null ? rs.getTimestamp("clock_in").toInstant() : null,
                        rs.getTimestamp("clock_out") != null ? rs.getTimestamp("clock_out").toInstant() : null,
                        rs.getInt("break_minutes"),
                        rs.getObject("worked_minutes") != null ? rs.getInt("worked_minutes") : null,
                        rs.getString("source"),
                        rs.getString("state")
                ),
                id, tenantId
        );
        return records.isEmpty() ? null : records.get(0);
    }

    // ==================== G2-T05: Monthly Attendance Report ====================

    /**
     * Generate a monthly attendance report per employee for the given year+month.
     *
     * <p>The report is a DERIVED projection from {@code hr_attendance_records}
     * — no separate report table is maintained. The query aggregates per
     * employment_id for the given month:
     * <ul>
     *   <li>worked_minutes: sum of worked_minutes from COMPLETED records</li>
     *   <li>absent_days: count of dates with state=MISSED</li>
     *   <li>missing_punches: count of OPEN records (clock-in without clock-out)</li>
     * </ul>
     *
     * <p>Leave days are derived from {@code hr_leave_requests} where state=APPROVED
     * and the request overlaps the given month.
     *
     * <p>Scheduled days/minutes are not yet available (scheduling domain is
     * not implemented in this PR — follow-up needed). These are NULL in the
     * report until the scheduling domain is built.
     */
    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow> monthlyAttendanceReport(
            UUID tenantId, int year, int month, UUID employmentId
    ) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        StringBuilder sql = new StringBuilder(
                "SELECT employment_id, " +
                "  COUNT(*) FILTER (WHERE state = 'COMPLETED') AS completed_count, " +
                "  COALESCE(SUM(worked_minutes) FILTER (WHERE state = 'COMPLETED'), 0) AS total_worked, " +
                "  COUNT(*) FILTER (WHERE state = 'MISSED') AS missed_count, " +
                "  COUNT(*) FILTER (WHERE state = 'OPEN' AND clock_out IS NULL) AS missing_punches " +
                "FROM hr_attendance_records " +
                "WHERE tenant_id = ? AND record_date >= ? AND record_date <= ?"
        );
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        params.add(startDate);
        params.add(endDate);
        if (employmentId != null) {
            sql.append(" AND employment_id = ?");
            params.add(employmentId);
        }
        sql.append(" GROUP BY employment_id ORDER BY employment_id");

        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            UUID empId = UUID.fromString(rs.getString("employment_id"));
            int totalWorked = rs.getInt("total_worked");
            int missedDays = rs.getInt("missed_count");
            int missingPunches = rs.getInt("missing_punches");

            // Count approved leave days overlapping this month
            Integer leaveDays = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(days_count), 0)::int FROM hr_leave_requests " +
                    "WHERE tenant_id = ? AND employment_id = ? AND state = 'APPROVED' " +
                    "AND start_date <= ? AND end_date >= ?",
                    Integer.class, tenantId, empId, endDate, startDate
            );
            if (leaveDays == null) leaveDays = 0;

            String status = missedDays > 0 ? "EXCEPTIONS" : "NORMAL";

            return new HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow(
                    empId,
                    null,  // scheduledDays — not yet available (scheduling domain pending)
                    null,  // scheduledMinutes — not yet available
                    totalWorked,
                    missedDays,
                    leaveDays,
                    0,  // lateOccurrences — requires schedule comparison (pending)
                    0,  // earlyDepartures — requires schedule comparison (pending)
                    missingPunches,
                    status
            );
        }, params.toArray());
    }
}
