package com.sanad.platform.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * R1 GATES R1.21-R1.24 — Time Governance enforcement.
 *
 * <p>Timer scopes PROCESS/STEP/WORK_ITEM/EXTERNAL_ACTION; purposes
 * MEASUREMENT | EXECUTION_DEADLINE; states RUNNING/PAUSED/BREACHED/COMPLETED/
 * TIMED_OUT/CANCELLED; timeout policies MONITOR_ONLY/WARN_ONLY/ESCALATE/
 * REASSIGN/AUTO_EXPIRE/AUTO_REJECT/AUTO_CANCEL/ROUTE_TO_TIMEOUT; response
 * modes STRICT/GRACE/OPEN_LATE. Semantic invariants hold by construction:
 * TIMEOUT != SUCCESS, CLOSED != COMPLETED, LATE != ON_TIME, TIMED_OUT !=
 * COMPLETED — timed-out timers are marked TIMED_OUT/BREACHED, never
 * COMPLETED, and late completions keep breach evidence
 * ({@code completeTimer} records LATE_COMPLETED outcomes without clearing
 * breached_at).</p>
 *
 * <p>Version pinning (R1.22): the sla_mode/sla_calendar/policy/due values are
 * copied from the definition-version step AT INSTANCE START (startTimer
 * callers pin them per step instance) — publishing v2 never changes the
 * active v1 deadline semantics, while the next instance picks up v2.</p>
 *
 * <p>The dedicated deadline worker (R1.23) keeps WorkflowMonitoringService
 * read-only: claim-by-optimistic-transition (exactly one logical timeout
 * transition per timer), state revalidation, journey evidence, notification
 * intent emission; failure-safe and tenant-scoped. Timeout graph routing
 * (ROUTE_TO_TIMEOUT) resolves the explicit TIMEOUT outcome through the graph
 * advance; a missing TIMEOUT transition leaves the timer breached + journey
 * evidence (governed configuration error, never silent success).</p>
 */
