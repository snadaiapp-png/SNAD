package com.sanad.platform.workflow.application;

import com.sanad.platform.hr.domain.HrEmployeeRepository;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import com.sanad.platform.workflow.domain.WorkflowWorkItemRepository;
import com.sanad.platform.workflow.domain.WorkflowVersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for central WorkItems (design decisions C3/L3/N3).
 *
 * <p>Assignment is not authorization: claiming, completing, and reassigning
 * here enforce the optimistic-concurrency and candidacy invariants; the
 * caller-facing command layer (Task 16) additionally revalidates the acting
 * user's actionability and current capabilities server-side before reaching
 * this service.</p>
 */
@Service
public class WorkflowWorkItemService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowWorkItemService.class);

    private final WorkflowAttachmentService attachmentService;
    private final WorkflowWorkItemRepository workItemRepo;
    private final HrEmployeeRepository employeeRepo;
    private final WorkflowNotificationService notificationService;

    public WorkflowWorkItemService(WorkflowWorkItemRepository workItemRepo,
                                   HrEmployeeRepository employeeRepo,
                                   WorkflowNotificationService notificationService,
                                   WorkflowAttachmentService attachmentService) {
        this.workItemRepo = workItemRepo;
        this.employeeRepo = employeeRepo;
        this.notificationService = notificationService;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public WorkflowWorkItem create(WorkflowWorkItem item, List<WorkflowWorkItemCandidate> candidates) {
        var saved = workItemRepo.insert(item);
        if (item.assignmentMode() == WorkflowWorkItem.AssignmentMode.WORK_POOL) {
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalArgumentException("WORK_POOL work items require at least one candidate");
            }
            workItemRepo.insertCandidates(saved.id(), candidates);
        }

        // Task 15 / K3: the durable assignment intent is part of the same
        // transaction that creates the WorkItem. When create() is reached from
        // WorkflowGraphExecutionService, notification insertion therefore joins
        // the authoritative Y2 transition transaction: commit publishes both;
        // rollback publishes neither. Delivery is intentionally separate.
        enqueueTaskAssignedIntents(saved, candidates);

        log.info("WorkItem created: tenant={} id={} type={} mode={} candidates={}",
                saved.tenantId(), saved.id(), saved.type(), saved.assignmentMode(),
                candidates != null ? candidates.size() : 0);
        return saved;
    }

    private void enqueueTaskAssignedIntents(WorkflowWorkItem item,
                                            List<WorkflowWorkItemCandidate> candidates) {
        List<UUID> recipientEmployeeIds = item.assignmentMode() == WorkflowWorkItem.AssignmentMode.DIRECT
                ? List.of(item.assigneeEmployeeId())
                : candidates.stream().map(WorkflowWorkItemCandidate::employeeId).distinct().toList();

        for (UUID employeeId : recipientEmployeeIds) {
            var employee = employeeRepo.findById(item.tenantId(), employeeId).orElse(null);
            if (employee == null || employee.userId() == null) {
                continue;
            }
            UUID recipientUserId = employee.userId();
            String deduplicationKey = "TASK_ASSIGNED:" + item.id() + ":" + recipientUserId;
            notificationService.enqueue(
                    item.tenantId(), "TASK_ASSIGNED", item.workflowInstanceId(), item.id(),
                    recipientUserId, "IN_APP", deduplicationKey);
        }
    }

    @Transactional(readOnly = true)
    public WorkflowWorkItem get(UUID tenantId, UUID workItemId) {
        return workItemRepo.findById(tenantId, workItemId)
                .orElseThrow(() -> new IllegalArgumentException("WorkItem not found: " + workItemId));
    }

    /**
     * Atomic pool claim: exactly one candidate can hold the claimed version.
     */
    @Transactional
    public WorkflowWorkItem claim(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        int updated = workItemRepo.claimAvailableItem(tenantId, workItemId, employeeId, expectedVersion);
        if (updated == 0) {
            throw conflict(tenantId, workItemId, employeeId, expectedVersion, "claim");
        }
        var claimed = workItemRepo.findById(tenantId, workItemId).orElseThrow();
        log.info("WorkItem claimed: tenant={} item={} employee={} version={}",
                tenantId, workItemId, employeeId, claimed.version());
        return claimed;
    }

    @Transactional
    public WorkflowWorkItem release(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        int updated = workItemRepo.releaseClaimedItem(tenantId, workItemId, employeeId, expectedVersion);
        if (updated == 0) {
            throw conflict(tenantId, workItemId, employeeId, expectedVersion, "release");
        }
        return workItemRepo.findById(tenantId, workItemId).orElseThrow();
    }

    @Transactional
    public WorkflowWorkItem complete(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion) {
        int updated = workItemRepo.completeClaimedItem(tenantId, workItemId, employeeId, expectedVersion);
        if (updated == 0) {
            throw conflict(tenantId, workItemId, employeeId, expectedVersion, "complete");
        }
        var completed = workItemRepo.findById(tenantId, workItemId).orElseThrow();
        log.info("WorkItem completed: tenant={} item={} employee={}", tenantId, workItemId, employeeId);
        return completed;
    }

    /**
     * Authorized reassignment to a concrete employee. Cross-tenant assignees
     * fail closed through the tenant-scoped UPDATE predicate.
     */
    @Transactional
    public WorkflowWorkItem reassign(UUID tenantId, UUID workItemId, UUID newAssigneeEmployeeId,
                                     UUID actingEmployeeId, long expectedVersion, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reassignment reason is required");
        }
        int updated = workItemRepo.reassignItem(tenantId, workItemId, newAssigneeEmployeeId, expectedVersion);
        if (updated == 0) {
            throw conflict(tenantId, workItemId, newAssigneeEmployeeId, expectedVersion, "reassign");
        }
        var reassigned = workItemRepo.findById(tenantId, workItemId).orElseThrow();
        log.info("WorkItem reassigned: tenant={} item={} to={} by={} reason={} version={}",
                tenantId, workItemId, newAssigneeEmployeeId, actingEmployeeId, reason,
                reassigned.version());
        // R2.13 reassignment notification: durable intent + journey evidence,
        // deduped per reassignment event (version-bounded key). Delivery is
        // decoupled (dispatcher); a notification failure never rolls back the
        // reassignment.
        try {
            UUID newAssigneeUserId = resolveUserIdForEmployee(tenantId, newAssigneeEmployeeId);
            notificationService.enqueue(tenantId,
                    new WorkflowNotificationService.NotificationIntentRequest(
                            "TASK_REASSIGNED", reassigned.workflowInstanceId(),
                            workItemId, null,
                            newAssigneeUserId, null, null, "IN_APP",
                            "reassign:" + workItemId + ":" + expectedVersion,
                            "Task reassigned to you",
                            "A workflow task has been reassigned to you: " + reason,
                            null, "ar", "HIGH", null,
                            null, workItemId,
                            "reassign:" + workItemId + ":" + expectedVersion,
                            Map.of("workItemId", workItemId.toString(),
                                    "assigneeEmployeeId",
                                    String.valueOf(newAssigneeEmployeeId))));
        } catch (IllegalStateException raced) {
            log.debug("Reassignment notification dedup race for item {}: {}",
                    workItemId, raced.getMessage());
        }
        return reassigned;
    }

    /** Best-effort Employee.id -> User.id mapping for IN_APP delivery. */
    private UUID resolveUserIdForEmployee(UUID tenantId, UUID employeeId) {
        return employeeRepo.findById(tenantId, employeeId)
                .map(com.sanad.platform.hr.domain.HrEmployee::userId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkflowWorkItem> findMyWork(UUID tenantId, UUID employeeId, int limit) {
        return workItemRepo.findMyWork(tenantId, employeeId, Math.min(limit, 200));
    }

    @Transactional(readOnly = true)
    public List<WorkflowWorkItem> findPoolWork(UUID tenantId, UUID employeeId, int limit) {
        return workItemRepo.findPoolWork(tenantId, employeeId, Math.min(limit, 200));
    }

    private WorkflowVersionConflictException conflict(UUID tenantId, UUID workItemId,
                                                      UUID employeeId, long expectedVersion,
                                                      String command) {
        return new WorkflowVersionConflictException(
                "WorkItem " + command + " lost the concurrency race or was inadmissible"
                        + " (tenant=" + tenantId + ", item=" + workItemId
                        + ", employee=" + employeeId + ", expectedVersion=" + expectedVersion + ")");
    }
}
