package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.infrastructure.JdbcWorkflowOperationalQueryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Operational read models (design decision AL3): indexed queries over the
 * normalized source tables power My Tasks, My Approvals, pools, definition
 * summaries, instance search, incidents, and monitoring.
 *
 * <p>A read model is never authorization evidence — every command reloads
 * authoritative state and revalidates tenant/employee/user/capability/
 * expectedVersion at execution time. The same snapshot publishes bounded
 * operational gauges (no unbounded label cardinality — no tenant, user,
 * instance, or free-text labels).</p>
 */
@Service
public class WorkflowOperationalQueryService {

    private final JdbcWorkflowOperationalQueryRepository repo;
    private final AtomicLong queueDepth = new AtomicLong();
    private final AtomicLong overdueStepsGauge = new AtomicLong();
    private final AtomicLong overdueApprovalsGauge = new AtomicLong();
    private final AtomicLong openIncidentGauge = new AtomicLong();
    private final AtomicLong openIncidentAgeGauge = new AtomicLong();
    private final AtomicLong inboxLagGauge = new AtomicLong();
    private final AtomicLong outboxLagGauge = new AtomicLong();
    private final AtomicLong stuckJoinsGauge = new AtomicLong();
    private final AtomicLong notificationFailuresGauge = new AtomicLong();
    private final AtomicLong actionRetryGauge = new AtomicLong();
    private final AtomicLong actionFailureGauge = new AtomicLong();

    public WorkflowOperationalQueryService(JdbcWorkflowOperationalQueryRepository repo,
                                           MeterRegistry meterRegistry) {
        this.repo = repo;
        meterRegistry.gauge("workflow_queue_depth", queueDepth);
        meterRegistry.gauge("workflow_overdue_steps", overdueStepsGauge);
        meterRegistry.gauge("workflow_overdue_approvals", overdueApprovalsGauge);
        meterRegistry.gauge("workflow_open_incidents", openIncidentGauge);
        meterRegistry.gauge("workflow_open_incident_age_minutes", openIncidentAgeGauge);
        meterRegistry.gauge("workflow_inbox_lag_seconds", inboxLagGauge);
        meterRegistry.gauge("workflow_outbox_lag_seconds", outboxLagGauge);
        meterRegistry.gauge("workflow_stuck_joins", stuckJoinsGauge);
        meterRegistry.gauge("workflow_notification_failures", notificationFailuresGauge);
        meterRegistry.gauge("workflow_action_retries", actionRetryGauge);
        meterRegistry.gauge("workflow_action_failures", actionFailureGauge);
    }

    public record OperationalTaskRow(
            UUID workItemId,
            String title,
            String status,
            String assignmentMode,
            String type,
            Instant dueAt,
            long version) {}

    public record MonitoringSnapshot(
            int availableWorkItems,
            int overdueSteps,
            int overdueApprovals,
            int openIncidents,
            long openIncidentAgeMinutes,
            long inboxLagSeconds,
            long outboxLagSeconds,
            int stuckJoins,
            int failedNotifications,
            long actionRetryCount,
            long actionFailureCount) {}

    /** Direct and claimed work for one concrete employee, tenant-scoped. */
    @Transactional(readOnly = true)
    public List<OperationalTaskRow> findMyTasks(UUID tenantId, UUID employeeId, int limit) {
        return repo.findMyTasks(tenantId, employeeId, Math.max(1, Math.min(limit, 200)))
                .stream().map(this::toRow).toList();
    }

    /** Pool work where the employee is a persisted candidate, tenant-scoped. */
    @Transactional(readOnly = true)
    public List<OperationalTaskRow> findPoolTasks(UUID tenantId, UUID employeeId, int limit) {
        return repo.findPoolTasks(tenantId, employeeId, Math.max(1, Math.min(limit, 200)))
                .stream().map(this::toRow).toList();
    }

    private OperationalTaskRow toRow(JdbcWorkflowOperationalQueryRepository.TaskRow row) {
        return new OperationalTaskRow(row.workItemId(), row.title(), row.status(),
                row.assignmentMode(), row.type(), row.dueAt(), row.version());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findMyApprovals(UUID tenantId, UUID userId, int limit) {
        return repo.findMyApprovals(tenantId, userId, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> definitionSummaries(UUID tenantId, int limit) {
        return repo.definitionSummaries(tenantId, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchInstances(UUID tenantId, String status, int limit) {
        return repo.searchInstances(tenantId, status, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> openIncidents(UUID tenantId, int limit) {
        return repo.openIncidents(tenantId, Math.max(1, Math.min(limit, 200)));
    }

    /**
     * Operational snapshot for one tenant. Publishes the bounded global
     * gauges as a side effect; metric publication failure must never fail a
     * workflow transaction, so the caller treats this as best-effort.
     */
    @Transactional(readOnly = true)
    public MonitoringSnapshot monitoringSnapshot(UUID tenantId) {
        var openIncidents = repo.openIncidentAggregate(tenantId);
        long retries = repo.countAttemptsByOutcome(tenantId, List.of("FAILED_TRANSIENT"));
        long failures = repo.countAttemptsByOutcome(tenantId, List.of("FAILED_PERMANENT", "TIMED_OUT"));
        MonitoringSnapshot snapshot = new MonitoringSnapshot(
                repo.countByStatus(tenantId, "AVAILABLE"),
                repo.countOverdueSteps(tenantId),
                repo.countOverdueApprovals(tenantId),
                openIncidents.count(),
                openIncidents.oldestAgeMinutes(),
                repo.inboxLagSeconds(tenantId),
                repo.outboxLagSeconds(tenantId),
                repo.stuckJoins(tenantId),
                repo.countNotificationsByStatus(tenantId, "FAILED"),
                retries,
                failures);
        queueDepth.set(snapshot.availableWorkItems());
        overdueStepsGauge.set(snapshot.overdueSteps());
        overdueApprovalsGauge.set(snapshot.overdueApprovals());
        openIncidentGauge.set(snapshot.openIncidents());
        openIncidentAgeGauge.set(snapshot.openIncidentAgeMinutes());
        inboxLagGauge.set(snapshot.inboxLagSeconds());
        outboxLagGauge.set(snapshot.outboxLagSeconds());
        stuckJoinsGauge.set(snapshot.stuckJoins());
        notificationFailuresGauge.set(snapshot.failedNotifications());
        actionRetryGauge.set(snapshot.actionRetryCount());
        actionFailureGauge.set(snapshot.actionFailureCount());
        return snapshot;
    }
}
