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
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical Workflow Y2 adapter for HRM LEAVE approval (Manager → HR).
 *
 * <p>Follows the G1 {@code WorkflowY2OpeningApprovalAdapter} pattern exactly.
 * Uses the central Workflow Engine — HRM builds NO second approval engine.
 */
@Component
public class HrLeaveWorkflowAdapter {

    static final String DEFINITION_CODE = "HR_LEAVE_APPROVAL";
    static final String BUSINESS_ENTITY_TYPE = "LEAVE_REQUEST";
    static final String STEP_SUBMIT_KEY = "submit";
    static final String STEP_MANAGER_APPROVAL_KEY = "manager_approval";
    static final String STEP_HR_APPROVAL_KEY = "hr_approval";
    static final String STEP_END_APPROVED_KEY = "end_approved";
    static final String STEP_END_REJECTED_KEY = "end_rejected";
    static final String CAPABILITY_LEAVE_TEAM_APPROVE = "HRM.LEAVE.TEAM_APPROVE";
    static final String CAPABILITY_LEAVE_HR_APPROVE = "HRM.LEAVE.HR_APPROVE";
    static final Integer APPROVAL_SLA_HOURS = 48;

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
     *
     * Idempotency: catches ONLY DuplicateKeyException from the unique constraint
     * uq_workflow_instances_tenant_idemkey. On catch, reloads the existing instance.
     * Does NOT catch generic DataAccessException.
     */
    public UUID startLeaveApproval(UUID tenantId, UUID leaveRequestId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrPublishDefinition(tenantId, submittedBy);

        // Idempotency: check for existing RUNNING instance first
        Optional<WorkflowInstance> existing = instanceRepository
                .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, leaveRequestId).stream()
                .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                .findFirst();
        if (existing.isPresent()) {
            if (STEP_SUBMIT_KEY.equals(existing.get().currentStepKey())) {
                graphExecutionService.advance(tenantId, existing.get().id(), null, submittedBy);
            }
            return existing.get().id();
        }

        String idempotencyKey = "HR_LEAVE_APPROVAL:" + tenantId + ":" + leaveRequestId;

        WorkflowInstance instance = WorkflowInstance.startY2(
                tenantId, definition.definitionFamilyId(), definition.id(), definition.version(),
                BUSINESS_ENTITY_TYPE, leaveRequestId, STEP_SUBMIT_KEY, submittedBy, leaveRequestId,
                "MANUAL", null, idempotencyKey, null, null);

