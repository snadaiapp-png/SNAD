package com.sanad.platform.workflow.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Journey-derived analytics projection service (GATES R2.14/R2.15 / AD-9).
 *
 * <p>Deterministic, rebuildable read models derived ONLY from
 * workflow_journey + workflow_responsibility_segments + workflow_timers +
 * instance/work-item state. Journey remains the authoritative evidence;
 * analytics is derived and can be fully rebuilt from it. Every projected
 * number is traceable to source events (reconciliation gate R2.15);
 * non-computable values are NULL, never invented. Wait categories are
 * NEVER mixed: customer wait (EXTERNAL_WAIT), system wait (SYSTEM_WAIT),
 * queue wait (QUEUE), employee responsibility (ASSIGNED+CLAIMED) each
 * aggregate their own segment type only.</p>
 */
@Service
public class WorkflowAnalyticsProjectionService {

    private final JdbcTemplate jdbc;

    public WorkflowAnalyticsProjectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Deterministic per-instance projection. Idempotent (upsert). */
    @Transactional
    public void projectInstance(UUID tenantId, UUID instanceId) {
        Map<String, Object> instance = jdbc.queryForMap("""
                SELECT status, current_step_key, created_at
                  FROM workflow_instances WHERE tenant_id = ? AND id = ?
                """, tenantId, instanceId);
        Map<String, Object> journeyBounds = jdbc.queryForMap("""
                SELECT COALESCE(MAX(id), 0) AS max_event_id,
                       MIN(occurred_at) FILTER (WHERE event_type = 'PROCESS_STARTED') AS started_at,
                       MAX(occurred_at) FILTER (WHERE event_type IN ('PROCESS_COMPLETED','PROCESS_CANCELLED')) AS completed_at,
                       COUNT(*) FILTER (WHERE event_type = 'TIMEOUT_EVENT') AS timeout_count,
                       COUNT(*) FILTER (WHERE event_type = 'TASK_REASSIGNED') AS reassignment_count,
                       COUNT(*) FILTER (WHERE event_type = 'TIMER_LATE_COMPLETED') AS late_completed_count,
                       MIN(occurred_at) FILTER (WHERE event_type = 'EXTERNAL_RESPONSE') AS first_external_response
                  FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, tenantId, instanceId);
        Map<String, Object> timerStats = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE state IN ('BREACHED','TIMED_OUT')) AS breach_count,
                       MIN(sla_mode) AS sla_mode,
                       MIN(sla_calendar_id::text) AS calendar_id
                  FROM workflow_timers
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                """, tenantId, instanceId);
        Map<String, Object> segmentStats = segmentStats(tenantId, instanceId);
        Map<String, Object> externalLatency = jdbc.queryForMap("""
                SELECT MIN(EXTRACT(EPOCH FROM (r.occurred_at - c.occurred_at)))::BIGINT AS ext_seconds
                  FROM workflow_journey c
                  JOIN workflow_journey r
                    ON r.tenant_id = c.tenant_id
                   AND r.workflow_instance_id = c.workflow_instance_id
                   AND r.event_type = 'EXTERNAL_RESPONSE'
                   AND r.external_action_id = c.external_action_id
                 WHERE c.tenant_id = ? AND c.workflow_instance_id = ?
                   AND c.event_type = 'EXTERNAL_ACTION_CREATED'
                """, tenantId, instanceId);

        java.sql.Timestamp startedAt = (java.sql.Timestamp) journeyBounds.get("started_at");
        if (startedAt == null) {
            startedAt = (java.sql.Timestamp) instance.get("created_at");
        }
        java.sql.Timestamp completedAt =
                (java.sql.Timestamp) journeyBounds.get("completed_at");
        Long processDuration = startedAt != null && completedAt != null
                ? Long.valueOf((long) (completedAt.getTime() - startedAt.getTime()) / 1000)
                : null;
        Long extSeconds = externalLatency.get("ext_seconds") == null
                ? null : ((Number) externalLatency.get("ext_seconds")).longValue();
        Integer breachCount = ((Number) timerStats.get("breach_count")).intValue();
        Integer timeoutCount = ((Number) journeyBounds.get("timeout_count")).intValue();
        Integer reassignmentCount =
                ((Number) journeyBounds.get("reassignment_count")).intValue();
        boolean lateCompleted =
                ((Number) journeyBounds.get("late_completed_count")).intValue() > 0;
        String status = String.valueOf(instance.get("status"));
        Long customerWait = seconds((Number) segmentStats.get("customer_wait"));
        Long systemWait = seconds((Number) segmentStats.get("system_wait"));
        Long queueWait = seconds((Number) segmentStats.get("queue_wait"));
        Long employeeResp = seconds((Number) segmentStats.get("employee_resp"));
        Long firstResponse = firstResponseSeconds(tenantId, instanceId, startedAt);
        String calendarIdText = (String) timerStats.get("calendar_id");
        UUID calendarId = calendarIdText == null ? null : UUID.fromString(calendarIdText);

        jdbc.update("""
                INSERT INTO workflow_analytics_process_facts (
                    id, tenant_id, workflow_instance_id, definition_id,
                    definition_family_id, definition_version, source_module,
                    source_entity_type, source_entity_id, status, started_at,
                    completed_at, process_duration_seconds, sla_breach_count,
                    sla_compliant, late_completed, timeout_count,
                    reassignment_count, first_response_seconds,
                    external_response_seconds, customer_wait_seconds,
                    system_wait_seconds, queue_wait_seconds,
                    employee_responsibility_seconds, calendar_id, sla_mode,
                    derived_at, derived_from_event_id, created_at, updated_at)
                SELECT ?, ?, i.id, i.workflow_definition_id, d.definition_family_id,
                       i.workflow_version, wi.source_module, wi.source_entity_type,
                       wi.source_entity_id, i.status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       ?, ?, ?, ?, ?, ?, NOW(), ?, NOW(), NOW()
                  FROM workflow_instances i
                  LEFT JOIN workflow_definitions d
                    ON d.tenant_id = i.tenant_id AND d.id = i.workflow_definition_id
                  LEFT JOIN LATERAL (
                       SELECT w.source_module, w.source_entity_type,
                              w.source_entity_id
                         FROM workflow_work_items w
                        WHERE w.tenant_id = i.tenant_id
                          AND w.workflow_instance_id = i.id
                        ORDER BY w.created_at
                        LIMIT 1) wi ON TRUE
                 WHERE i.tenant_id = ? AND i.id = ?
                ON CONFLICT (tenant_id, workflow_instance_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    started_at = EXCLUDED.started_at,
                    completed_at = EXCLUDED.completed_at,
                    process_duration_seconds = EXCLUDED.process_duration_seconds,
                    sla_breach_count = EXCLUDED.sla_breach_count,
                    sla_compliant = EXCLUDED.sla_compliant,
                    late_completed = EXCLUDED.late_completed,
                    timeout_count = EXCLUDED.timeout_count,
                    reassignment_count = EXCLUDED.reassignment_count,
                    first_response_seconds = EXCLUDED.first_response_seconds,
                    external_response_seconds = EXCLUDED.external_response_seconds,
                    customer_wait_seconds = EXCLUDED.customer_wait_seconds,
                    system_wait_seconds = EXCLUDED.system_wait_seconds,
                    queue_wait_seconds = EXCLUDED.queue_wait_seconds,
                    employee_responsibility_seconds = EXCLUDED.employee_responsibility_seconds,
                    calendar_id = EXCLUDED.calendar_id,
                    sla_mode = EXCLUDED.sla_mode,
                    derived_at = NOW(),
                    derived_from_event_id = EXCLUDED.derived_from_event_id,
                    projection_revision = workflow_analytics_process_facts.projection_revision + 1,
                    updated_at = NOW()
                """,
                UUID.randomUUID(), tenantId,
                startedAt, completedAt, processDuration,
                breachCount,
                completedAt != null && breachCount == 0,
                lateCompleted,
                timeoutCount, reassignmentCount,
                firstResponse, extSeconds,
                customerWait, systemWait, queueWait, employeeResp,
                calendarId,
                timerStats.get("sla_mode") == null ? null
                        : String.valueOf(timerStats.get("sla_mode")),
                ((Number) journeyBounds.get("max_event_id")).longValue(),
                tenantId, instanceId);
        projectStepFacts(tenantId, instanceId);
    }

