package com.sanad.platform.hr.recruitment.infrastructure;

import com.sanad.platform.hr.recruitment.application.HireApprovalWorkflowPort;
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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow Y2 adapter for the OPTIONAL hire approval boundary (design §11.3,
 * directive T8.8).
 *
 * <p>REUSES the existing Workflow Y2 engine — HRM builds NO second approval
 * engine. This adapter only:</p>
 * <ul>
 *   <li>bootstraps (idempotently, per tenant) the PUBLISHED Y2
 *       "Hire Approval" definition: START → APPROVAL(requiredCapability
 *       {@code HRM.RECRUITMENT.HIRE.APPROVE}, policy ANY_ONE, self-approval
 *       DENY by engine policy) with APPROVE/REJECT end steps;</li>
 *   <li>starts instances with the canonical correlation
 *       {@code businessEntityType = "HR_OFFER_HIRE"},
 *       {@code businessEntityId = offerId}, {@code correlationId = offerId};
 *       an open instance for the same offer is REUSED (one approval per
 *       conversion-attempt window — never duplicated);</li>
 *   <li>reports the AUTHORITATIVE outcome: any REJECTED request ⇒ REJECTED;
 *       COMPLETED with ≥1 APPROVED request ⇒ APPROVED; otherwise NONE.</li>
 * </ul>
 *
 * <p>There is NO auto-run on timeout: the approval step's SLA escalation is
 * Y2-native and never produces APPROVED by itself. Approval DECISIONS remain
 * exclusively inside Workflow Y2.</p>
 */
@Component
public class WorkflowY2HireApprovalAdapter implements HireApprovalWorkflowPort {

    static final String DEFINITION_CODE = "HR_HIRE_APPROVAL";
    static final String DEFINITION_NAME = "HR Hire Approval";
    static final String DEFINITION_MODULE = "HRM";
    static final String STEP_SUBMIT_KEY = "submit";
    static final String STEP_APPROVAL_KEY = "hire_approval";
    static final String STEP_END_APPROVED_KEY = "end_approved";
    static final String STEP_END_REJECTED_KEY = "end_rejected";
    static final String CAPABILITY_HIRE_APPROVE = "HRM.RECRUITMENT.HIRE.APPROVE";

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowApprovalRequestRepository approvalRepository;
    private final WorkflowExecutionService executionService;
    private final WorkflowGraphExecutionService graphExecutionService;
    private final WorkflowEntitlementGuard entitlementGuard;

