package com.sanad.platform.crm.platform;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.workflow", name = "enabled", havingValue = "true")
public class CrmTimerDispatcher {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CrmPlatformProperties properties;

    public CrmTimerDispatcher(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            CrmPlatformProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${sanad.crm.platform.workflow.poll-interval:5s}")
    public void dispatchDueTimers() {
        transactions.executeWithoutResult(status -> {
            List<Map<String, Object>> timers = jdbc.queryForList("""
                SELECT id, tenant_id, workflow_instance_id, timer_key, payload
                  FROM crm_platform.workflow_timer
                 WHERE status='SCHEDULED' AND fire_at <= now()
                 ORDER BY fire_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, properties.getWorkflow().getTimerBatchSize());

            for (Map<String, Object> timer : timers) {
                UUID timerId = (UUID) timer.get("id");
                UUID tenantId = (UUID) timer.get("tenant_id");
                UUID instanceId = (UUID) timer.get("workflow_instance_id");
                String timerKey = String.valueOf(timer.get("timer_key"));

                jdbc.update("""
                    INSERT INTO crm_platform.event_outbox
                        (tenant_id, aggregate_type, aggregate_id, event_type,
                         routing_key, payload, headers, correlation_id)
                    VALUES (?, 'WORKFLOW_INSTANCE', ?, 'WorkflowTimerFired',
                            'crm.workflow.timer.fired',
                            jsonb_build_object('timerId', ?, 'workflowInstanceId', ?, 'timerKey', ?, 'payload', ?::jsonb),
                            '{}'::jsonb, ?)
                    """,
                    tenantId, instanceId, timerId, instanceId, timerKey,
                    String.valueOf(timer.get("payload")), instanceId);

                jdbc.update("""
                    UPDATE crm_platform.workflow_timer
                       SET status='FIRED', attempts=attempts+1, updated_at=now()
                     WHERE id=?
                    """, timerId);
            }
        });
    }

    @Scheduled(fixedDelayString = "60s")
    public void enqueueOverdueTasks() {
        jdbc.update("""
            INSERT INTO crm_platform.event_outbox
                (tenant_id, aggregate_type, aggregate_id, event_type, routing_key, payload, headers)
            SELECT task.tenant_id,
                   'WORKFLOW_TASK',
                   task.id,
                   'WorkflowTaskOverdue',
                   'crm.workflow.task.overdue',
                   jsonb_build_object(
                       'taskId', task.id,
                       'workflowInstanceId', task.workflow_instance_id,
                       'taskKey', task.task_key,
                       'priority', task.priority,
                       'dueAt', task.due_at),
                   '{}'::jsonb
              FROM crm_platform.workflow_task task
             WHERE task.status IN ('OPEN','CLAIMED')
               AND task.due_at IS NOT NULL
               AND task.due_at <= now()
               AND NOT EXISTS (
                   SELECT 1
                     FROM crm_platform.event_outbox event
                    WHERE event.aggregate_type='WORKFLOW_TASK'
                      AND event.aggregate_id=task.id
                      AND event.event_type='WorkflowTaskOverdue'
                      AND event.created_at >= now() - interval '1 hour'
               )
            """);
    }
}
