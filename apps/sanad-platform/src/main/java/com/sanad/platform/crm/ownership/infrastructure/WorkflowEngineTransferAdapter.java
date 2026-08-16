package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.WorkflowPort;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production adapter that bridges {@link WorkflowPort} (CRM ownership transfer
 * boundary) to the central Workflow Engine.
 *
 * <p>This adapter creates real WorkflowDefinition, WorkflowInstance,
 * WorkflowStepInstance, and WorkflowApprovalRequest rows so that CRM
 * ownership transfers are backed by the full workflow engine audit trail.
 *
 * <p>It is registered as {@code @Component @Primary} so it takes precedence
 * over {@link InlineTransferWorkflowStubAdapter} when both are on the
 * classpath (i.e., in non-prod profiles where the stub is also registered).
 *
 * <p>Workflow definition bootstrap: On first use per tenant, the adapter
 * looks for an existing ACTIVE workflow definition with code
 * {@code CRM_TRANSFER_APPROVAL}. If none exists, it creates one with a
 * single APPROVAL step. This is idempotent — subsequent calls find and
 * reuse the existing definition.
 */
@Component
@Primary
public class WorkflowEngineTransferAdapter implements WorkflowPort {

    private static final String DEFINITION_CODE = "CRM_TRANSFER_APPROVAL";
    private static final String DEFINITION_NAME = "CRM Ownership Transfer Approval";
    private static final String DEFINITION_MODULE = "CRM";
    private static final String STEP_KEY = "transfer_approval";
    private static final String STEP_NAME = "Transfer Approval";
    private static final String BUSINESS_ENTITY_TYPE = "CRM_TRANSFER_REQUEST";
    private static final String APPROVER_ROLE = "TRANSFER_APPROVER";
    private static final long APPROVAL_DUE_HOURS = 7L * 24L; // 7 days

    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowApprovalRequestRepository approvalRepo;

    public WorkflowEngineTransferAdapter(
            WorkflowDefinitionRepository defRepo,
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowApprovalRequestRepository approvalRepo) {
        this.defRepo = defRepo;
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.approvalRepo = approvalRepo;
    }

    @Override
    public UUID startTransferApproval(UUID tenantId,
                                       UUID transferRequestId,
                                       UUID requesterUserId,
                                       List<UUID> approverUserIds) {
        if (tenantId == null || transferRequestId == null || requesterUserId == null
                || approverUserIds == null || approverUserIds.isEmpty()) {
            throw new OwnershipDomainException(
                    "Transfer approval requires tenant, transfer request, requester, and at least one approver");
        }

        // 1. Find or create the workflow definition for this tenant
        WorkflowDefinition def = findOrCreateDefinition(tenantId, requesterUserId);

        // 2. Find the approval step definition
        List<WorkflowStep> steps = defRepo.findSteps(def.id());
        WorkflowStep approvalStep = steps.isEmpty() ? null : steps.get(0);
        if (approvalStep == null) {
            throw new OwnershipDomainException(
                    "CRM transfer approval step not found in workflow definition");
        }

        // 3. Start a workflow instance
        WorkflowInstance instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                BUSINESS_ENTITY_TYPE, transferRequestId,
                STEP_KEY, requesterUserId, transferRequestId);
        instance = instanceRepo.save(instance);

        // 4. Create the step instance (PENDING)
        Instant dueAt = Instant.now().plus(Duration.ofHours(APPROVAL_DUE_HOURS));
        WorkflowStepInstance stepInstance = WorkflowStepInstance.create(
                tenantId, instance.id(), approvalStep.id(),
                STEP_KEY, dueAt, approverUserIds.get(0), APPROVER_ROLE);
        stepInstanceRepo.save(stepInstance);

        // 5. Create one approval request per approver
        for (UUID approverId : approverUserIds) {
            WorkflowApprovalRequest request = WorkflowApprovalRequest.create(
                    tenantId, instance.id(), stepInstance.id(),
                    approverId, APPROVER_ROLE, dueAt, requesterUserId);
            approvalRepo.save(request);
        }

        return instance.id();
    }

    @Override
    public void cancelApproval(UUID tenantId, UUID workflowRunId, String reason) {
        if (tenantId == null || workflowRunId == null) {
            throw new OwnershipDomainException(
                    "Complete workflow cancellation requires tenant and workflow run ID");
        }

        // Find the workflow instance
        Optional<WorkflowInstance> instanceOpt = instanceRepo.findById(tenantId, workflowRunId);
        if (instanceOpt.isEmpty()) {
            return; // Already gone or never existed — idempotent
        }

        WorkflowInstance instance = instanceOpt.get();

        // Cancel the instance
        UUID systemCancelledBy = UUID.nameUUIDFromBytes(
                ("system-cancel:" + workflowRunId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WorkflowInstance cancelled = instance.cancel(systemCancelledBy, reason);
        instanceRepo.save(cancelled);

        // Cancel all pending approval requests for this instance
        List<WorkflowApprovalRequest> requests = approvalRepo.findByInstance(tenantId, workflowRunId);
        for (WorkflowApprovalRequest request : requests) {
            if (request.status() == WorkflowApprovalRequest.Status.PENDING) {
                WorkflowApprovalRequest cancelledReq = request.cancel(systemCancelledBy);
                approvalRepo.save(cancelledReq);
            }
        }
    }

    @Override
    public boolean isStub() {
        return false;
    }

    /**
     * Find an existing ACTIVE workflow definition by code, or create a new one.
     * Idempotent — if the definition already exists, it is reused.
     */
    private WorkflowDefinition findOrCreateDefinition(UUID tenantId, UUID requesterUserId) {
        Optional<WorkflowDefinition> existing = defRepo.findActiveByCode(tenantId, DEFINITION_CODE);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create DRAFT, then activate
        WorkflowDefinition def = WorkflowDefinition.create(
                tenantId, DEFINITION_CODE, DEFINITION_NAME,
                "Workflow for CRM ownership transfer approval requests",
                DEFINITION_MODULE, WorkflowDefinition.TriggerType.MANUAL, requesterUserId);
        def = defRepo.save(def);
        def = def.activate();
        def = defRepo.save(def);

        // Create the single approval step
        WorkflowStep step = WorkflowStep.create(
                tenantId, def.id(), STEP_KEY, STEP_NAME,
                WorkflowStep.StepType.APPROVAL, 1, null,
                (int) APPROVAL_DUE_HOURS, null, APPROVER_ROLE);
        defRepo.saveStep(step);

        return def;
    }
}
