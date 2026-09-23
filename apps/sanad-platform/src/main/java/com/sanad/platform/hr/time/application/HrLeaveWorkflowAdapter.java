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
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(HrLeaveWorkflowAdapter.class);

    static final String DEFINITION_CODE = "HR_LEAVE_APPROVAL";
    static final String DEFINITION_NAME = "Leave Approval";
    static final String DEFINITION_MODULE = "HRM";
    static final Integer APPROVAL_SLA_HOURS = 48;
    static final String BUSINESS_ENTITY_TYPE = "LEAVE_REQUEST";
    static final String STEP_SUBMIT_KEY = "submit";
    static final String STEP_MANAGER_APPROVAL_KEY = "manager_approval";
    static final String STEP_HR_APPROVAL_KEY = "hr_approval";
    static final String STEP_END_APPROVED_KEY = "end_approved";
    static final String STEP_END_REJECTED_KEY = "end_rejected";
    static final String CAPABILITY_LEAVE_TEAM_APPROVE = "HRM.LEAVE.TEAM_APPROVE";
    static final String CAPABILITY_LEAVE_HR_APPROVE = "HRM.LEAVE.HR_APPROVE";

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowStepInstanceRepository stepInstanceRepository;
    private final WorkflowApprovalRequestRepository approvalRepository;
    private final WorkflowApprovalService approvalService;
    private final WorkflowExecutionService executionService;
    private final WorkflowGraphExecutionService graphExecutionService;
    private final WorkflowEntitlementGuard entitlementGuard;

    public HrLeaveWorkflowAdapter(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowStepInstanceRepository stepInstanceRepository,
            WorkflowApprovalRequestRepository approvalRepository,
            WorkflowApprovalService approvalService,
            WorkflowExecutionService executionService,
            WorkflowGraphExecutionService graphExecutionService,
            WorkflowEntitlementGuard entitlementGuard) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.stepInstanceRepository = stepInstanceRepository;
        this.approvalRepository = approvalRepository;
        this.approvalService = approvalService;
        this.executionService = executionService;
        this.graphExecutionService = graphExecutionService;
        this.entitlementGuard = entitlementGuard;
    }

    /**
     * Start the canonical leave approval workflow.
     *
     * Idempotency: the caller (HrLeaveService.submitLeaveRequest) serializes
     * concurrent submissions via SELECT ... FOR UPDATE on the leave request row.
     * This method does a pre-check for existing RUNNING instances.
     * The DB unique constraint uq_workflow_instances_tenant_idemkey is
     * defense-in-depth — if it fires, the exception propagates (not caught).
     */
    public UUID startLeaveApproval(UUID tenantId, UUID leaveRequestId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrPublishDefinition(tenantId, submittedBy);

        // Pre-check for existing RUNNING instance (caller has already serialized)
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
        instance = executionService.startWorkflow(instance, submittedBy);
        graphExecutionService.advance(tenantId, instance.id(), null, submittedBy);
        return instance.id();
    }

    /**
     * Find the pending approval for the CURRENT workflow step, bound to the
     * exact current {@link WorkflowStepInstance}.
     *
     * <p>Strict resolution algorithm (directive §4):
     * <ol>
     *   <li>A. Load {@link WorkflowInstance} by (tenantId, workflowInstanceId).
     *       Fail closed if missing.</li>
     *   <li>B. Require {@code instance.status == RUNNING}.</li>
     *   <li>C. Require {@code instance.currentStepKey == expectedStepKey}.</li>
     *   <li>D. Resolve the CURRENT {@link WorkflowStepInstance} for the step key
     *       (status PENDING or IN_PROGRESS — never historical COMPLETED).
     *       Fail closed if 0 or &gt;1.</li>
     *   <li>E. Load all {@link WorkflowApprovalRequest}s for the workflow instance.</li>
     *   <li>F. Filter to approvals whose {@code workflowStepInstanceId} equals the
     *       current step instance id AND whose status is PENDING.</li>
     *   <li>G. Require exactly one actionable approval. For the HR leave workflow
     *       the step policy is {@code ANY_ONE} which supports multiple sibling
     *       approvals (one per work-pool candidate). When multiple siblings exist
     *       the policy permits it — return the first (deterministic). For
     *       ambiguous-zero or non-sibling-policy multiple, fail closed.</li>
     * </ol>
     *
     * <p>This bound-resolution prevents the historical defect where
     * {@code approvalRepository.findByInstance().stream().findFirst()}
     * could return an OLD Manager approval after the workflow had already
     * advanced to the HR step (or vice versa). Manager approval from an old
     * step is never reusable at the HR stage because the step-instance id
     * no longer matches.
     */
    public Optional<WorkflowApprovalRequest> findPendingApprovalForCurrentStep(
            UUID tenantId, UUID workflowInstanceId, String expectedStepKey) {
        if (tenantId == null || workflowInstanceId == null || expectedStepKey == null) {
            return Optional.empty();
        }

        // A. Load WorkflowInstance (tenant-scoped)
        Optional<WorkflowInstance> instanceOpt = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instanceOpt.isEmpty()) {
            log.debug("findPendingApprovalForCurrentStep: instance not found tenant={} instance={}",
                    tenantId, workflowInstanceId);
            return Optional.empty();
        }
        WorkflowInstance instance = instanceOpt.get();

        // B. Require RUNNING
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            log.debug("findPendingApprovalForCurrentStep: instance not RUNNING ({}), instance={}",
                    instance.status(), workflowInstanceId);
            return Optional.empty();
        }

        // C. Require currentStepKey == expectedStepKey
        if (!expectedStepKey.equals(instance.currentStepKey())) {
            log.debug("findPendingApprovalForCurrentStep: currentStepKey mismatch expected={} actual={}",
                    expectedStepKey, instance.currentStepKey());
            return Optional.empty();
        }

        // D. Resolve CURRENT WorkflowStepInstance (PENDING or IN_PROGRESS — not historical COMPLETED)
        WorkflowStepInstance currentStepInstance = findCurrentStepInstance(tenantId, workflowInstanceId, expectedStepKey);
        if (currentStepInstance == null) {
            log.debug("findPendingApprovalForCurrentStep: no actionable step instance for key={} instance={}",
                    expectedStepKey, workflowInstanceId);
            return Optional.empty();
        }

        // E + F. Filter approvals by step instance id + PENDING
        List<WorkflowApprovalRequest> approvals = approvalRepository.findByInstance(tenantId, workflowInstanceId);
        List<WorkflowApprovalRequest> actionable = approvals.stream()
                .filter(a -> currentStepInstance.id().equals(a.workflowStepInstanceId()))
                .filter(a -> a.status() == WorkflowApprovalRequest.Status.PENDING)
                .toList();

        // G. Exactly one (or multiple under ANY_ONE sibling-supporting policy)
        if (actionable.isEmpty()) {
            log.debug("findPendingApprovalForCurrentStep: no PENDING approval for stepInstance={} stepKey={}",
                    currentStepInstance.id(), expectedStepKey);
            return Optional.empty();
        }
        if (actionable.size() == 1) {
            return Optional.of(actionable.get(0));
        }
        // Multiple siblings — HR_LEAVE_APPROVAL step policy is ANY_ONE which
        // supports siblings (one approval per work-pool candidate). Return the
        // first; the caller's approve(actorId) enforces SOD, the controller's
        // @RequireCapability enforces authorization. NOTE: production
        // multi-approver scenarios should extend the signature with actorId
        // for proper per-actor binding; this is the safe default.
        log.warn("Multiple PENDING approvals ({}) for step instance {} (key={}) — "
                        + "ANY_ONE policy supports siblings, returning first; "
                        + "extend resolver with actorId for per-actor binding",
                actionable.size(), currentStepInstance.id(), expectedStepKey);
        return Optional.of(actionable.get(0));
    }

    /**
     * Resolve the CURRENT {@link WorkflowStepInstance} for the given workflow
     * instance + step key. "Current" means PENDING or IN_PROGRESS (the
     * actionable state for an approval step); COMPLETED/SKIPPED/FAILED
     * instances are historical and must NOT be selected.
     *
     * <p>Fails closed (returns {@code null}) when:
     * <ul>
     *   <li>0 matching step instances (step not yet activated or already completed)</li>
     *   <li>&gt;1 matching step instances (ambiguous — should never happen for
     *       a non-fork workflow, but defensive)</li>
     * </ul>
     */
    private WorkflowStepInstance findCurrentStepInstance(UUID tenantId, UUID workflowInstanceId, String stepKey) {
        List<WorkflowStepInstance> stepInstances = stepInstanceRepository.findByInstance(workflowInstanceId);
        List<WorkflowStepInstance> currentCandidates = stepInstances.stream()
                .filter(si -> stepKey.equals(si.stepKey()))
                .filter(si -> si.status() == WorkflowStepInstance.Status.PENDING
                        || si.status() == WorkflowStepInstance.Status.IN_PROGRESS)
                .toList();
        if (currentCandidates.size() != 1) {
            log.debug("findCurrentStepInstance: expected exactly one current step instance for key={}, found {} (instance={})",
                    stepKey, currentCandidates.size(), workflowInstanceId);
            return null;
        }
        return currentCandidates.get(0);
    }

    /**
     * Resolve the current {@link WorkflowStepInstance} for the given workflow
     * instance + step key, exposed for callers/tests that need to verify the
     * binding.
     */
    public Optional<WorkflowStepInstance> getCurrentStepInstance(UUID tenantId, UUID workflowInstanceId, String stepKey) {
        if (tenantId == null || workflowInstanceId == null || stepKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findCurrentStepInstance(tenantId, workflowInstanceId, stepKey));
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
