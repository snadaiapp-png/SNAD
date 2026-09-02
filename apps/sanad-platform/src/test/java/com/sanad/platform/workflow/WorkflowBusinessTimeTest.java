package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowBusinessTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 12 — business-time arithmetic over tenant calendars (V3).
 *
 * <p>Proves business hours skip weekends and holidays, that partial-day
 * windows carry remaining duration into later working days, and that a
 * missing calendar fails closed.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowBusinessTimeTest {

    @Autowired
    private WorkflowBusinessTimeService businessTime;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID calendarId;

    // Calendar: Asia/Riyadh, Mon-Fri (ISO 1..5), 09:00-17:00; Friday 2026-08-28 is a holiday.
    private static final Instant WEDNESDAY_1600_RIYADH =
            Instant.parse("2026-08-27T13:00:00Z"); // Thursday 16:00 local

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Business Time', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-bt-" + tenantId.toString().substring(0, 8), now, now);
        calendarId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_business_calendars (
                    id, tenant_id, name, timezone, working_days, working_windows,
                    created_at, updated_at
                ) VALUES (?, ?, 'Standard', 'Asia/Riyadh', '[1,2,3,4,5]',
                          '[{"start":"09:00","end":"17:00"}]', ?, ?)
                """, calendarId, tenantId, now, now);
        jdbc.update("""
                INSERT INTO workflow_calendar_holidays (id, tenant_id, calendar_id, holiday_date, name, created_at)
                VALUES (?, ?, ?, DATE '2026-08-28', 'Fixture Holiday', ?)
                """, UUID.randomUUID(), tenantId, calendarId, now);
    }

    @Test
    void businessHoursSkipWeekendAndHoliday() {
        // Thursday 16:00 local: 1h left today, Friday is a holiday, Sat+Sun
        // weekend, so 7h remain into Monday 09:00-16:00 local (13:00Z).
        var due = businessTime.addBusinessDuration(tenantId, calendarId,
                WEDNESDAY_1600_RIYADH, Duration.ofHours(8));
        assertThat(due).isEqualTo(Instant.parse("2026-08-31T13:00:00Z"));
    }

    @Test
    void durationFittingInsideOneWindowStaysSameDay() {
        // Thursday 16:00 local + 30m -> 16:30 local = 13:30Z same day.
        var due = businessTime.addBusinessDuration(tenantId, calendarId,
                WEDNESDAY_1600_RIYADH, Duration.ofMinutes(30));
        assertThat(due).isEqualTo(Instant.parse("2026-08-27T13:30:00Z"));
    }

    @Test
    void missingCalendarFailsClosed() {
        assertThatThrownBy(() -> businessTime.addBusinessDuration(
                tenantId, UUID.randomUUID(), WEDNESDAY_1600_RIYADH, Duration.ofHours(1)))
                .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
    }
}