    public WorkflowY2HireApprovalAdapter(WorkflowDefinitionRepository definitionRepository,
                                         WorkflowInstanceRepository instanceRepository,
                                         WorkflowApprovalRequestRepository approvalRepository,
                                         WorkflowExecutionService executionService,
                                         WorkflowGraphExecutionService graphExecutionService,
                                         WorkflowEntitlementGuard entitlementGuard) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.approvalRepository = approvalRepository;
        this.executionService = executionService;
        this.graphExecutionService = graphExecutionService;
        this.entitlementGuard = entitlementGuard;
    }

    @Override
    public UUID startHireApproval(UUID tenantId, UUID offerId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrPublishDefinition(tenantId, submittedBy);

        // One approval per (offer, conversion-attempt window): reuse the open
        // instance (self-healed past START) instead of duplicating it.
        Optional<WorkflowInstance> open = instanceRepository
                .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, offerId).stream()
                .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                .filter(i -> definition.definitionFamilyId().equals(i.definitionFamilyId()))
                .findFirst();
        if (open.isPresent()) {
            healPastStart(tenantId, open.get(), submittedBy);
            return open.get().id();
        }

        String idempotencyKey = "HR_HIRE_APPROVAL:" + offerId + ":" + UUID.randomUUID();
        WorkflowInstance instance = WorkflowInstance.startY2(
                tenantId, definition.definitionFamilyId(), definition.id(), definition.version(),
                BUSINESS_ENTITY_TYPE, offerId, STEP_SUBMIT_KEY, submittedBy, offerId,
                null, null, idempotencyKey, null, null);
        instance = executionService.startWorkflow(instance, submittedBy);
        healPastStart(tenantId, instance, submittedBy);
        return instance.id();
    }

    @Override
    public ApprovalSnapshot loadHireApprovalOutcome(UUID tenantId, UUID workflowInstanceId) {
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isEmpty()) {
            return null; // stale or foreign correlation — the caller fails closed
        }
        WorkflowInstance loaded = instance.get();
        ApprovalOutcome outcome = ApprovalOutcome.NONE;
        List<WorkflowApprovalRequest> requests = approvalRepository.findByInstance(tenantId, workflowInstanceId);
        boolean anyApproved = requests.stream()
                .anyMatch(r -> r.status() == WorkflowApprovalRequest.Status.APPROVED);
        boolean anyRejected = requests.stream()
                .anyMatch(r -> r.status() == WorkflowApprovalRequest.Status.REJECTED);
        if (anyRejected) {
            outcome = ApprovalOutcome.REJECTED;
        } else if (loaded.status() == WorkflowInstance.Status.COMPLETED && anyApproved) {
            outcome = ApprovalOutcome.APPROVED;
        }
        return new ApprovalSnapshot(loaded.id(), loaded.definitionVersionId(),
                loaded.businessEntityType(), loaded.businessEntityId(),
                loaded.status().name(), outcome);
    }

    @Override
    public Optional<UUID> findLatestApproval(UUID tenantId, UUID offerId) {
        return instanceRepository.findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, offerId).stream()
                .filter(i -> i.definitionFamilyId()
                        .equals(UUID.nameUUIDFromBytes(
                                (tenantId + "|" + DEFINITION_CODE + "|v1")
                                        .getBytes(StandardCharsets.UTF_8))))
                .map(WorkflowInstance::id)
                .findFirst();
    }

    @Override
    public void cancelHireApproval(UUID tenantId, UUID workflowInstanceId, UUID actorUserId, String reason) {
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isEmpty()) {
            return; // already gone or never existed — idempotent
        }
        WorkflowInstance loaded = instance.get();
        if (loaded.status() == WorkflowInstance.Status.RUNNING
                || loaded.status() == WorkflowInstance.Status.PAUSED
                || loaded.status() == WorkflowInstance.Status.CANCELLING) {
            instanceRepository.save(loaded.cancel(actorUserId, reason));
        }
        for (WorkflowApprovalRequest request : approvalRepository.findByInstance(tenantId, workflowInstanceId)) {
            if (request.status() == WorkflowApprovalRequest.Status.PENDING) {
                approvalRepository.save(request.cancel(actorUserId));
            }
        }
    }

    // ==================== definition bootstrap ====================

    private WorkflowDefinition findOrPublishDefinition(UUID tenantId, UUID actor) {
        UUID familyId = UUID.nameUUIDFromBytes(
                (tenantId + "|" + DEFINITION_CODE + "|v1").getBytes(StandardCharsets.UTF_8));
        Optional<WorkflowDefinition> published = definitionRepository.findPublishedByFamily(tenantId, familyId);
        if (published.isPresent()) {
            return published.get();
        }
        WorkflowDefinition draft = new WorkflowDefinition(
                familyId, tenantId, familyId, DEFINITION_CODE, DEFINITION_NAME,
                "Authoritative Workflow Y2 approval for the governed hire conversion (HRM G1 T8, §11.3)",
                DEFINITION_MODULE, 1, WorkflowDefinition.Status.DRAFT,
                WorkflowDefinition.TriggerType.MANUAL, actor, 0L,
                WorkflowDefinition.EngineGeneration.LEGACY, WorkflowDefinition.PublicationState.DRAFT,
                null, null, null, null, 1,
                java.time.Instant.now(), java.time.Instant.now());
        draft = definitionRepository.save(draft);

        WorkflowStep submit = WorkflowStep.create(tenantId, draft.id(), STEP_SUBMIT_KEY,
                "Hire Approval Submission", WorkflowStep.StepType.START, 1, "{}", null, null, null);
        submit = definitionRepository.saveStep(submit);
        WorkflowStep approval = WorkflowStep.create(tenantId, draft.id(), STEP_APPROVAL_KEY,
                "Hire Approval", WorkflowStep.StepType.APPROVAL, 2,
                "{\"approvalPolicy\":\"ANY_ONE\"}", null, CAPABILITY_HIRE_APPROVE, null);
        approval = definitionRepository.saveStep(approval);
        WorkflowStep endApproved = WorkflowStep.create(tenantId, draft.id(), STEP_END_APPROVED_KEY,
                "Hire Approved", WorkflowStep.StepType.END, 3, "{}", null, null, null);
        endApproved = definitionRepository.saveStep(endApproved);
        WorkflowStep endRejected = WorkflowStep.create(tenantId, draft.id(), STEP_END_REJECTED_KEY,
                "Hire Rejected", WorkflowStep.StepType.END, 4, "{}", null, null, null);
        endRejected = definitionRepository.saveStep(endRejected);

        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                submit.id(), approval.id(), "to_approval", "SUCCESS", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                approval.id(), endApproved.id(), "approved", "APPROVE", null, 10, null));
        definitionRepository.saveTransition(WorkflowTransition.create(tenantId, draft.id(),
                approval.id(), endRejected.id(), "rejected", "REJECT", null, 10, null));

        String checksum = Integer.toHexString(java.util.Arrays.asList(
                DEFINITION_CODE, STEP_SUBMIT_KEY, STEP_APPROVAL_KEY,
                STEP_END_APPROVED_KEY, STEP_END_REJECTED_KEY).hashCode());
        WorkflowDefinition publishedDefinition = draft.publish(actor, checksum);
        return definitionRepository.save(publishedDefinition);
    }

    /**
     * Ensures the instance is past its START step (approval requests created).
     * A crash between start and the first advance self-heals on the next
     * conversion attempt instead of creating a second instance.
     */
    private void healPastStart(UUID tenantId, WorkflowInstance instance, UUID actor) {
        if (STEP_SUBMIT_KEY.equals(instance.currentStepKey())) {
            graphExecutionService.advance(tenantId, instance.id(), null, actor);
        }
    }
}