    /** Per-step deterministic projection for one instance (idempotent). */
    private void projectStepFacts(UUID tenantId, UUID instanceId) {
        List<Map<String, Object>> steps = jdbc.queryForList("""
                SELECT si.id AS step_instance_id, si.step_key, si.status,
                       si.created_at AS entered_at, si.completed_at,
                       s.step_type
                  FROM workflow_step_instances si
                  LEFT JOIN workflow_steps s
                    ON s.tenant_id = ? AND s.id = si.workflow_step_id
                 WHERE si.tenant_id = ? AND si.workflow_instance_id = ?
                """, tenantId, tenantId, instanceId);
        for (Map<String, Object> step : steps) {
            UUID stepInstanceId = (UUID) step.get("step_instance_id");
            Map<String, Object> segmentStats = stepSegmentStats(tenantId, stepInstanceId);
            Map<String, Object> workItem = jdbc.queryForMap("""
                    SELECT id, assignee_employee_id, status,
                           completed_at, sla_due_at
                      FROM workflow_work_items
                     WHERE tenant_id = ? AND workflow_step_instance_id = ?
                     ORDER BY created_at LIMIT 1
                    """, tenantId, stepInstanceId);
            Long duration = step.get("entered_at") != null && step.get("completed_at") != null
                    ? Long.valueOf((((java.sql.Timestamp) step.get("completed_at")).getTime()
                        - ((java.sql.Timestamp) step.get("entered_at")).getTime()) / 1000)
                    : null;
            boolean slaBreached = workItem.get("sla_due_at") != null
                    && workItem.get("completed_at") != null
                    && ((java.sql.Timestamp) workItem.get("completed_at"))
                            .after((java.sql.Timestamp) workItem.get("sla_due_at"));
            jdbc.update("""
                    INSERT INTO workflow_analytics_step_facts (
                        id, tenant_id, workflow_instance_id, workflow_step_instance_id,
                        step_key, step_type, work_item_id, owner_employee_id, status,
                        entered_at, completed_at, step_duration_seconds,
                        queue_wait_seconds, employee_responsibility_seconds,
                        customer_wait_seconds, system_wait_seconds, sla_breached,
                        late_completed, timed_out, external_step,
                        derived_at, derived_from_event_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE,
                            NOW(), 0, NOW(), NOW())
                    ON CONFLICT (tenant_id, workflow_step_instance_id) DO UPDATE SET
                        step_key = EXCLUDED.step_key,
                        step_type = EXCLUDED.step_type,
                        work_item_id = EXCLUDED.work_item_id,
                        owner_employee_id = EXCLUDED.owner_employee_id,
                        status = EXCLUDED.status,
                        entered_at = EXCLUDED.entered_at,
                        completed_at = EXCLUDED.completed_at,
                        step_duration_seconds = EXCLUDED.step_duration_seconds,
                        queue_wait_seconds = EXCLUDED.queue_wait_seconds,
                        employee_responsibility_seconds = EXCLUDED.employee_responsibility_seconds,
                        customer_wait_seconds = EXCLUDED.customer_wait_seconds,
                        system_wait_seconds = EXCLUDED.system_wait_seconds,
                        sla_breached = EXCLUDED.sla_breached,
                        late_completed = EXCLUDED.late_completed,
                        timed_out = EXCLUDED.timed_out,
                        derived_at = NOW(),
                        projection_revision = workflow_analytics_step_facts.projection_revision + 1,
                        updated_at = NOW()
                    """,
                    UUID.randomUUID(), tenantId, instanceId, stepInstanceId,
                    (String) step.get("step_key"), (String) step.get("step_type"),
                    (UUID) workItem.get("id"),
                    (UUID) workItem.get("assignee_employee_id"),
                    (String) step.get("status"),
                    (java.sql.Timestamp) step.get("entered_at"),
                    (java.sql.Timestamp) step.get("completed_at"),
                    duration,
                    seconds((Number) segmentStats.get("queue_wait")),
                    seconds((Number) segmentStats.get("employee_resp")),
                    seconds((Number) segmentStats.get("customer_wait")),
                    seconds((Number) segmentStats.get("system_wait")),
                    slaBreached,
                    slaBreached && "COMPLETED".equals(workItem.get("status")),
                    false);
        }
    }

