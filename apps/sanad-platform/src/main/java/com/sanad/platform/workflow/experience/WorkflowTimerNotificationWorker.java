package com.sanad.platform.workflow.experience;

import com.sanad.platform.workflow.application.WorkflowJourneyService;
import com.sanad.platform.workflow.application.WorkflowNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timer-driven notification worker (GATE R2.13 / AD-6).
 *
 * <p>Generates durable notification intents for deadline approach (reminder),
 * warning threshold, breach, timeout, and reassignment events, with dedup
 * per timer/action/event so repeated scheduler/worker scans never spam
 * recipients. This worker is STRICTLY a notifier: it never mutates timer,
 * instance, or work-item state. Deadline enforcement and state transitions
 * remain the exclusive responsibility of the R1 deadline worker
 * (notification worker != deadline-state worker).</p>
 */
@Component
public class WorkflowTimerNotificationWorker {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowTimerNotificationWorker.class);

    private static final int MAX_TENANTS_PER_TICK = 200;
    private static final int SCAN_LIMIT = 200;

    private final JdbcTemplate jdbc;
    private final WorkflowNotificationService notifications;
    private final WorkflowJourneyService journeyService;
    private final boolean enabled;
    private final long reminderWindowHours;
    private final long warningWindowHours;

    public WorkflowTimerNotificationWorker(
            JdbcTemplate jdbc,
            WorkflowNotificationService notifications,
            WorkflowJourneyService journeyService,
            @Value("${sanad.workflow.timer-notification.enabled:true}") boolean enabled,
            @Value("${sanad.workflow.timer-notification.reminder-window-hours:72}")
            long reminderWindowHours,
            @Value("${sanad.workflow.timer-notification.warning-window-hours:24}")
            long warningWindowHours) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.journeyService = journeyService;
        this.enabled = enabled;
        this.reminderWindowHours = Math.max(1, reminderWindowHours);
        this.warningWindowHours = Math.max(1, warningWindowHours);
    }

    @Scheduled(fixedDelayString = "${sanad.workflow.timer-notification.interval-ms:60000}",
               initialDelayString = "${sanad.workflow.timer-notification.initial-delay-ms:60000}")
    public void notifyDueTimers() {
        if (!enabled) {
            return;
        }
        try {
            runScan();
        } catch (Exception e) {
            log.error("Timer notification tick failed: {}", e.getMessage(), e);
        }
    }

    /**
     * One bounded scan across ACTIVE tenants (invokable directly in tests).
     * Returns the number of intents enqueued (dedup collapses repeats).
     */
    @Transactional
    public int runScan() {
        int enqueued = 0;
        List<UUID> tenants = jdbc.queryForList(
                "SELECT id FROM tenants WHERE status = 'ACTIVE' ORDER BY id LIMIT ?",
                UUID.class, MAX_TENANTS_PER_TICK);
        for (UUID tenantId : tenants) {
            try {
                enqueued += scanTenant(tenantId);
                enqueued += scanExpiringExternalActions(tenantId);
            } catch (Exception e) {
                log.error("Timer notification scan failed for tenant {}: {}",
                        tenantId, e.getMessage());
            }
        }
        return enqueued;
    }

    /** Reminder (T-72h), warning (T-24h), breach (due passed) stages. */
    private int scanTenant(UUID tenantId) {
        int enqueued = 0;
        List<Map<String, Object>> timers = jdbc.queryForList("""
                SELECT id, state, policy, due_at, breached_at, workflow_instance_id,
                       work_item_id, external_action_id, correlation_id
                  FROM workflow_timers
                 WHERE tenant_id = ? AND purpose = 'EXECUTION_DEADLINE'
                   AND state IN ('RUNNING', 'BREACHED')
                 ORDER BY due_at NULLS LAST
                 LIMIT ?
                """, tenantId, SCAN_LIMIT);
        for (Map<String, Object> timer : timers) {
            UUID timerId = (UUID) timer.get("id");
            String state = String.valueOf(timer.get("state"));
            java.sql.Timestamp dueAt = (java.sql.Timestamp) timer.get("due_at");
            if (dueAt == null) {
                continue;
            }
            long secondsToDue = java.time.Duration.between(
                    java.time.Instant.now(), dueAt.toInstant()).getSeconds();
            String recipientType = timer.get("work_item_id") != null
                    ? "WORK_ITEM" : "TIMER";
            try {
                if ("BREACHED".equals(state) || secondsToDue <= 0) {
                    enqueued += enqueueStage(tenantId, timer, "DEADLINE_BREACH",
                            "wf-breach:" + timerId, "DEADLINE_BREACH",
                            "A workflow deadline has been breached",
                            recipientType);
                } else if (secondsToDue <= warningWindowSeconds()) {
                    enqueued += enqueueStage(tenantId, timer, "DEADLINE_WARNING",
                            "wf-warn:" + timerId, "DEADLINE_WARNING",
                            "A workflow deadline is approaching",
                            recipientType);
                } else if (secondsToDue <= reminderWindowSeconds()) {
                    enqueued += enqueueStage(tenantId, timer, "REMINDER_SENT",
                            "wf-rem:" + timerId, "DEADLINE_REMINDER",
                            "A workflow deadline reminder",
                            recipientType);
                }
            } catch (IllegalStateException raced) {
                // Tight enqueue race on a dedup key: retry scan lands on the
                // replay path. Never fatal to the scan.
                log.debug("Timer notification dedup race for timer {}: {}",
                        timerId, raced.getMessage());
            }
        }
        return enqueued;
    }

    /** External action expiry reminders — dedup per action (AD-6). */
    private int scanExpiringExternalActions(UUID tenantId) {
        List<Map<String, Object>> actions = jdbc.queryForList("""
                SELECT id, token_expires_at, participant_id, workflow_instance_id
                  FROM workflow_external_actions
                 WHERE tenant_id = ? AND status IN ('PENDING', 'VIEWED')
                   AND token_expires_at IS NOT NULL
                   AND token_expires_at > NOW()
                   AND token_expires_at <= NOW() + MAKE_INTERVAL(hours => 24)
                 LIMIT ?
                """, tenantId, SCAN_LIMIT);
        int enqueued = 0;
        for (Map<String, Object> action : actions) {
            UUID actionId = (UUID) action.get("id");
            try {
                notifications.enqueue(tenantId, new WorkflowNotificationService
                        .NotificationIntentRequest(
                        "EXTERNAL_ACTION_EXPIRY_REMINDER",
                        (UUID) action.get("workflow_instance_id"),
                        null, actionId,
                        null, (UUID) action.get("participant_id"), null,
                        "IN_APP",
                        "wf-ext-rem:" + actionId,
                        "External action expiring soon",
                        "An external action link is about to expire",
                        null, "ar", "HIGH", null,
                        null, actionId, "wf-ext-rem:" + actionId,
                        Map.of("externalActionId", actionId.toString())));
                journeyService.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                        "REMINDER_SENT", (UUID) action.get("workflow_instance_id"),
                        null, null, actionId, null, null, null, null, null, null,
                        null, "SYSTEM", null, null, null, null, null,
                        "External action expiry reminder",
                        actionId, actionId,
                        "ext-reminder:" + actionId, Map.of()));
                enqueued++;
            } catch (IllegalStateException raced) {
                log.debug("External expiry reminder dedup race for {}: {}",
                        actionId, raced.getMessage());
            }
        }
        return enqueued;
    }

    private int enqueueStage(UUID tenantId, Map<String, Object> timer,
                             String journeyEventType, String dedupKey,
                             String notificationEventType, String title,
                             String recipientType) {
        UUID timerId = (UUID) timer.get("id");
        UUID instanceId = (UUID) timer.get("workflow_instance_id");
        UUID workItemId = (UUID) timer.get("work_item_id");
        UUID externalActionId = (UUID) timer.get("external_action_id");
        notifications.enqueue(tenantId, new WorkflowNotificationService
                .NotificationIntentRequest(
                notificationEventType,
                instanceId, workItemId, externalActionId,
                null, null, null,
                "IN_APP",
                dedupKey,
                title,
                title + " (timer " + timerId + ")",
                null, "ar", "HIGH", null,
                null, timerId, dedupKey,
                Map.of("timerId", timerId.toString(),
                        "recipientType", recipientType)));
        journeyService.append(tenantId, new WorkflowJourneyService.JourneyEvent(
                journeyEventType, instanceId, null, workItemId, externalActionId,
                null, null, null, null, null, null, null,
                "SYSTEM", null, null, null, null, null,
                "Timer notification stage " + journeyEventType,
                timerId, timerId,
                journeyEventType.toLowerCase() + ":" + timerId, Map.of()));
        return 1;
    }

    private long reminderWindowSeconds() {
        return Math.max(warningWindowHours, reminderWindowHours) * 3600;
    }

    private long warningWindowSeconds() {
        return warningWindowHours * 3600;
    }
}
