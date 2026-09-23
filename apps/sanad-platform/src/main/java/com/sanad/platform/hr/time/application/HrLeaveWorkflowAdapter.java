package com.sanad.platform.hr.time.application;

import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowEntitlementGuard;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Canonical Workflow Y2 adapter for HRM LEAVE approval (Manager → HR).
 *
 * <p>Follows the G1 {@code WorkflowY2OpeningApprovalAdapter} pattern.
 * Uses the central Workflow Engine — HRM builds NO second approval engine.
 *
 * <p>Lifecycle:
 *   startLeaveApproval() → creates WorkflowInstance on START step,
 *     then calls graphExecutionService.advance() to move START → manager_approval.
 *     The Y2 graph activation creates the WorkflowStepInstance + WorkflowApprovalRequest.
 *   findPendingApproval() → finds the pending approval for the current step
 *   approveApproval() → approves via WorkflowApprovalService (Y2 graph auto-advances)
 *   rejectApproval() → rejects via WorkflowApprovalService
 *   cancelIfRunning() → cancels workflow if still RUNNING (not if COMPLETED)
 *
 * <p>Does NOT manually construct WorkflowApprovalRequest records.
 * The canonical Y2 graph creates approvals from the real APPROVAL WorkflowStepInstance.
 */
@Component
public class HrLeaveWorkflowAdapter {

    static final String DEFINITION_CODE = "HR_LEAVE_APPROVAL";
    static final String BUSINESS_ENTITY_TYPE = "LEAVE_REQUEST";
    static final String STEP_START = "submit";
    static final String STEP_MANAGER_APPROVAL = "manager_approval";
    static final String STEP_HR_APPROVAL = "hr_approval";

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowApprovalRequestRepository approvalRepository;
    private final WorkflowApprovalService approvalService;
    private final WorkflowExecutionService executionService;
    private final WorkflowGraphExecutionService graphExecutionService;
    private final WorkflowEntitlementGuard entitlementGuard;

    public HrLeaveWorkflowAdapter(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowApprovalRequestRepository approvalRepository,
            WorkflowApprovalService approvalService,
            WorkflowExecutionService executionService,
            WorkflowGraphExecutionService graphExecutionService,
            WorkflowEntitlementGuard entitlementGuard) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.approvalRepository = approvalRepository;
        this.approvalService = approvalService;
        this.executionService = executionService;
        this.graphExecutionService = graphExecutionService;
        this.entitlementGuard = entitlementGuard;
    }

    /**
     * Start the canonical leave approval workflow.
     * Creates the WorkflowInstance on START, then advances to manager_approval.
     * The Y2 graph activation creates the real WorkflowStepInstance + WorkflowApprovalRequest.
     *
     * Uses deterministic idempotency key: HR_LEAVE_APPROVAL:<tenantId>:<leaveRequestId>
     */
    public UUID startLeaveApproval(UUID tenantId, UUID leaveRequestId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrResolveDefinition(tenantId);

        // Idempotency: check for existing RUNNING instance via persisted linkage
        Optional<WorkflowInstance> existing = instanceRepository
                .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, leaveRequestId).stream()
                .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().id();
        }

        // Deterministic idempotency key
        String idempotencyKey = "HR_LEAVE_APPROVAL:" + tenantId + ":" + leaveRequestId;

        // Start on START step (NOT directly on manager_approval)
        WorkflowInstance instance = WorkflowInstance.startY2(
                tenantId,
                definition.definitionFamilyId(),
                definition.id(),
                definition.version(),
                BUSINESS_ENTITY_TYPE,
                leaveRequestId,
                STEP_START,
                submittedBy,
                leaveRequestId,
                "MANUAL",
                null,
                idempotencyKey,
                null,
                null
        );
        instance = executionService.startWorkflow(instance, submittedBy);

        // Advance from START → manager_approval
        // The Y2 graph activation creates the real WorkflowStepInstance + WorkflowApprovalRequest
        graphExecutionService.advance(tenantId, instance.id(), null, submittedBy);

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
     * The Y2 graph auto-advances after approval.
     */
    public WorkflowApprovalRequest approveApproval(UUID tenantId, UUID approvalRequestId,
                                                     UUID approverId, String comments) {
        return approvalService.approve(tenantId, approvalRequestId, approverId, comments);
    }

    /**
     * Reject a pending WorkflowApprovalRequest via the canonical WorkflowApprovalService.
     * The Y2 graph resolves the rejection.
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

    /**
     * Find or resolve the canonical LEAVE_APPROVAL workflow definition.
     * If an ACTIVE/PUBLISHED definition exists, use it.
     * If not, this throws — the definition must be seeded via a governed migration.
     */
    private WorkflowDefinition findOrResolveDefinition(UUID tenantId) {
        Optional<WorkflowDefinition> existing = definitionRepository.findActiveByCode(tenantId, DEFINITION_CODE);
        if (existing.isPresent()) {
            return existing.get();
        }
        throw new IllegalStateException(
                "HRM_WORKFLOW_DEFINITION_NOT_FOUND: " + DEFINITION_CODE +
                " is not published for tenant " + tenantId +
                " — seed the definition via V20260924_4 migration before using leave approval");
    }
}
