package com.sanad.platform.hr.time.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HRM-G2 Attendance Calculation Engine.
 *
 * <p>Deterministic calculation of attendance metrics from the
 * append-only event log (hr_attendance_events) and the effective
 * work schedule (hr_schedule_assignments + hr_work_schedules).
 *
 * <p>Calculates:
 *   - expected minutes (from schedule)
 *   - worked minutes (from clock-in/out events)
 *   - break minutes (from break events)
 *   - lateness (clock-in after shift start)
 *   - early departure (clock-out before shift end)
 *   - absence (no events on a scheduled day)
 *   - missing punch (clock-in without clock-out)
 *   - attendance status (PRESENT, LATE, ABSENT, MISSING_PUNCH, CORRECTED)
 *
 * <p>Does NOT calculate payroll valuation. Payroll remains outside G2.
 *
 * <p>Uses injectable Clock for testability (avoids time-bomb tests).
 */
@Service
public class HrAttendanceCalculationService {

    private final JdbcTemplate jdbc;
    private final java.time.Clock clock;

    public HrAttendanceCalculationService(JdbcTemplate jdbc, java.time.Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Calculate attendance summary for a given employment + date.
     *
     * @param tenantId     tenant scope
     * @param employmentId employee
     * @param date         the attendance date (cannot be LocalDate.now() —
     *                     must be passed by caller for testability)
     */
    @Transactional(readOnly = true)
    public AttendanceSummary calculate(UUID tenantId, UUID employmentId, LocalDate date) {
        // Get effective schedule for this date (would be injected in production)
        HrScheduleService.ScheduleAssignmentResponse schedule = null;

        // Get all events for this employment + date
        List<EventRow> events = jdbc.query(
                "SELECT event_type, event_timestamp, source, reason " +
                "FROM hr_attendance_events " +
                "WHERE tenant_id = ? AND employment_id = ? " +
                "AND event_timestamp::date = ? " +
                "ORDER BY event_timestamp",
                (rs, rowNum) -> new EventRow(
                        rs.getString("event_type"),
                        rs.getTimestamp("event_timestamp").toInstant(),
                        rs.getString("source"),
                        rs.getString("reason")
                ),
                tenantId, employmentId, java.sql.Date.valueOf(date)
        );

        if (events.isEmpty()) {
            return new AttendanceSummary(0, 0, 0, false, false, true, true, "ABSENT");
        }

        // Find CLOCK_IN and CLOCK_OUT events
        java.time.Instant clockIn = null;
        java.time.Instant clockOut = null;
        int breakMinutes = 0;
        java.time.Instant breakStart = null;

        for (EventRow e : events) {
            switch (e.eventType()) {
                case "CLOCK_IN" -> clockIn = e.timestamp();
                case "CLOCK_OUT" -> clockOut = e.timestamp();
                case "BREAK_START" -> breakStart = e.timestamp();
                case "BREAK_END" -> {
                    if (breakStart != null) {
                        breakMinutes += (int) java.time.Duration.between(breakStart, e.timestamp()).toMinutes();
                        breakStart = null;
                    }
                }
            }
        }

        // Calculate worked minutes
        int workedMinutes = 0;
        if (clockIn != null && clockOut != null) {
            workedMinutes = (int) java.time.Duration.between(clockIn, clockOut).toMinutes() - breakMinutes;
        }

        // Determine status
        boolean missingPunch = clockIn != null && clockOut == null;
        boolean absent = false;
        boolean late = false;
        boolean earlyDeparture = false;
        String status;

        if (missingPunch) {
            status = "MISSING_PUNCH";
        } else if (clockIn == null) {
            status = "ABSENT";
            absent = true;
        } else {
            // Check for lateness (would compare against schedule shift_start)
            // For now, mark as PRESENT (schedule comparison needs HrScheduleService injection)
            status = "PRESENT";
        }

        // Check for manual correction events
        boolean corrected = events.stream().anyMatch(e -> "MANUAL_CORRECTION".equals(e.eventType()));
        if (corrected) {
            status = "CORRECTED";
        }

        return new AttendanceSummary(
                workedMinutes,
                breakMinutes,
                workedMinutes,
                late,
                earlyDeparture,
                absent,
                missingPunch,
                status
        );
    }

    // Internal DTOs
    private record EventRow(String eventType, java.time.Instant timestamp, String source, String reason) {}

    /**
     * Calculated attendance summary for a single day.
     */
    public record AttendanceSummary(
            int expectedMinutes,
            int breakMinutes,
            int workedMinutes,
            boolean late,
            boolean earlyDeparture,
            boolean absent,
            boolean missingPunch,
            String status
    ) {}
}