    /** Instance-level wait/responsibility aggregation (bounded, set-based). */
    private Map<String, Object> segmentStats(UUID tenantId, UUID instanceId) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'EXTERNAL_WAIT'), 0) AS customer_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'SYSTEM_WAIT'), 0) AS system_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'QUEUE'), 0) AS queue_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type IN ('ASSIGNED','CLAIMED')), 0) AS employee_resp
                  FROM workflow_responsibility_segments seg
                 WHERE seg.tenant_id = ? AND seg.work_item_id IN (
                       SELECT id FROM workflow_work_items
                        WHERE tenant_id = ? AND workflow_instance_id = ?)
                """, tenantId, tenantId, instanceId);
    }

    private Map<String, Object> stepSegmentStats(UUID tenantId, UUID stepInstanceId) {
        return jdbc.queryForMap("""
                SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'EXTERNAL_WAIT'), 0) AS customer_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'SYSTEM_WAIT'), 0) AS system_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type = 'QUEUE'), 0) AS queue_wait,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(seg.ended_at, NOW()) - seg.started_at))::BIGINT)
                         FILTER (WHERE seg.segment_type IN ('ASSIGNED','CLAIMED')), 0) AS employee_resp
                  FROM workflow_responsibility_segments seg
                 WHERE seg.tenant_id = ? AND seg.work_item_id IN (
                       SELECT id FROM workflow_work_items
                        WHERE tenant_id = ? AND workflow_step_instance_id = ?)
                """, tenantId, tenantId, stepInstanceId);
    }

    /** Full deterministic rebuild for one tenant in bounded batches. */
    @Transactional
    public int rebuildTenant(UUID tenantId, int limit) {
        List<UUID> instances = jdbc.queryForList("""
                SELECT id FROM workflow_instances WHERE tenant_id = ?
                 ORDER BY updated_at DESC LIMIT ?
                """, UUID.class, tenantId, Math.min(limit, 500));
        for (UUID instanceId : instances) {
            projectInstance(tenantId, instanceId);
        }
        return instances.size();
    }

    private Long firstResponseSeconds(UUID tenantId, UUID instanceId,
                                      java.sql.Timestamp startedAt) {
        if (startedAt == null) {
            return null;
        }
        List<java.sql.Timestamp> first = jdbc.queryForList("""
                SELECT MIN(occurred_at) AS first_view
                  FROM workflow_journey
                 WHERE tenant_id = ? AND workflow_instance_id = ?
                   AND event_type IN ('TASK_VIEWED','EXTERNAL_VIEWED')
                """, java.sql.Timestamp.class, tenantId, instanceId);
        if (first.isEmpty() || first.get(0) == null) {
            return null;
        }
        return Long.valueOf((first.get(0).getTime() - startedAt.getTime()) / 1000);
    }

    private static Long seconds(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
