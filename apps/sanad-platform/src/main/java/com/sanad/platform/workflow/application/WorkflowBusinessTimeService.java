package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Business-time arithmetic over tenant calendars (design decision V3).
 *
 * <p>Normalizes to the calendar timezone, advances only through configured
 * working windows on working days, skips weekends and holidays/closures,
 * and persists the final due time as a UTC instant. The calendar reference
 * is pinned by id so later calendar edits never make historical evidence
 * ambiguous.</p>
 */
@Service
public class WorkflowBusinessTimeService {

    private static final int MAX_DAY_LOOKAHEAD = 366 * 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowBusinessTimeService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record CalendarWindow(LocalTime start, LocalTime end) {}

    /**
     * Adds {@code duration} of business time to {@code start} using the pinned
     * calendar version. Sub-minute remainders roll into the next window.
     */
    @Transactional(readOnly = true)
    public Instant addBusinessDuration(UUID tenantId, UUID calendarId, Instant start, Duration duration) {
        var calendar = jdbc.queryForMap("""
                SELECT timezone, working_days, working_windows
                FROM workflow_business_calendars
                WHERE tenant_id = ? AND id = ?
                """, tenantId, calendarId);
        Set<DayOfWeek> workingDays = workingDays(calendar);
        List<CalendarWindow> windows = workingWindows(calendar);
        Set<LocalDate> holidays = new HashSet<>(jdbc.queryForList("""
                SELECT holiday_date FROM workflow_calendar_holidays WHERE calendar_id = ?
                """, LocalDate.class, calendarId));

        ZoneId zone = ZoneId.of((String) calendar.get("timezone"));
        ZonedDateTime cursor = start.atZone(zone);
        Duration remaining = duration;

        for (int day = 0; day < MAX_DAY_LOOKAHEAD; day++) {
            LocalDate date = cursor.toLocalDate();
            if (workingDays.contains(date.getDayOfWeek()) && !holidays.contains(date)) {
                for (CalendarWindow window : windows) {
                    ZonedDateTime windowStart = date.atTime(window.start()).atZone(zone);
                    ZonedDateTime windowEnd = date.atTime(window.end()).atZone(zone);
                    ZonedDateTime segmentStart = cursor.isAfter(windowStart) ? cursor : windowStart;
                    if (!segmentStart.isBefore(windowEnd)) {
                        continue;
                    }
                    Duration available = Duration.between(segmentStart, windowEnd);
                    if (available.compareTo(remaining) >= 0) {
                        return segmentStart.plus(remaining).toInstant();
                    }
                    remaining = remaining.minus(available);
                    cursor = windowEnd;
                }
            }
            cursor = date.plusDays(1).atStartOfDay(zone);
        }
        throw new IllegalStateException(
                "Business duration could not be satisfied within " + MAX_DAY_LOOKAHEAD + " days");
    }

    private Set<DayOfWeek> workingDays(java.util.Map<String, Object> calendar) {
        try {
            JsonNode days = objectMapper.readTree((String) calendar.get("working_days"));
            Set<DayOfWeek> result = new HashSet<>();
            days.forEach(d -> result.add(DayOfWeek.of(d.asInt())));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Calendar working_days is not a valid JSON array", e);
        }
    }

    private List<CalendarWindow> workingWindows(java.util.Map<String, Object> calendar) {
        try {
            JsonNode windows = objectMapper.readTree((String) calendar.get("working_windows"));
            return windows.valueStream()
                    .map(w -> new CalendarWindow(
                            LocalTime.parse(w.get("start").asText()),
                            LocalTime.parse(w.get("end").asText())))
                    .sorted(java.util.Comparator.comparing(CalendarWindow::start))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Calendar working_windows is not a valid JSON array", e);
        }
    }

    /** Timestamp helper kept for callers persisting derived due times. */
    static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
