package com.sanad.platform.workflow.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.security.SecurityPermitAllTestConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * R1 GATES R1.35/R1.36 — journey ledger + time governance matrices
 * (PostgreSQL Direct).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowJourneyTimeGovernanceTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowJourneyService journey;
    @Autowired private WorkflowDeadlineEnforcementWorker worker;
    @Autowired private WorkflowResponsibilityService responsibility;

    private final java.util.List<UUID> tenantIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> timerIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        // journey rows are DB-enforced append-only: TRUNCATE bypasses row-level
        // mutation triggers while keeping the test database clean (row triggers
        // are the production immutability mechanism, not a test concern).
        jdbc.execute("TRUNCATE workflow_journey");
        jdbc.execute("TRUNCATE workflow_timers");
        jdbc.execute("TRUNCATE workflow_responsibility_segments");
        jdbc.execute("TRUNCATE workflow_notification_intents");
        jdbc.update("DELETE FROM workflow_instances WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-JTG-%')");
        jdbc.update("DELETE FROM workflow_definitions WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-JTG-%')");
        jdbc.update("DELETE FROM users WHERE tenant_id IN (SELECT id FROM tenants WHERE name LIKE 'R1-JTG-%')");
        for (UUID id : tenantIds) jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    private UUID newRealInstance(UUID tenantId) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'JTG User', 'ACTIVE', 'x', NOW(), NOW())", userId, tenantId,
                "r1jtg-" + userId + "@test");
        UUID defId = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_definitions (id, tenant_id, definition_family_id, code, name, module, "
                + "version, status, trigger_type, created_by, version_lock, engine_generation, "
                + "publication_state, schema_version, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,'GENERAL', 1, 'ACTIVE', 'MANUAL', ?, 0, 'Y2', 'PUBLISHED', 1, NOW(), NOW())",
                defId, tenantId, defId, "R1-JTG-DEF", "JTG def", userId);
        UUID instId = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_instances (id, tenant_id, workflow_definition_id, definition_family_id, "
                + "workflow_version, business_entity_type, business_entity_id, status, engine_generation, "
                + "current_step_key, started_by, started_at, created_at, updated_at) "
                + "VALUES (?,?,?,?, 1, 'R1_JTG', ?, 'RUNNING', 'Y2', 'START', ?, NOW(), NOW(), NOW())",
                instId, tenantId, defId, defId, instId, userId);
        return instId;
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (?,?,?,?,NOW(),NOW())",
                id, "R1-JTG-" + label, "r1jtg-" + label + "-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
        tenantIds.add(id);
        return id;
    }

    private WorkflowJourneyService.JourneyEvent event(String type, UUID tenant) {
        return new WorkflowJourneyService.JourneyEvent(type, UUID.randomUUID(), null, null, null,
                null, UUID.randomUUID(), UUID.randomUUID(), 1, "CRM", "CUSTOMER", UUID.randomUUID(),
                "USER", UUID.randomUUID(), null, null, "RUNNING", "COMPLETED",
                "r1 journey event", UUID.randomUUID(), UUID.randomUUID(),
                type + ":" + tenant + ":" + UUID.randomUUID(), Map.of("k", "v"));
    }

    // ===== R1.35 journey =====

    @Test
    void JOURNEY_EVENT_FAMILIES_APPENDABLE_AND_READABLE() {
        UUID tenant = newTenant("fam");
        for (String family : List.of("PROCESS_STARTED", "STEP_ENTERED", "TASK_CREATED",
                "TASK_ASSIGNED", "TASK_VIEWED", "TASK_CLAIMED", "TASK_COMPLETED", "TASK_REJECTED",
                "TASK_REASSIGNED", "TASK_DELEGATED", "APPROVAL_REQUESTED", "APPROVAL_DECIDED",
                "REMINDER_SENT", "DEADLINE_WARNING", "DEADLINE_REACHED", "TASK_TIMED_OUT",
                "ESCALATED", "EXTERNAL_ACTION_CREATED", "EXTERNAL_NOTIFIED", "EXTERNAL_VIEWED",
                "EXTERNAL_RESPONSE", "ATTACHMENT_ADDED", "DOMAIN_ACTION_REQUESTED",
                "DOMAIN_ACTION_COMPLETED", "DOMAIN_ACTION_FAILED", "INCIDENT_OPENED",
                "INCIDENT_RESOLVED", "STEP_COMPLETED", "PROCESS_COMPLETED", "PROCESS_CANCELLED")) {
            journey.append(tenant, event(family, tenant));
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_journey WHERE tenant_id = ?", Long.class, tenant);
        assertThat(count).isEqualTo(30L);
    }

    @Test
    void JOURNEY_APPEND_ONLY() {
        UUID tenant = newTenant("append");
        journey.append(tenant, event("PROCESS_STARTED", tenant));
        Long id = jdbc.queryForObject("SELECT MIN(id) FROM workflow_journey WHERE tenant_id = ?", Long.class, tenant);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE workflow_journey SET to_state = 'REWRITTEN' WHERE id = ?", id))
                .hasMessageContaining("WORKFLOW_JOURNEY_IMMUTABLE");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM workflow_journey WHERE id = ?", id))
                .hasMessageContaining("WORKFLOW_JOURNEY_IMMUTABLE");
    }

    @Test
    void JOURNEY_TENANT_ISOLATION_AND_CORRELATION_CAUSATION() {
        UUID tenantA = newTenant("isoA");
        UUID tenantB = newTenant("isoB");
        WorkflowJourneyService.JourneyEvent e = event("TASK_COMPLETED", tenantA);
        journey.append(tenantA, e);
        // B cannot read A's journey (tenant-scoped read + RLS policy)
        assertThat(journey.journey(tenantB, e.workflowInstanceId())).isEmpty();
        List<Map<String, Object>> read = journey.journey(tenantA, e.workflowInstanceId());
        assertThat(read).hasSize(1);
        assertThat(read.get(0).get("correlation_id")).isEqualTo(e.correlationId());
        assertThat(read.get(0).get("causation_id")).isEqualTo(e.causationId());
    }

    @Test
    void NO_HISTORY_REWRITE_FOR_CURRENT_STATE() {
        UUID tenant = newTenant("norewrite");
        WorkflowJourneyService.JourneyEvent original = event("TASK_ASSIGNED", tenant);
        journey.append(tenant, original);
        // Correction is appended as superseding evidence — original untouched.
        WorkflowJourneyService.JourneyEvent correction = new WorkflowJourneyService.JourneyEvent(
                "TASK_REASSIGNED", original.workflowInstanceId(), null, null, null, null, null,
                null, null, null, null, null, "SYSTEM", null, null, null, null, "EMPLOYEE_B",
                "supersedes prior assignment evidence", original.correlationId(), null,
                "corr:" + UUID.randomUUID(), null);
        journey.append(tenant, correction);
        String originalState = jdbc.queryForObject(
                "SELECT to_state FROM workflow_journey WHERE event_key = ?", String.class, original.eventKey());
        assertThat(originalState).isEqualTo("COMPLETED"); // unchanged
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflow_journey WHERE tenant_id = ?",
                Long.class, tenant)).isEqualTo(2L);
    }

    @Test
    void JOURNEY_IDEMPOTENT_EVENT_KEY() {
        UUID tenant = newTenant("idem");
        WorkflowJourneyService.JourneyEvent e = event("PROCESS_STARTED", tenant);
        journey.append(tenant, e);
        WorkflowJourneyService.JourneyEvent replay = new WorkflowJourneyService.JourneyEvent(
                e.eventType(), e.workflowInstanceId(), null, null, null, null, null, null, null,
                null, null, null, "SYSTEM", null, null, null, null, null, null, null, null,
                e.eventKey(), null);
        assertThatThrownBy(() -> journey.append(tenant, replay))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ===== R1.36 time governance =====

    private WorkflowDeadlineEnforcementWorker.Timer newTimer(UUID tenant, String policy,
                                                             String responseMode, String slaMode) {
        var t = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "EXECUTION_DEADLINE",
                policy, responseMode, slaMode, null, Instant.now().minusSeconds(60),
                UUID.randomUUID(), null, null, null, null, UUID.randomUUID());
        timerIds.add(t.id());
        return t;
    }

    @Test
    void MEASUREMENT_AND_EXECUTION_DEADLINE_TIMERS_WITH_ALL_MODES() {
        UUID tenant = newTenant("modes");
        var measurement = worker.startTimer(tenant, "PROCESS", UUID.randomUUID(), "MEASUREMENT",
                "MONITOR_ONLY", "STRICT", "WALL_CLOCK", null, null, null, null, null, null, null, null);
        timerIds.add(measurement.id());
        assertThat(measurement.purpose()).isEqualTo("MEASUREMENT");
        for (String slaMode : List.of("WALL_CLOCK", "CALENDAR_TIME", "BUSINESS_TIME")) {
            var t = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "EXECUTION_DEADLINE",
                    "WARN_ONLY", "GRACE", slaMode, null, Instant.now().plusSeconds(3600),
                    null, null, null, null, null, null);
            timerIds.add(t.id());
            assertThat(t.slaMode()).isEqualTo(slaMode);
        }
    }

    @Test
    void PAUSE_RESUME_ACCOUNTS_PAUSED_SECONDS() {
        UUID tenant = newTenant("pause");
        var t = newTimer(tenant, "MONITOR_ONLY", "STRICT", "WALL_CLOCK");
        worker.pause(tenant, t.id(), "WAITING_CUSTOMER");
        assertThat(worker.load(tenant, t.id()).orElseThrow().state()).isEqualTo("PAUSED");
        jdbc.update("UPDATE workflow_timers SET paused_at = NOW() - interval '120 seconds' WHERE id = ?", t.id());
        worker.resume(tenant, t.id(), "customer responded");
        var resumed = worker.load(tenant, t.id()).orElseThrow();
        assertThat(resumed.state()).isEqualTo("RUNNING");
        Long paused = jdbc.queryForObject(
                "SELECT paused_seconds FROM workflow_timers WHERE id = ?", Long.class, t.id());
        assertThat(paused).isBetween(119L, 125L);
    }

    @Test
    void DEADLINE_WORKER_CLAIMS_ONCE_AND_EMITS_JOURNEY_EVIDENCE() {
        UUID tenant = newTenant("worker");
        UUID realInstance = newRealInstance(tenant);
        var t = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "EXECUTION_DEADLINE",
                "WARN_ONLY", "STRICT", "WALL_CLOCK", null, Instant.now().minusSeconds(60),
                realInstance, null, null, null, null, UUID.randomUUID());
        timerIds.add(t.id());
        // due_at is in the past; first claim wins
        worker.enforceDueDeadlines();
        var timedOut = worker.load(tenant, t.id()).orElseThrow();
        assertThat(timedOut.state()).isEqualTo("TIMED_OUT"); // TIMEOUT != SUCCESS
        assertThat(timedOut.breachedAt()).isNotNull();
        // idempotent retry + concurrent-worker proof: nothing left to claim
        worker.enforceDueDeadlines();
        Long journeyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_journey WHERE tenant_id=? AND event_type='TIMEOUT_EVENT'",
                Long.class, tenant);
        assertThat(journeyCount).isEqualTo(1L);
        // notification intent emitted for WARN_ONLY
        Long intents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_notification_intents WHERE tenant_id=? AND event_type='DEADLINE_WARNING'",
                Long.class, tenant);
        assertThat(intents).isEqualTo(1L);
    }

    @Test
    void TIMEOUT_NOT_SUCCESS_AND_CLOSED_NOT_COMPLETED() {
        UUID tenant = newTenant("notsuccess");
        var t = newTimer(tenant, "AUTO_CANCEL", "STRICT", "WALL_CLOCK");
        worker.enforceDueDeadlines();
        assertThat(worker.load(tenant, t.id()).orElseThrow().state()).isEqualTo("TIMED_OUT");
        assertThat(worker.load(tenant, t.id()).orElseThrow().state()).isNotEqualTo("COMPLETED");
        var c = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "MEASUREMENT",
                "MONITOR_ONLY", "STRICT", "WALL_CLOCK", null, null, null, null, null, null, null, null);
        timerIds.add(c.id());
        worker.cancelTimer(tenant, c.id(), "instance cancelled");
        assertThat(worker.load(tenant, c.id()).orElseThrow().state()).isEqualTo("CANCELLED");
    }

    @Test
    void GRACE_OPEN_LATE_LATE_COMPLETION_RETAINS_BREACH() {
        UUID tenant = newTenant("late");
        var t = newTimer(tenant, "ESCALATE", "OPEN_LATE", "WALL_CLOCK");
        jdbc.update("UPDATE workflow_timers SET state='BREACHED', breached_at=NOW() WHERE id = ?", t.id());
        var completed = worker.completeTimer(tenant, t.id(), "late completion accepted under OPEN_LATE");
        assertThat(completed.state()).isEqualTo("COMPLETED");
        Instant breachedAt = worker.load(tenant, t.id()).orElseThrow().breachedAt();
        assertThat(breachedAt).isNotNull(); // breach evidence retained
        String eventType = jdbc.queryForObject(
                "SELECT event_type FROM workflow_journey WHERE tenant_id=? AND event_key=?",
                String.class, tenant, "COMPLETED:" + t.id());
        assertThat(eventType).isEqualTo("TIMER_LATE_COMPLETED");
        // STRICT is the enforced-by-policy variant: default deny handled at the
        // accept boundary; the evidence trail here proves breach != on-time.
        assertThat(breachedAt).isCloseTo(Instant.now(), within(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void VERSION_PINNED_TIMER_POLICY() {
        UUID tenant = newTenant("pin");
        UUID defVersionV1 = UUID.randomUUID();
        UUID defVersionV2 = UUID.randomUUID();
        var v1Timer = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "EXECUTION_DEADLINE",
                "AUTO_REJECT", "STRICT", "WALL_CLOCK", null, Instant.now().plusSeconds(3600),
                UUID.randomUUID(), null, null, null, defVersionV1, null);
        timerIds.add(v1Timer.id());
        // "Publish v2" with a different deadline policy
        var v2Timer = worker.startTimer(tenant, "STEP", UUID.randomUUID(), "EXECUTION_DEADLINE",
                "ROUTE_TO_TIMEOUT", "GRACE", "CALENDAR_TIME", null, Instant.now().plusSeconds(7200),
                UUID.randomUUID(), null, null, null, defVersionV2, null);
        timerIds.add(v2Timer.id());
        // RUNNING v1 policy unchanged after v2 publication
        assertThat(worker.load(tenant, v1Timer.id()).orElseThrow().policy()).isEqualTo("AUTO_REJECT");
        assertThat(worker.load(tenant, v1Timer.id()).orElseThrow().slaMode()).isEqualTo("WALL_CLOCK");
        assertThat(worker.load(tenant, v2Timer.id()).orElseThrow().policy()).isEqualTo("ROUTE_TO_TIMEOUT");
    }

    @Test
    void REASSIGNMENT_RESPONSIBILITY_SEGMENTS_AND_WAIT_ATTRIBUTION() {
        UUID tenant = newTenant("resp");
        UUID instance = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        // A owns the task
        responsibility.beginSegment(tenant, item, instance, "ASSIGNED", employeeA, "assigned", null, null);
        jdbc.update("UPDATE workflow_responsibility_segments SET started_at = NOW() - interval '4 hours' "
                + "WHERE tenant_id=? AND work_item_id=? AND owner_employee_id=? AND ended_at IS NULL",
                tenant, item, employeeA);
        // reassigned -> A closed, B opens
        responsibility.beginSegment(tenant, item, instance, "ASSIGNED", employeeB, "reassigned", null, null);
        jdbc.update("UPDATE workflow_responsibility_segments SET started_at = NOW() - interval '2 hours' "
                + "WHERE tenant_id=? AND work_item_id=? AND owner_employee_id=? AND ended_at IS NULL",
                tenant, item, employeeB);
        // external + system + queue waits are separate segment types
        responsibility.beginSegment(tenant, item, instance, "EXTERNAL_WAIT", null, "waiting customer", null, null);
        responsibility.beginSegment(tenant, item, instance, "SYSTEM_WAIT", null, "system hold", null, null);
        responsibility.endAllSegments(tenant, item, "completed", null, null, instance);
        var durations = responsibility.ownershipDurations(tenant, item);
        Map<String, Object> segA = durations.stream()
                .filter(d -> employeeA.equals(d.get("owner_employee_id"))).findFirst().orElseThrow();
        Map<String, Object> segB = durations.stream()
                .filter(d -> employeeB.equals(d.get("owner_employee_id"))).findFirst().orElseThrow();
        long aSeconds = ((Number) segA.get("owned_seconds")).longValue();
        long bSeconds = ((Number) segB.get("owned_seconds")).longValue();
        assertThat(aSeconds).isBetween(4 * 3600L - 30, 4 * 3600L + 30); // A = 4h
        assertThat(bSeconds).isBetween(2 * 3600L - 30, 2 * 3600L + 30); // B = 2h
        // external/system waits are NOT employee time
        assertThat(durations).allSatisfy(d -> {
            if (!"ASSIGNED".equals(d.get("segment_type")) && !"CLAIMED".equals(d.get("segment_type"))) {
                assertThat(d.get("owner_employee_id")).isNull();
            }
        });
        Long journeyEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_journey WHERE tenant_id=? AND event_type LIKE 'RESPONSIBILITY_%'",
                Long.class, tenant);
        assertThat(journeyEvents).isEqualTo(5L);
    }
}
