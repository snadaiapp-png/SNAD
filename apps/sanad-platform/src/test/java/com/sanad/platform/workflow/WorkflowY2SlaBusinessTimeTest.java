package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowBusinessTimeService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowInstance;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 12 — Y2 SLA policy modes (design decision V3).
 *
 * <p>Proves Y2 step activation resolves the due instant through the pinned
 * SLA policy: BUSINESS_TIME traverses the tenant business calendar via
 * {@link WorkflowBusinessTimeService}, a missing calendar fails closed,
 * WALL_CLOCK keeps legacy-compatible elapsed-hour semantics, and the
 * resolved policy snapshot (mode, calendar, hours) is persisted on the
 * step instance so later definition edits never change historical
 * evidence.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowY2SlaBusinessTimeTest {

    @Autowired
    private WorkflowGraphExecutionService graphExecutionService;

    @Autowired
    private WorkflowBusinessTimeService businessTime;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID definitionId;
    private UUID startStepId;
    private UUID taskStepId;
    private UUID endStepId;
    private UUID instanceId;
    private UUID calendarId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Y2 SLA Modes', ?, 'ACTIVE', ?, ?)",
                tenantId, "y2-sla-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Y2 SLA User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "y2-sla-" + userId.toString().substring(0, 8) + "@test", now, now);
        jdbc.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

        // Pool candidate with the HUMAN_TASK capability so the activated work
        // item resolves an ACTIVE employee (assignment is not authorization).
        UUID linkedUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'SLA Candidate', 'ACTIVE', 'dummy', ?, ?)",
                linkedUserId, tenantId, "y2-sla-cand-" + linkedUserId.toString().substring(0, 8) + "@test", now, now);
        UUID employeeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name, display_name,
                    employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'E-SLA', 'Sla', 'Candidate', 'Sla Candidate',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """, employeeId, tenantId, linkedUserId, now, now);
        UUID adminRole = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'ADMIN', 'Administrator', 'ACTIVE', ?, ?)",
                adminRole, tenantId, now, now);
        jdbc.update("""
                INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                SELECT ?, ?, ?, id, NOW() FROM access_capabilities WHERE code = 'WORKFLOW.TASK_EXECUTE'
                """, UUID.randomUUID(), tenantId, adminRole);
        jdbc.update("""
                INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), tenantId, linkedUserId, adminRole, now, now);

        // Tenant business calendar: Asia/Riyadh, Mon-Fri, 09:00-17:00.
        calendarId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_business_calendars (
                    id, tenant_id, name, timezone, working_days, working_windows,
                    created_at, updated_at
                ) VALUES (?, ?, 'Standard', 'Asia/Riyadh', '[1,2,3,4,5]',
                          '[{"start":"09:00","end":"17:00"}]', ?, ?)
                """, calendarId, tenantId, now, now);
    }

    private void createY2Definition(UUID stepCalendarId, String slaMode, Integer slaHours) {
        definitionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_definitions (
                    id, tenant_id, definition_family_id, code, name, module, version, status,
                    trigger_type, created_by, version_lock, engine_generation, publication_state,
                    schema_version, created_at, updated_at
                ) VALUES (?, ?, ?, 'WF-Y2-SLA', 'Y2 SLA', 'GENERAL', 1, 'ACTIVE',
                          'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, ?, ?)
                """, definitionId, tenantId, definitionId, userId, now, now);
        startStepId = createStep("start", "START", null, null, null, null);
        taskStepId = createStep("review", "HUMAN_TASK", "WORKFLOW.TASK_EXECUTE",
                null, slaMode, stepCalendarId);
        if (slaHours != null) {
            jdbc.update("UPDATE workflow_steps SET sla_hours = ? WHERE id = ?", slaHours, taskStepId);
        }
        endStepId = createStep("end", "END", null, null, null, null);
        createTransition(startStepId, taskStepId, "begin", "SUCCESS");
        createTransition(taskStepId, endStepId, "done", "SUCCESS");

        instanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_instances (
                    id, tenant_id, workflow_definition_id, workflow_version, business_entity_type,
                    business_entity_id, status, current_step_key, started_by, started_at,
                    engine_generation, definition_family_id, definition_version_id,
                    context_json, context_schema_version, version, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'TEST', gen_random_uuid(), 'RUNNING', 'start', ?,
                          ?, 'Y2', ?, ?, CAST('{}' AS jsonb), 1, 0, ?, ?)
                """, instanceId, tenantId, definitionId, userId, now,
                definitionId, definitionId, now, now);
        jdbc.update("""
                INSERT INTO workflow_step_instances (
                    id, tenant_id, workflow_instance_id, workflow_step_id, step_key,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'start', 'PENDING', 0, ?, ?)
                """, UUID.randomUUID(), tenantId, instanceId, startStepId, now, now);
    }

    private UUID createStep(String stepKey, String stepType, String requiredCapability,
                            String configuration, String slaMode, UUID slaCalendarId) {
        UUID stepId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_steps (
                    id, tenant_id, workflow_definition_id, step_key, name, step_type,
                    sequence_order, configuration, required_capability, sla_mode, sla_calendar_id,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, CAST(? AS jsonb), ?, ?, ?, 0, ?, ?)
                """, stepId, tenantId, definitionId, stepKey, stepKey, stepType,
                configuration, requiredCapability,
                slaMode != null ? slaMode : "WALL_CLOCK", slaCalendarId, now, now);
        return stepId;
    }

    private void createTransition(UUID fromStep, UUID toStep, String key, String outcome) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO workflow_step_transitions (
                    id, tenant_id, workflow_definition_id, from_step_id, to_step_id,
                    transition_key, outcome, priority, metadata, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 10, CAST('{}' AS jsonb), ?, ?)
                """, UUID.randomUUID(), tenantId, definitionId, fromStep, toStep, key, outcome, now, now);
    }

    private Map<String, Object> activatedStepInstance(UUID instanceId) {
        return jdbc.queryForMap("""
                SELECT si.due_at, si.sla_mode, si.sla_calendar_id, si.sla_hours
                FROM workflow_step_instances si
                WHERE si.workflow_instance_id = ? AND si.step_key = 'review'
                """, instanceId);
    }

    @Test
    void businessTimeStepResolvesDueThroughPinnedCalendarAndPersistsSnapshot() {
        createY2Definition(calendarId, "BUSINESS_TIME", 8);

        Instant before = Instant.now();
        var advanced = graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId);
        Instant after = Instant.now();

        assertThat(advanced.currentStepKey()).isEqualTo("review");
        var row = activatedStepInstance(instanceId);

        // The persisted due instant equals the business-time oracle for the
        // same pinned calendar and duration (activation instant bounded by
        // the advance call window).
        Instant due = ((Timestamp) row.get("due_at")).toInstant();
        Instant oracleLower = businessTime.addBusinessDuration(
                tenantId, calendarId, before.minus(Duration.ofSeconds(30)), Duration.ofHours(8));
        Instant oracleUpper = businessTime.addBusinessDuration(
                tenantId, calendarId, after.plus(Duration.ofSeconds(30)), Duration.ofHours(8));
        assertThat(due).isBetween(oracleLower, oracleUpper);

        // The due instant lands inside a configured working window on a
        // working, non-holiday day (business-time property).
        ZonedDateTime local = due.atZone(ZoneId.of("Asia/Riyadh"));
        assertThat(local.toLocalTime())
                .isBetween(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0));

        // SLA policy snapshot persisted at activation.
        assertThat(row.get("sla_mode")).isEqualTo("BUSINESS_TIME");
        assertThat(row.get("sla_calendar_id")).isEqualTo(calendarId);
        assertThat(((Number) row.get("sla_hours")).intValue()).isEqualTo(8);
    }

    @Test
    void businessTimeStepWithoutCalendarFailsClosed() {
        createY2Definition(null, "BUSINESS_TIME", 8);

        assertThatThrownBy(() -> graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("calendar");

        // Nothing was activated for the business-time step.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_step_instances WHERE workflow_instance_id = ? AND step_key = 'review'",
                Integer.class, instanceId);
        assertThat(count).isZero();
        // The instance stays RUNNING at its current step (fail-closed, no guess).
        assertThat(jdbc.queryForObject(
                "SELECT status FROM workflow_instances WHERE id = ?", String.class, instanceId))
                .isEqualTo("RUNNING");
    }

    @Test
    void wallClockStepKeepsLegacyDueSemanticsAndPersistsSnapshot() {
        createY2Definition(null, "WALL_CLOCK", 4);

        Instant before = Instant.now();
        var advanced = graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId);
        Instant after = Instant.now();

        assertThat(advanced.currentStepKey()).isEqualTo("review");
        var row = activatedStepInstance(instanceId);
        Instant due = ((Timestamp) row.get("due_at")).toInstant();
        assertThat(due).isBetween(before.plus(Duration.ofHours(4)).minus(Duration.ofMinutes(1)),
                after.plus(Duration.ofHours(4)).plus(Duration.ofMinutes(1)));
        assertThat(row.get("sla_mode")).isEqualTo("WALL_CLOCK");
        assertThat(row.get("sla_calendar_id")).isNull();
        assertThat(((Number) row.get("sla_hours")).intValue()).isEqualTo(4);
    }

    @Test
    void defaultSlaModeIsWallClockWhenStepDeclaresNothing() {
        createY2Definition(null, null, 2);

        var advanced = graphExecutionService.advance(tenantId, instanceId, "SUCCESS", userId);
        assertThat(advanced.currentStepKey()).isEqualTo("review");

        var row = activatedStepInstance(instanceId);
        assertThat(row.get("sla_mode")).isEqualTo("WALL_CLOCK");
        Instant due = ((Timestamp) row.get("due_at")).toInstant();
        assertThat(due).isAfter(Instant.now().plus(Duration.ofHours(2)).minus(Duration.ofMinutes(1)));
    }
}