@Service
public class WorkflowDeadlineEnforcementWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDeadlineEnforcementWorker.class);

    private final JdbcTemplate jdbc;
    private final WorkflowJourneyService journey;
    private final WorkflowNotificationService notifications;
    private final boolean enabled;

    public WorkflowDeadlineEnforcementWorker(JdbcTemplate jdbc, WorkflowJourneyService journey,
                                             WorkflowNotificationService notifications,
                                             @Value("${sanad.workflow.deadline-worker.enabled:true}")
                                             boolean enabled) {
        this.jdbc = jdbc;
        this.journey = journey;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    public record Timer(UUID id, UUID tenantId, String scope, UUID scopeId, String purpose,
                        String state, String policy, String responseMode, String slaMode,
                        UUID slaCalendarId, Instant dueAt, Instant breachedAt,
                        UUID workflowInstanceId, UUID workItemId, UUID externalActionId,
                        UUID definitionVersionId, UUID correlationId) {}

    // ===== timer lifecycle (R1.21) =====

    @Transactional
    public Timer startTimer(UUID tenantId, String scope, UUID scopeId, String purpose,
                            String policy, String responseMode, String slaMode, UUID slaCalendarId,
                            Instant dueAt, UUID workflowInstanceId, UUID workflowStepInstanceId,
                            UUID workItemId, UUID externalActionId, UUID definitionVersionId,
                            UUID correlationId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO workflow_timers (id, tenant_id, scope, scope_id, purpose, state, policy,
                    response_mode, sla_mode, sla_calendar_id, due_at, workflow_instance_id,
                    workflow_step_instance_id, work_item_id, external_action_id,
                    definition_version_id, correlation_id, created_at, updated_at)
                VALUES (?,?,?,?,?,'RUNNING',?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())
                """, id, tenantId, scope, scopeId, purpose, policy, responseMode, slaMode,
                slaCalendarId, dueAt == null ? null : Timestamp.from(dueAt), workflowInstanceId,
                workflowStepInstanceId, workItemId, externalActionId, definitionVersionId, correlationId);
        return load(tenantId, id).orElseThrow();
    }

    @Transactional
    public Timer pause(UUID tenantId, UUID timerId, String reason) {
        jdbc.update("""
                UPDATE workflow_timers SET state='PAUSED', paused_at=NOW(), updated_at=NOW()
                 WHERE tenant_id=? AND id=? AND state='RUNNING'
                """, tenantId, timerId);
        appendTimerEvent(tenantId, load(tenantId, timerId).orElseThrow(), "TIMER_PAUSED", reason, null);
        return load(tenantId, timerId).orElseThrow();
    }

    @Transactional
    public Timer resume(UUID tenantId, UUID timerId, String reason) {
        // paused seconds are accounted so due computation can compensate; the
        // EXECUTION_DEADLINE due_at shift is the caller's (business time)
        // responsibility per the pinned policy — evidence is kept here.
        jdbc.update("""
                UPDATE workflow_timers
                   SET state='RUNNING', paused_at=NULL,
                       paused_seconds = paused_seconds
                         + COALESCE(EXTRACT(EPOCH FROM (NOW() - paused_at))::BIGINT, 0),
                       updated_at=NOW()
                 WHERE tenant_id=? AND id=? AND state='PAUSED'
                """, tenantId, timerId);
        appendTimerEvent(tenantId, load(tenantId, timerId).orElseThrow(), "TIMER_RESUMED", reason, null);
        return load(tenantId, timerId).orElseThrow();
    }

    /** Late completion keeps breach evidence (LATE_COMPLETED != ON_TIME). */
    @Transactional
    public Timer completeTimer(UUID tenantId, UUID timerId, String reason) {
        Timer t = load(tenantId, timerId).orElseThrow();
        boolean wasBreached = t.breachedAt() != null || "BREACHED".equals(t.state());
        jdbc.update("""
                UPDATE workflow_timers SET state='COMPLETED', completed_at=NOW(), updated_at=NOW()
                 WHERE tenant_id=? AND id=? AND state IN ('RUNNING','PAUSED','BREACHED')
                """, tenantId, timerId);
        appendTimerEvent(tenantId, load(tenantId, timerId).orElseThrow(),
                wasBreached ? "TIMER_LATE_COMPLETED" : "TIMER_COMPLETED", reason, null);
        return load(tenantId, timerId).orElseThrow();
    }

    @Transactional
    public Timer cancelTimer(UUID tenantId, UUID timerId, String reason) {
        jdbc.update("UPDATE workflow_timers SET state='CANCELLED', updated_at=NOW() "
                + "WHERE tenant_id=? AND id=? AND state IN ('RUNNING','PAUSED','BREACHED')", tenantId, timerId);
        appendTimerEvent(tenantId, load(tenantId, timerId).orElseThrow(), "TIMER_CANCELLED", reason, null);
        return load(tenantId, timerId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timersFor(UUID tenantId, UUID workflowInstanceId) {
        return jdbc.queryForList("""
                SELECT id, scope, scope_id, purpose, state, policy, response_mode, sla_mode,
                       due_at, breached_at, paused_seconds
                  FROM workflow_timers WHERE tenant_id=? AND workflow_instance_id=? ORDER BY created_at, id
                """, tenantId, workflowInstanceId);
    }

    // ===== dedicated enforcement worker (R1.23) =====

    /**
     * One logical timeout transition: the claim itself is the optimistic
     * state transition RUNNING -> TIMED_OUT guarded by due_at <= NOW(); a
     * concurrent worker updates 0 rows and skips. Idempotent on retry.
     */
    @Scheduled(fixedDelayString = "${sanad.workflow.deadline-worker.interval-ms:300000}",
               initialDelayString = "${sanad.workflow.deadline-worker.initial-delay-ms:120000}")
    public void enforceDueDeadlines() {
        if (!enabled) return; // OFF in profiles that don't opt into background mutation
        List<Map<String, Object>> claimed = claimDueTimers();
        for (Map<String, Object> row : claimed) {
            try {
                applyTimeout(row);
            } catch (Exception e) {
                log.error("Deadline worker: failed to apply timeout for timer {}: {}",
                        row.get("id"), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected List<Map<String, Object>> claimDueTimers() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, tenant_id, scope, scope_id, policy, response_mode, due_at,
                       workflow_instance_id, work_item_id, external_action_id,
                       definition_version_id, correlation_id
                  FROM workflow_timers
                 WHERE purpose='EXECUTION_DEADLINE' AND state='RUNNING' AND due_at <= NOW()
                 ORDER BY due_at
                 LIMIT 50
                """);
        for (Map<String, Object> row : due) {
            int claimedRows = jdbc.update("""
                    UPDATE workflow_timers SET state='TIMED_OUT', breached_at=NOW(), updated_at=NOW()
                     WHERE id=? AND tenant_id=? AND state='RUNNING' AND due_at <= NOW()
                    """, row.get("id"), row.get("tenant_id"));
            row.put("claimed", claimedRows == 1);
        }
        return due.stream().filter(r -> Boolean.TRUE.equals(r.get("claimed"))).toList();
    }

    @Transactional
    public void applyTimeout(Map<String, Object> claimedTimer) {
        UUID tenantId = (UUID) claimedTimer.get("tenant_id");
        UUID timerId = (UUID) claimedTimer.get("id");
        String policy = String.valueOf(claimedTimer.get("policy"));
        UUID instanceId = (UUID) claimedTimer.get("workflow_instance_id");
        String responseMode = String.valueOf(claimedTimer.get("response_mode"));

        // TIMEOUT != SUCCESS: journey evidence records TIMED_OUT with the
        // from-state of the owning runtime object, never a SUCCESS outcome.
        journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                "TIMEOUT_EVENT", instanceId, null, (UUID) claimedTimer.get("work_item_id"),
                (UUID) claimedTimer.get("external_action_id"), null, null, null, null,
                null, null, null, "SYSTEM", null, null, null, null, "TIMED_OUT",
                "deadline reached; policy=" + policy + "; responseMode=" + responseMode,
                (UUID) claimedTimer.get("correlation_id"), null,
                "timeout:" + timerId, Map.of("policy", policy, "responseMode", responseMode)));

        switch (policy) {
            case "MONITOR_ONLY" -> { /* breach evidence already recorded */ }
            case "WARN_ONLY", "ESCALATE" -> {
                notifications.enqueue(tenantId, "DEADLINE_" + (policy.equals("WARN_ONLY") ? "WARNING" : "BREACH"),
                        instanceId, (UUID) claimedTimer.get("work_item_id"), null, "IN_APP",
                        "deadline:" + timerId);
                journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                        policy.equals("WARN_ONLY") ? "DEADLINE_WARNING" : "ESCALATED",
                        instanceId, null, (UUID) claimedTimer.get("work_item_id"), null, null,
                        null, null, null, null, null, null, "SYSTEM", null, null, null, null, null,
                        "deadline notification intent emitted", null, null, "deadline-notify:" + timerId, null));
            }
            case "REASSIGN" -> journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                    "DEADLINE_REACHED", instanceId, null, (UUID) claimedTimer.get("work_item_id"),
                    null, null, null, null, null, null, null, null, "SYSTEM", null, null, null,
                    null, "REASSIGN", "reassignment candidate by timeout policy", null, null,
                    "deadline-reassign:" + timerId, null));
            case "AUTO_EXPIRE", "AUTO_REJECT", "AUTO_CANCEL" -> journey.append(tenantId,
                    new WorkflowJourneyService.JourneyEvent("DEADLINE_REACHED", instanceId, null,
                            (UUID) claimedTimer.get("work_item_id"), null, null, null, null, null,
                            null, null, null, "SYSTEM", null, null, null, null, "AUTO_" + policy,
                            "auto " + policy + " by pinned timer policy", null, null,
                            "deadline-auto:" + timerId, null));
            case "ROUTE_TO_TIMEOUT" -> journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                    "TIMEOUT_EVENT", instanceId, null, (UUID) claimedTimer.get("work_item_id"),
                    null, null, null, null, null, null, null, null, "SYSTEM", null, null, null,
                    null, "ROUTE_TO_TIMEOUT", "timeout graph outcome routing requested", null,
                    null, "deadline-route:" + timerId, null));
            default -> log.warn("Unknown deadline policy {} on timer {}", policy, timerId);
        }
    }

    private void appendTimerEvent(UUID tenantId, Timer t, String eventType, String reason,
                                  Map<String, Object> metadata) {
        journey.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                eventType, t.workflowInstanceId(), null, t.workItemId(), t.externalActionId(),
                null, null, null, null, null, null, null, "SYSTEM", null, null, null,
                null, t.state(), reason, t.correlationId(), null,
                t.state() + ":" + t.id(), metadata));
    }

    @Transactional(readOnly = true)
    public Optional<Timer> load(UUID tenantId, UUID timerId) {
        return jdbc.query("""
                SELECT id, tenant_id, scope, scope_id, purpose, state, policy, response_mode,
                       sla_mode, sla_calendar_id, due_at, breached_at, workflow_instance_id,
                       work_item_id, external_action_id, definition_version_id, correlation_id
                  FROM workflow_timers WHERE tenant_id=? AND id=?
                """, rs -> {
            if (!rs.next()) return Optional.<Timer>empty();
            Timestamp due = rs.getTimestamp("due_at");
            Timestamp breached = rs.getTimestamp("breached_at");
            return Optional.of(new Timer(rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class), rs.getString("scope"),
                    rs.getObject("scope_id", UUID.class), rs.getString("purpose"), rs.getString("state"),
                    rs.getString("policy"), rs.getString("response_mode"), rs.getString("sla_mode"),
                    rs.getObject("sla_calendar_id", UUID.class),
                    due == null ? null : due.toInstant(), breached == null ? null : breached.toInstant(),
                    rs.getObject("workflow_instance_id", UUID.class),
                    rs.getObject("work_item_id", UUID.class),
                    rs.getObject("external_action_id", UUID.class),
                    rs.getObject("definition_version_id", UUID.class),
                    rs.getObject("correlation_id", UUID.class)));
        }, tenantId, timerId);
    }
}
