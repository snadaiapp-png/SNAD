package com.sanad.platform.hr.time.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Time & Attendance service.
 *
 * <p>SELF mutation methods receive employmentId only from trusted controller
 * resolution. Every write additionally includes tenant + employment predicates,
 * while PostgreSQL FORCE RLS remains the final isolation layer.</p>
 */
@Service
public class HrTimeAttendanceService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HrTimeAttendanceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public HrTimeAttendanceV2Controller.HrAttendanceRecordResponse clockIn(
            UUID tenantId,
            UUID userId,
            UUID employmentId,
            HrTimeAttendanceV2Controller.ClockInRequest request
    ) {
        List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> existing =
                listAttendance(tenantId, employmentId, request.recordDate(), request.recordDate());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO hr_attendance_records (id, tenant_id, employment_id, record_date, clock_in, source, state) " +
                "VALUES (?, ?, ?, ?, ?, 'MANUAL', 'OPEN')",
                id, tenantId, employmentId, request.recordDate(), Timestamp.from(now)
        );
        return getAttendanceRecord(tenantId, employmentId, id);
    }

    @Transactional
    public HrTimeAttendanceV2Controller.HrAttendanceRecordResponse clockOut(
            UUID tenantId,
            UUID userId,
            UUID employmentId,
            UUID recordId
    ) {
        Instant now = clock.instant();
        Timestamp clockOutTs = Timestamp.from(now);
        int updated = jdbc.update(
                "UPDATE hr_attendance_records SET clock_out = ?, worked_minutes = " +
                "EXTRACT(EPOCH FROM (? - clock_in))::int / 60, state = 'COMPLETED', updated_at = NOW() " +
                "WHERE id = ? AND tenant_id = ? AND employment_id = ? AND state = 'OPEN'",
                clockOutTs, clockOutTs, recordId, tenantId, employmentId
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "HRM_ATTENDANCE_NOT_OWNED_OR_NOT_OPEN: attendance record is unavailable for this employee");
        }
        return getAttendanceRecord(tenantId, employmentId, recordId);
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

    private HrTimeAttendanceV2Controller.HrAttendanceRecordResponse getAttendanceRecord(
            UUID tenantId,
            UUID employmentId,
            UUID id) {
        List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> records = jdbc.query(
                "SELECT id, employment_id, record_date, clock_in, clock_out, break_minutes, worked_minutes, source, state " +
                "FROM hr_attendance_records WHERE id = ? AND tenant_id = ? AND employment_id = ?",
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
                id, tenantId, employmentId
        );
        if (records.isEmpty()) {
            throw new IllegalStateException("HRM_ATTENDANCE_NOT_FOUND_IN_SELF_SCOPE");
        }
        return records.get(0);
    }

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
                    null,
                    null,
                    totalWorked,
                    missedDays,
                    leaveDays,
                    0,
                    0,
                    missingPunches,
                    status
            );
        }, params.toArray());
    }
}
