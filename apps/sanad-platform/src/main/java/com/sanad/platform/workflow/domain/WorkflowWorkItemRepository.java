package com.sanad.platform.workflow.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowWorkItemRepository {

    WorkflowWorkItem insert(WorkflowWorkItem item);

    Optional<WorkflowWorkItem> findById(UUID tenantId, UUID id);

    /**
     * Atomic work-pool claim (design decision L3). The UPDATE matches at most
     * one row: correct tenant, still AVAILABLE, unmodified version, and the
     * actor is a persisted candidate. Zero affected rows means lost race,
     * stale version, or non-candidate — the caller raises a conflict.
     */
    int claimAvailableItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion);

    /**
     * Releases a claimed item back to the pool. Zero rows means the item is
     * not CLAIMED at the expected version for this tenant.
     */
    int releaseClaimedItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion);

    /**
     * Completes a claimed item; only the current claimant may complete it.
     */
    int completeClaimedItem(UUID tenantId, UUID workItemId, UUID employeeId, long expectedVersion);

    /**
     * Authorized reassignment: moves the item to a new employee assignee with
     * a version bump. Zero rows means stale version or non-reassignable state.
     */
    int reassignItem(UUID tenantId, UUID workItemId, UUID newAssigneeEmployeeId, long expectedVersion);

    /** Items currently assigned to or claimed by one employee (My Tasks). */
    List<WorkflowWorkItem> findMyWork(UUID tenantId, UUID employeeId, int limit);

    /** Pool items where the employee is a persisted candidate. */
    List<WorkflowWorkItem> findPoolWork(UUID tenantId, UUID employeeId, int limit);

    void insertCandidates(UUID workItemId, List<WorkflowWorkItemCandidate> candidates);

    List<WorkflowWorkItemCandidate> findCandidates(UUID tenantId, UUID workItemId);

    boolean isCandidate(UUID tenantId, UUID workItemId, UUID employeeId);
}
