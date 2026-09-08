package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
 * every SLA scan reloads current domain state through the authoritative
 * repositories. Operational read-model snapshots are never authorization or
 * command-decision evidence. SLA enforcement mutations belong to a separate
 * command worker that revalidates authoritative state before transition.
 */
@Service
public class WorkflowMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMonitoringService.class);

    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowApprovalRequestRepository approvalRepo;

    public WorkflowMonitoringService(
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowApprovalRequestRepository approvalRepo) {
        this.stepInstanceRepo = stepInstanceRepo;
        this.approvalRepo = approvalRepo;
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
        var inProgress = stepInstanceRepo.findByTenantAndStatus(
                tenantId, WorkflowStepInstance.Status.IN_PROGRESS, 200);
        var now = Instant.now();
        List<WorkflowStepInstance> overdue = inProgress.stream()
                .filter(si -> si.dueAt() != null && now.isAfter(si.dueAt()))
                .toList();
        if (!overdue.isEmpty()) {
            log.warn("Tenant {} has {} overdue workflow step_instances (first: {})",
                    tenantId, overdue.size(), overdue.get(0).id());
        }
        return overdue.size();
    }

    /**
     * Detect PENDING approval_requests whose {@code due_at} has passed.
     *
     * @return number of overdue approval requests
     */
    @Transactional(readOnly = true)
    public int checkOverdueApprovals(UUID tenantId) {
        var pending = approvalRepo.findByTenantAndStatus(
                tenantId, WorkflowApprovalRequest.Status.PENDING, 200);
        var now = Instant.now();
        List<WorkflowApprovalRequest> overdue = pending.stream()
                .filter(a -> a.dueAt() != null && now.isAfter(a.dueAt()))
                .toList();
        if (!overdue.isEmpty()) {
            log.warn("Tenant {} has {} overdue workflow approval_requests (first: {})",
                    tenantId, overdue.size(), overdue.get(0).id());
        }
        return overdue.size();
    }
}