        try {
            instance = executionService.startWorkflow(instance, submittedBy);
        } catch (DuplicateKeyException e) {
            // Concurrent start with same idempotency key — reload existing instance
            Optional<WorkflowInstance> concurrent = instanceRepository
                    .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, leaveRequestId).stream()
                    .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                    .findFirst();
            if (concurrent.isPresent()) {
                if (STEP_SUBMIT_KEY.equals(concurrent.get().currentStepKey())) {
                    graphExecutionService.advance(tenantId, concurrent.get().id(), null, submittedBy);
                }
                return concurrent.get().id();
            }
            throw e; // Re-throw if we can't find the concurrent instance
        }

        graphExecutionService.advance(tenantId, instance.id(), null, submittedBy);
        return instance.id();
    }

    /**
     * Find the pending approval for the CURRENT workflow step.
     * Verifies currentStepKey matches expectedStepKey before returning.
     */
    public Optional<WorkflowApprovalRequest> findPendingApprovalForCurrentStep(
            UUID tenantId, UUID workflowInstanceId, String expectedStepKey) {
        if (workflowInstanceId == null) return Optional.empty();

        // Verify current step
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isEmpty()) return Optional.empty();

        WorkflowInstance loaded = instance.get();
        if (!expectedStepKey.equals(loaded.currentStepKey())) {
            return Optional.empty(); // Wrong step — no actionable approval
        }

        // Find the PENDING approval for this instance
        return approvalRepository.findByInstance(tenantId, workflowInstanceId).stream()
                .filter(r -> r.status() == WorkflowApprovalRequest.Status.PENDING)
                .findFirst();
    }

    /**
     * Get the current step key from the workflow instance.
     */
    public String getCurrentStepKey(UUID tenantId, UUID workflowInstanceId) {
        if (workflowInstanceId == null) return null;
        return instanceRepository.findById(tenantId, workflowInstanceId)
                .map(WorkflowInstance::currentStepKey)
                .orElse(null);
    }

    /**
     * Verify the workflow is RUNNING at the specified step.
     */
    public boolean isRunningAt(UUID tenantId, UUID workflowInstanceId, String stepKey) {
        if (workflowInstanceId == null) return false;
        return instanceRepository.findById(tenantId, workflowInstanceId)
                .map(i -> i.status() == WorkflowInstance.Status.RUNNING && stepKey.equals(i.currentStepKey()))
                .orElse(false);
    }

    /**
     * Verify the workflow is COMPLETED at the specified terminal step.
     */
    public boolean isCompletedAt(UUID tenantId, UUID workflowInstanceId, String terminalStepKey) {
        if (workflowInstanceId == null) return false;
        return instanceRepository.findById(tenantId, workflowInstanceId)
                .map(i -> i.status() == WorkflowInstance.Status.COMPLETED && terminalStepKey.equals(i.currentStepKey()))
                .orElse(false);
    }

    /**
     * Approve a pending approval via canonical WorkflowApprovalService.
     */
    public WorkflowApprovalRequest approveApproval(UUID tenantId, UUID approvalRequestId,
                                                     UUID approverId, long expectedVersion, String comments) {
        return approvalService.approve(tenantId, approvalRequestId, approverId, expectedVersion, comments);
    }

    /**
     * Reject a pending approval via canonical WorkflowApprovalService.
     */
    public WorkflowApprovalRequest rejectApproval(UUID tenantId, UUID approvalRequestId,
                                                    UUID rejecterId, long expectedVersion, String comments) {
        return approvalService.reject(tenantId, approvalRequestId, rejecterId, expectedVersion, comments);
    }

    /**
     * Cancel the workflow if still RUNNING.
     */
    public void cancelIfRunning(UUID tenantId, UUID workflowInstanceId, UUID cancelledBy, String reason) {
        if (workflowInstanceId == null) return;
        instanceRepository.findById(tenantId, workflowInstanceId).ifPresent(i -> {
            if (i.status() == WorkflowInstance.Status.RUNNING) {
                executionService.cancel(tenantId, workflowInstanceId, cancelledBy, reason);
            }
        });
    }

    // ==================== Definition Publication (follows G1 pattern) ====================

    private WorkflowDefinition findOrPublishDefinition(UUID tenantId, UUID actor) {
        UUID familyId = deterministicFamilyId(tenantId);
        Optional<WorkflowDefinition> published = definitionRepository.findPublishedByFamily(tenantId, familyId);
        if (published.isPresent()) {
            return published.get();
        }

        WorkflowDefinition draft = new WorkflowDefinition(
                familyId, tenantId, familyId, DEFINITION_CODE, DEFINITION_NAME,
                "Manager → HR two-step leave approval (HRM G2)",
                DEFINITION_MODULE, 1, WorkflowDefinition.Status.DRAFT,
                WorkflowDefinition.TriggerType.MANUAL, actor, 0L,
                WorkflowDefinition.EngineGeneration.LEGACY, WorkflowDefinition.PublicationState.DRAFT,
                null, null, null, null, 1,
                Instant.now(), Instant.now());
        draft = definitionRepository.save(draft);

        WorkflowStep submit = definitionRepository.saveStep(WorkflowStep.create(tenantId, draft.id(),
                STEP_SUBMIT_KEY, "Leave Submission", WorkflowStep.StepType.START, 1, "{}", null, null, null));
        WorkflowStep managerApproval = definitionRepository.saveStep(WorkflowStep.create(tenantId, draft.id(),
                STEP_MANAGER_APPROVAL_KEY, "Manager Approval", WorkflowStep.StepType.APPROVAL, 2,
                "{\"approvalPolicy\":\"ANY_ONE\"}", APPROVAL_SLA_HOURS, CAPABILITY_LEAVE_TEAM_APPROVE, null));
        WorkflowStep hrApproval = definitionRepository.saveStep(WorkflowStep.create(tenantId, draft.id(),
                STEP_HR_APPROVAL_KEY, "HR Approval", WorkflowStep.StepType.APPROVAL, 3,
                "{\"approvalPolicy\":\"ANY_ONE\"}", APPROVAL_SLA_HOURS, CAPABILITY_LEAVE_HR_APPROVE, null));
        WorkflowStep endApproved = definitionRepository.saveStep(WorkflowStep.create(tenantId, draft.id(),
                STEP_END_APPROVED_KEY, "Leave Approved", WorkflowStep.StepType.END, 4, "{}", null, null, null));
        WorkflowStep endRejected = definitionRepository.saveStep(WorkflowStep.create(tenantId, draft.id(),
                STEP_END_REJECTED_KEY, "Leave Rejected", WorkflowStep.StepType.END, 5, "{}", null, null, null));

        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                submit.id(), managerApproval.id(), "to_manager_approval", "SUCCESS", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                managerApproval.id(), hrApproval.id(), "manager_approved", "APPROVE", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                managerApproval.id(), endRejected.id(), "manager_rejected", "REJECT", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                hrApproval.id(), endApproved.id(), "hr_approved", "APPROVE", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                hrApproval.id(), endRejected.id(), "hr_rejected", "REJECT", null, 10, null));

        String checksum = Integer.toHexString(java.util.Arrays.asList(
                DEFINITION_CODE, STEP_SUBMIT_KEY, STEP_MANAGER_APPROVAL_KEY, STEP_HR_APPROVAL_KEY,
                STEP_END_APPROVED_KEY, STEP_END_REJECTED_KEY).hashCode());
        WorkflowDefinition publishedDefinition = draft.publish(actor, checksum);
        return definitionRepository.save(publishedDefinition);
    }

    private UUID deterministicFamilyId(UUID tenantId) {
        return UUID.nameUUIDFromBytes(
                (tenantId.toString() + "|HR_LEAVE_APPROVAL|v1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
