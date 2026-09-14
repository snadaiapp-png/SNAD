package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowIncident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * SLA escalation command worker (design decisions V3/G3/AF3/K3).
 *
 * <p>{@link WorkflowMonitoringService} deliberately stays read-only; this
 * service is the separate command worker it defers enforcement mutations
 * to. For every overdue Y2 work item it produces, idempotently:</p>
 * <ul>
 *   <li>one governed {@code SLA_BREACH} incident (OPEN lifecycle, severity
 *       HIGH) demanding explicit operator disposition — the platform never
 *       silently converts a breach into success; and</li>
 *   <li>one {@code SLA_BREACHED} IN_APP notification intent to the
 *       responsible user (claimant first, then assignee) resolved through
 *       the canonical Employee↔User bridge.</li>
 * </ul>
 *
 * <p>B1 dominance: escalation NEVER reassigns, releases, or otherwise
 * mutates the work item — automatic fallback on human work is forbidden and
 * reassignment is an explicit authorized supervisor command. Repeated scans
 * reuse the open incident and the notification deduplication key, so no
 * incident or notification storm can build up. LEGACY engine breaches are
 * out of scope: only persisted-generation Y2 instances are escalated.</p>
 */
@Service
public class WorkflowSlaEscalationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSlaEscalationService.class);

    /** Safety bound per scan — mirrors the monitoring scan limit. */
    private static final int MAX_ESCALATIONS_PER_SCAN = 200;

    private final JdbcTemplate jdbc;
    private final WorkflowIncidentService incidentService;
    private final WorkflowNotificationService notificationService;

    public WorkflowSlaEscalationService(JdbcTemplate jdbc,
                                        WorkflowIncidentService incidentService,
                                        WorkflowNotificationService notificationService) {
        this.jdbc = jdbc;
        this.incidentService = incidentService;
        this.notificationService = notificationService;
    }

    private record OverdueWorkItem(
            UUID workItemId, UUID workflowInstanceId, UUID workflowStepInstanceId,
            UUID responsibleUserId) {}

    /**
     * Escalates every overdue Y2 work item of the tenant. Idempotent: an
     * item already carrying an open SLA_BREACH incident is skipped.
     *
     * @return number of newly escalated work items
     */
    @Transactional
    public int escalateTenant(UUID tenantId) {
        List<OverdueWorkItem> overdue = jdbc.query("""
                SELECT wi.id, wi.workflow_instance_id, wi.workflow_step_instance_id,
                       he.user_id AS responsible_user_id
                FROM workflow_work_items wi
                JOIN workflow_instances i
                     ON i.id = wi.workflow_instance_id AND i.tenant_id = wi.tenant_id
                LEFT JOIN hr_employees he
                     ON he.tenant_id = wi.tenant_id
                    AND he.id = COALESCE(wi.claimed_by_employee_id, wi.assignee_employee_id)
                WHERE wi.tenant_id = ?
                  AND i.engine_generation = 'Y2'
                  AND wi.status IN ('AVAILABLE', 'CLAIMED', 'IN_PROGRESS')
                  AND wi.sla_due_at IS NOT NULL AND wi.sla_due_at < NOW()
                  AND NOT EXISTS (
                      SELECT 1 FROM workflow_incidents inc
                      WHERE inc.tenant_id = wi.tenant_id
                        AND inc.workflow_instance_id = wi.workflow_instance_id
                        AND inc.step_instance_id = wi.workflow_step_instance_id
                        AND inc.failure_category = 'SLA_BREACH'
                        AND inc.status IN ('OPEN', 'ACKNOWLEDGED'))
                ORDER BY wi.sla_due_at ASC
                LIMIT ?
                """, (rs, n) -> new OverdueWorkItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workflow_instance_id", UUID.class),
                        rs.getObject("workflow_step_instance_id", UUID.class),
                        rs.getObject("responsible_user_id", UUID.class)),
                tenantId, Math.min(MAX_ESCALATIONS_PER_SCAN, 200));

        int escalated = 0;
        for (OverdueWorkItem item : overdue) {
            incidentService.open(tenantId, item.workflowInstanceId(), item.workflowStepInstanceId(),
                    "SLA_ESCALATION", WorkflowIncident.Severity.HIGH, "SLA_BREACH");
            if (item.responsibleUserId() != null) {
                notificationService.enqueue(tenantId, "SLA_BREACHED", item.workflowInstanceId(),
                        item.workItemId(), item.responsibleUserId(), "IN_APP",
                        "sla-breach:" + item.workItemId());
            } else {
                // Fail-closed notification path: the incident still demands
                // operator attention even when no responsible user is resolvable.
                log.warn("SLA escalation without resolvable user: tenant={} workItem={}",
                        tenantId, item.workItemId());
            }
            escalated++;
        }
        if (escalated > 0) {
            log.info("SLA escalation for tenant {}: {} work items escalated", tenantId, escalated);
        }
        return escalated;
    }
}
