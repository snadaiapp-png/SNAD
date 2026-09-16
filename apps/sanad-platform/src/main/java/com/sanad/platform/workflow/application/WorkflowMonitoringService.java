package com.sanad.platform.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * SLA Monitoring Service for the Workflow Engine.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detect workflow step_instances that are IN_PROGRESS past their
 *       {@code due_at} (overdue steps)</li>
 *   <li>Detect workflow approval_requests that are PENDING past their
 *       {@code due_at} (overdue approvals)</li>
 *   <li>Aggregate both into a single {@link #checkAllSlaBreaches(UUID)} call</li>
 * </ul>
 *
 * <p>This service is <strong>idempotent and authoritative-read only</strong>:
 * every SLA scan executes tenant-scoped COUNT queries over the authoritative
 * source tables. These operational counts are observability evidence only,
 * never authorization or command-decision evidence. SLA enforcement mutations belong to a separate
 * command worker that revalidates authoritative state before transition.
 */
@Service
public class WorkflowMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMonitoringService.class);

    private final WorkflowOperationalQueryService operationalQueryService;

    public WorkflowMonitoringService(WorkflowOperationalQueryService operationalQueryService) {
        this.operationalQueryService = operationalQueryService;
    }

    /**
     * Check all SLA breaches for a tenant. Idempotent and read-only.
     *
     * @return total number of overdue items (steps + approvals)
     */
    @Transactional(readOnly = true)
    public int checkAllSlaBreaches(UUID tenantId) {
        int steps = checkOverdueSteps(tenantId);
        int approvals = checkOverdueApprovals(tenantId);
        int total = steps + approvals;
        if (total > 0) {
            log.info("Workflow SLA monitoring for tenant {}: {} breaches ({} steps, {} approvals)",
                    tenantId, total, steps, approvals);
        }
        return total;
    }

    /**
     * Detect IN_PROGRESS step_instances whose {@code due_at} has passed.
     *
     * @return number of overdue step instances
     */
    @Transactional(readOnly = true)
    public int checkOverdueSteps(UUID tenantId) {
        int overdue = operationalQueryService.countOverdueSteps(tenantId);
        if (overdue > 0) {
            log.warn("Tenant {} has {} overdue workflow step_instances", tenantId, overdue);
        }
        return overdue;
    }

    /**
     * Detect PENDING approval_requests whose {@code due_at} has passed.
     *
     * @return number of overdue approval requests
     */
    @Transactional(readOnly = true)
    public int checkOverdueApprovals(UUID tenantId) {
        int overdue = operationalQueryService.countOverdueApprovals(tenantId);
        if (overdue > 0) {
            log.warn("Tenant {} has {} overdue workflow approval_requests", tenantId, overdue);
        }
        return overdue;
    }
}
