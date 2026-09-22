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
}
