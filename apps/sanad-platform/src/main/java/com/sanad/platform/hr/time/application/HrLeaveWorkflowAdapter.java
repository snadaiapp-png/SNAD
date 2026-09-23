package com.sanad.platform.hr.time.application;

import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowEntitlementGuard;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical Workflow Y2 adapter for HRM LEAVE approval (Manager → HR).
 *
 * <p>Follows the G1 {@code WorkflowY2OpeningApprovalAdapter} pattern.
 * Uses the central Workflow Engine — HRM builds NO second approval engine.
 *
 * <p>Lifecycle:
 *   startLeaveApproval() → creates WorkflowInstance + Manager WorkflowApprovalRequest
 *   findPendingApproval() → finds the current pending approval request
 *   approveApproval() → approves via WorkflowApprovalService (Y2 graph auto-advances)
 *   rejectApproval() → rejects via WorkflowApprovalService
 *   cancelIfRunning() → cancels workflow if still RUNNING (not if COMPLETED)
 */
@Component
public class HrLeaveWorkflowAdapter {

    static final String DEFINITION_CODE = "HR_LEAVE_APPROVAL";
    static final String BUSINESS_ENTITY_TYPE = "LEAVE_REQUEST";
    static final String STEP_MANAGER_APPROVAL = "manager_approval";
    static final String STEP_HR_APPROVAL = "hr_approval";

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowApprovalRequestRepository approvalRepository;
    private final WorkflowApprovalService approvalService;
    private final WorkflowExecutionService executionService;
    private final WorkflowEntitlementGuard entitlementGuard;

