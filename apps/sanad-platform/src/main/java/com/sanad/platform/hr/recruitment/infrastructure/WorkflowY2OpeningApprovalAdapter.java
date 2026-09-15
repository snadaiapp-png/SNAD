package com.sanad.platform.hr.recruitment.infrastructure;

import com.sanad.platform.hr.recruitment.application.OpeningApprovalWorkflowPort;
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
 * Workflow Y2 adapter for the HRM JOB OPENING approval boundary (design
 * §11.1, directive §2).
 *
 * <p>REUSES the existing Workflow Y2 engine — HRM builds NO second approval
 * engine. Bootstraps (idempotently, per tenant) the PUBLISHED Y2
 * "Job Opening Approval" definition: START → APPROVAL(requiredCapability
 * {@code HRM.RECRUITMENT.OPENING.PUBLISH}, policy ANY_ONE, self-approval
 * DENY by engine policy, SLA 120h ⇒ Y2 escalation, never auto-approval)
 * with APPROVE/REJECT end steps. Instances correlate via
 * {@code businessEntityType = "HR_JOB_OPENING"} and are deduplicated per
 * open opening approval. Approval DECISIONS remain exclusively inside
 * Workflow Y2.</p>
 */
@Component
public class WorkflowY2OpeningApprovalAdapter implements OpeningApprovalWorkflowPort {

    static final String DEFINITION_CODE = "HR_OPENING_APPROVAL";
    static final String DEFINITION_NAME = "Job Opening Approval";
    static final String DEFINITION_MODULE = "HRM";
    static final String STEP_SUBMIT_KEY = "submit";
    static final String STEP_APPROVAL_KEY = "opening_approval";
    static final String STEP_END_APPROVED_KEY = "end_approved";
    static final String STEP_END_REJECTED_KEY = "end_rejected";
    static final String CAPABILITY_OPENING_PUBLISH = "HRM.RECRUITMENT.OPENING.PUBLISH";
    /** Design §11.1 seed default: 5 business days ⇒ Y2 escalation, no auto-approval. */
    static final Integer APPROVAL_SLA_HOURS = 120;

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowApprovalRequestRepository approvalRepository;
    private final WorkflowExecutionService executionService;
    private final WorkflowGraphExecutionService graphExecutionService;
    private final WorkflowEntitlementGuard entitlementGuard;

    public WorkflowY2OpeningApprovalAdapter(WorkflowDefinitionRepository definitionRepository,
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
    public UUID startOpeningApproval(UUID tenantId, UUID openingId, UUID submittedBy) {
        entitlementGuard.requireWorkflowEnabled(tenantId);
        WorkflowDefinition definition = findOrPublishDefinition(tenantId, submittedBy);

        // Idempotency: one open workflow approval per (opening, submitted
        // state period) — an already-RUNNING instance is reused and healed.
        Optional<WorkflowInstance> open = instanceRepository
                .findByBusinessEntity(tenantId, BUSINESS_ENTITY_TYPE, openingId).stream()
                .filter(i -> i.status() == WorkflowInstance.Status.RUNNING)
                .filter(i -> definition.definitionFamilyId().equals(i.definitionFamilyId()))
                .findFirst();
        if (open.isPresent()) {
            healPastStart(tenantId, open.get(), submittedBy);
            return open.get().id();
        }

        String idempotencyKey = "HR_OPENING_APPROVAL:" + openingId + ":" + UUID.randomUUID();
        WorkflowInstance instance = WorkflowInstance.startY2(
                tenantId, definition.definitionFamilyId(), definition.id(), definition.version(),
                BUSINESS_ENTITY_TYPE, openingId, STEP_SUBMIT_KEY, submittedBy, openingId,
                null, null, idempotencyKey, null, null);
        instance = executionService.startWorkflow(instance, submittedBy);
        healPastStart(tenantId, instance, submittedBy);
        return instance.id();
    }

    @Override
    public ApprovalSnapshot loadOpeningApprovalOutcome(UUID tenantId, UUID workflowInstanceId) {
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isEmpty()) {
            return null;
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
    public void cancelOpeningApproval(UUID tenantId, UUID workflowInstanceId, UUID actorUserId, String reason) {
        Optional<WorkflowInstance> instance = instanceRepository.findById(tenantId, workflowInstanceId);
        if (instance.isEmpty()) {
            return; // idempotent
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

    private UUID deterministicFamilyId(UUID tenantId) {
        return UUID.nameUUIDFromBytes(
                (tenantId + "|" + DEFINITION_CODE + "|v1").getBytes(StandardCharsets.UTF_8));
    }

    private WorkflowDefinition findOrPublishDefinition(UUID tenantId, UUID actor) {
        UUID familyId = deterministicFamilyId(tenantId);
        Optional<WorkflowDefinition> published = definitionRepository.findPublishedByFamily(tenantId, familyId);
        if (published.isPresent()) {
            return published.get();
        }
        WorkflowDefinition draft = new WorkflowDefinition(
                familyId, tenantId, familyId, DEFINITION_CODE, DEFINITION_NAME,
                "Authoritative Workflow Y2 approval for job opening publication (HRM G1 T7)",
                DEFINITION_MODULE, 1, WorkflowDefinition.Status.DRAFT,
                WorkflowDefinition.TriggerType.MANUAL, actor, 0L,
                WorkflowDefinition.EngineGeneration.LEGACY, WorkflowDefinition.PublicationState.DRAFT,
                null, null, null, null, 1,
                java.time.Instant.now(), java.time.Instant.now());
        draft = definitionRepository.save(draft);

        WorkflowStep submit = WorkflowStep.create(tenantId, draft.id(), STEP_SUBMIT_KEY,
                "Opening Approval Submission", WorkflowStep.StepType.START, 1, "{}", null, null, null);
        submit = definitionRepository.saveStep(submit);
        // §11.1 inputs: opening ref, job snapshot title, org unit, headcount,
        // submitter, tenant — references ONLY, no PII, no compensation.
        WorkflowStep approval = WorkflowStep.create(tenantId, draft.id(), STEP_APPROVAL_KEY,
                "Opening Approval", WorkflowStep.StepType.APPROVAL, 2,
                "{\"approvalPolicy\":\"ANY_ONE\"}", APPROVAL_SLA_HOURS, CAPABILITY_OPENING_PUBLISH, null);
        approval = definitionRepository.saveStep(approval);
        WorkflowStep endApproved = WorkflowStep.create(tenantId, draft.id(), STEP_END_APPROVED_KEY,
                "Opening Approved", WorkflowStep.StepType.END, 3, "{}", null, null, null);
        endApproved = definitionRepository.saveStep(endApproved);
        WorkflowStep endRejected = WorkflowStep.create(tenantId, draft.id(), STEP_END_REJECTED_KEY,
                "Opening Rejected", WorkflowStep.StepType.END, 4, "{}", null, null, null);
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

    private void healPastStart(UUID tenantId, WorkflowInstance instance, UUID actor) {
        if (STEP_SUBMIT_KEY.equals(instance.currentStepKey())) {
            graphExecutionService.advance(tenantId, instance.id(), null, actor);
        }
    }
}
