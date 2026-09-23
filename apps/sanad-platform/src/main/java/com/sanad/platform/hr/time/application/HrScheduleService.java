package com.sanad.platform.hr.time.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Work Scheduling service.
 *
 * <p>Manages work schedules, schedule versions, and effective-dated
 * employee schedule assignments. Tenant-scoped.
 *
 * <p>Overlap protection: prevents two active primary assignments for the
 * same employment with overlapping effective periods (enforced via
 * unique index uq_hr_schedule_assignments_primary_overlap).
 */
@Service
public class HrScheduleService {

    private final JdbcTemplate jdbc;

    public HrScheduleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(UUID tenantId) {
        return jdbc.query(
                "SELECT id, code, name_ar, name_en, timezone, shift_start, shift_end, " +
                "break_minutes, expected_minutes, is_overnight, state " +
                "FROM hr_work_schedules WHERE tenant_id = ? AND state = 'ACTIVE' ORDER BY code",
                (rs, rowNum) -> new ScheduleResponse(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("name_ar"),
                        rs.getString("name_en"),
                        rs.getString("timezone"),
                        rs.getTime("shift_start").toLocalTime(),
                        rs.getTime("shift_end").toLocalTime(),
                        rs.getInt("break_minutes"),
                        rs.getInt("expected_minutes"),
                        rs.getBoolean("is_overnight"),
                        rs.getString("state")
                ),
                tenantId
        );
    }

    @Transactional
    public UUID createSchedule(UUID tenantId, CreateScheduleRequest request) {
        UUID id = UUID.randomUUID();
        boolean overnight = request.shiftEnd().isBefore(request.shiftStart());
        int expectedMinutes = (int) java.time.Duration.between(request.shiftStart(), request.shiftEnd()).toMinutes();
        if (overnight) expectedMinutes += 24 * 60;
        expectedMinutes -= request.breakMinutes();

        jdbc.update(
                "INSERT INTO hr_work_schedules (id, tenant_id, code, name_ar, name_en, timezone, " +
                "shift_start, shift_end, break_start, break_end, break_minutes, expected_minutes, is_overnight) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, request.code(), request.nameAr(), request.nameEn(), request.timezone(),
                java.sql.Time.valueOf(request.shiftStart()),
                java.sql.Time.valueOf(request.shiftEnd()),
                request.breakStart() != null ? java.sql.Time.valueOf(request.breakStart()) : null,
                request.breakEnd() != null ? java.sql.Time.valueOf(request.breakEnd()) : null,
                request.breakMinutes(), expectedMinutes, overnight
        );
        return id;
    }

    @Transactional
    public UUID assignSchedule(UUID tenantId, AssignScheduleRequest request) {
        UUID id = UUID.randomUUID();
        // The unique index uq_hr_schedule_assignments_primary_overlap will reject
        // overlapping active primary assignments for the same employment.
        jdbc.update(
                "INSERT INTO hr_schedule_assignments (id, tenant_id, employment_id, schedule_id, " +
                "effective_from, effective_to, is_primary) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, request.employmentId(), request.scheduleId(),
                request.effectiveFrom(), request.effectiveTo(), request.isPrimary()
        );
        return id;
    }

    @Transactional(readOnly = true)
    public ScheduleAssignmentResponse getEffectiveSchedule(UUID tenantId, UUID employmentId, LocalDate date) {
        List<ScheduleAssignmentResponse> results = jdbc.query(
                "SELECT sa.id, sa.employment_id, sa.schedule_id, sa.effective_from, sa.effective_to, " +
                "sa.is_primary, sa.state, ws.code, ws.name_ar, ws.name_en, ws.timezone, " +
                "ws.shift_start, ws.shift_end, ws.break_minutes, ws.expected_minutes, ws.is_overnight " +
                "FROM hr_schedule_assignments sa " +
                "JOIN hr_work_schedules ws ON sa.schedule_id = ws.id AND sa.tenant_id = ws.tenant_id " +
                "WHERE sa.tenant_id = ? AND sa.employment_id = ? " +
                "AND sa.state = 'ACTIVE' AND sa.effective_from <= ? " +
                "AND (sa.effective_to IS NULL OR sa.effective_to >= ?) " +
                "ORDER BY sa.is_primary DESC LIMIT 1",
                (rs, rowNum) -> new ScheduleAssignmentResponse(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("employment_id")),
                        UUID.fromString(rs.getString("schedule_id")),
                        rs.getDate("effective_from").toLocalDate(),
                        rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                        rs.getBoolean("is_primary"),
                        rs.getString("state"),
                        rs.getString("code"),
                        rs.getString("name_ar"),
                        rs.getString("name_en"),
                        rs.getString("timezone"),
                        rs.getTime("shift_start").toLocalTime(),
                        rs.getTime("shift_end").toLocalTime(),
                        rs.getInt("break_minutes"),
                        rs.getInt("expected_minutes"),
                        rs.getBoolean("is_overnight")
                ),
                tenantId, employmentId, date, date
        );
        return results.isEmpty() ? null : results.get(0);
    }

    // DTOs
    public record CreateScheduleRequest(
            String code, String nameAr, String nameEn, String timezone,
            java.time.LocalTime shiftStart, java.time.LocalTime shiftEnd,
            java.time.LocalTime breakStart, java.time.LocalTime breakEnd,
            int breakMinutes
    ) {}

    public record AssignScheduleRequest(
            UUID employmentId, UUID scheduleId,
            LocalDate effectiveFrom, LocalDate effectiveTo, boolean isPrimary
    ) {}

    public record ScheduleResponse(
            UUID id, String code, String nameAr, String nameEn, String timezone,
            java.time.LocalTime shiftStart, java.time.LocalTime shiftEnd,
            int breakMinutes, int expectedMinutes, boolean isOvernight, String state
    ) {}

    public record ScheduleAssignmentResponse(
            UUID id, UUID employmentId, UUID scheduleId,
            LocalDate effectiveFrom, LocalDate effectiveTo, boolean isPrimary, String state,
            String scheduleCode, String scheduleNameAr, String scheduleNameEn,
            String timezone, java.time.LocalTime shiftStart, java.time.LocalTime shiftEnd,
            int breakMinutes, int expectedMinutes, boolean isOvernight
    ) {}
}