    public HrLeaveWorkflowAdapter(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowApprovalRequestRepository approvalRepository,
            WorkflowApprovalService approvalService,
            WorkflowExecutionService executionService,
            WorkflowEntitlementGuard entitlementGuard) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.approvalRepository = approvalRepository;
        this.approvalService = approvalService;
        this.executionService = executionService;
        this.entitlementGuard = entitlementGuard;
    }

    /**
     * Start the canonical leave approval workflow.
     * Creates the WorkflowInstance + first Manager WorkflowApprovalRequest.
     *
     * Uses deterministic idempotency key: HR_LEAVE_APPROVAL:<tenantId>:<leaveRequestId>
     */
    public UUID startLeaveApproval(UUID tenantId, UUID leaveRequestId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrResolveDefinition(tenantId);

        // Idempotency: check for existing RUNNING instance via persisted linkage
        // (not just findByBusinessEntity which loses terminal instances)
        Optional<WorkflowInstance> existing = instanceRepository
                .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, leaveRequestId).stream()
                .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                .findFirst();
        if (existing.isPresent()) {
            // Verify a Manager approval request exists; create if missing (heal)
            ensureApprovalRequestExists(tenantId, existing.get().id(), submittedBy, STEP_MANAGER_APPROVAL);
            return existing.get().id();
        }

        // Deterministic idempotency key — same logical submit resolves to same workflow
        String idempotencyKey = "HR_LEAVE_APPROVAL:" + tenantId + ":" + leaveRequestId;

        WorkflowInstance instance = WorkflowInstance.startY2(
                tenantId,
                definition.definitionFamilyId(),
                definition.id(),
                definition.version(),
                BUSINESS_ENTITY_TYPE,
                leaveRequestId,
                STEP_MANAGER_APPROVAL,
                submittedBy,
                leaveRequestId,
                "MANUAL",
                null,
                idempotencyKey,
                null,
                null
        );
        instance = executionService.startWorkflow(instance, submittedBy);

        // Create the Manager WorkflowApprovalRequest via the canonical path
        ensureApprovalRequestExists(tenantId, instance.id(), submittedBy, STEP_MANAGER_APPROVAL);

        return instance.id();
    }

    /**
     * Find the pending WorkflowApprovalRequest for a leave request.
     * Uses the persisted workflow_instance_id from the leave row.
     */
    public Optional<WorkflowApprovalRequest> findPendingApproval(UUID tenantId, UUID workflowInstanceId) {
        if (workflowInstanceId == null) return Optional.empty();
        return approvalRepository.findByInstance(tenantId, workflowInstanceId).stream()
                .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING)
                .findFirst();
    }

    /**
     * Approve a pending WorkflowApprovalRequest via the canonical WorkflowApprovalService.
     * The Y2 graph auto-advances after approval — no manual resume/complete needed.
     */
    public WorkflowApprovalRequest approveApproval(UUID tenantId, UUID approvalRequestId,
                                                     UUID approverId, String comments) {
        return approvalService.approve(tenantId, approvalRequestId, approverId, comments);
    }

    /**
     * Reject a pending WorkflowApprovalRequest via the canonical WorkflowApprovalService.
     * The Y2 graph resolves the rejection — no manual cancel needed.
     */
    public WorkflowApprovalRequest rejectApproval(UUID tenantId, UUID approvalRequestId,
                                                    UUID rejecterId, String comments) {
        return approvalService.reject(tenantId, approvalRequestId, rejecterId, comments);
    }

    /**
     * Cancel the workflow if still RUNNING — used for withdraw.
     * Does NOT cancel COMPLETED/CANCELLED instances (forbidden by canonical domain).
     */
    public void cancelIfRunning(UUID tenantId, UUID workflowInstanceId, UUID cancelledBy, String reason) {
        if (workflowInstanceId == null) return;
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isPresent() && instance.get().status() == WorkflowInstance.Status.RUNNING) {
            executionService.cancel(tenantId, workflowInstanceId, cancelledBy, reason);
        }
    }

    /**
     * Check if the workflow is in a terminal state.
     */
    public boolean isTerminal(UUID tenantId, UUID workflowInstanceId) {
        if (workflowInstanceId == null) return true;
        return instanceRepository.findById(tenantId, workflowInstanceId)
                .map(i -> i.status() == WorkflowInstance.Status.COMPLETED ||
                          i.status() == WorkflowInstance.Status.CANCELLED ||
                          i.status() == WorkflowInstance.Status.FAILED)
                .orElse(true);
    }

    // ==================== Internal ====================

    private void ensureApprovalRequestExists(UUID tenantId, UUID workflowInstanceId,
                                              UUID requesterId, String stepKey) {
        List<WorkflowApprovalRequest> existing = approvalRepository.findByInstance(tenantId, workflowInstanceId);
        boolean hasPending = existing.stream()
                .anyMatch(r -> r.status() == WorkflowApprovalRequest.Status.PENDING);
        if (hasPending) return; // Already has a pending approval request

        // Create a new WorkflowApprovalRequest via the canonical service
        WorkflowApprovalRequest request = new WorkflowApprovalRequest(
                UUID.randomUUID(),           // id
                tenantId,                     // tenantId
                workflowInstanceId,           // workflowInstanceId
                null,                         // workflowStepInstanceId (resolved by service)
                null,                         // requestedFromUserId (assigned by policy)
                "MANAGER",                    // requestedFromRole
                requesterId,                  // requestedByUserId (SOD: requester cannot approve)
                null,                         // requestedFromEmployeeId
                WorkflowApprovalRequestRepository.class.isInterface() ? null : null, // approvalPolicy (use default)
                null,                         // selfApprovalPolicy
                null,                         // policySnapshot
                WorkflowApprovalRequest.Status.PENDING, // status
                java.time.Instant.now(),      // requestedAt
                null,                         // dueAt
                null,                         // actedBy
                null,                         // actedAt
                null,                         // decision
                null,                         // comments
                0,                            // version
                java.time.Instant.now(),      // createdAt
                java.time.Instant.now()       // updatedAt
        );
        approvalService.createApproval(request, requesterId);
    }

    private WorkflowDefinition findOrResolveDefinition(UUID tenantId) {
        Optional<WorkflowDefinition> existing = definitionRepository.findActiveByCode(tenantId, DEFINITION_CODE);
        if (existing.isPresent()) {
            return existing.get();
        }
        throw new IllegalStateException(
                "HRM_WORKFLOW_DEFINITION_NOT_FOUND: " + DEFINITION_CODE +
                " is not published for tenant " + tenantId +
                " — seed the definition via a governed migration before using leave approval");
    }
}
