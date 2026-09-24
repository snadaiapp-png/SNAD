package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransitionAudit;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link WorkflowInstance} lifecycle management.
 *
 * <p>State machine: RUNNING ↔ PAUSED → COMPLETED | CANCELLED | FAILED
 *
 * <p>Every state transition is recorded in {@code workflow_transition_audit}.
 * When {@link #advanceToNextStep(UUID, UUID, String, UUID)} is called, a new
 * {@link WorkflowStepInstance} (PENDING) is created for the target step,
 * and the previous step_instance (if any) is marked COMPLETED.
 *
 * <p>{@link #startWorkflow(WorkflowInstance, UUID)} also creates the first
 * step_instance (PENDING) so the workflow has a concrete work item from the
 * moment it starts.
 */
@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowTransitionAuditRepository auditRepo;

    public WorkflowExecutionService(
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository defRepo,
            WorkflowTransitionAuditRepository auditRepo) {
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.defRepo = defRepo;
        this.auditRepo = auditRepo;
    }

    @Transactional
    public WorkflowInstance startWorkflow(WorkflowInstance instance, UUID actorUserId) {
        var saved = instanceRepo.save(instance);
        // Create the first step_instance (PENDING) for the firstStepKey.
        createPendingStepInstance(saved, saved.currentStepKey());
        audit(actorUserId, saved, null, WorkflowTransitionAudit.Action.START,
                null, saved.status().name());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowInstance> findById(UUID tenantId, UUID id) {
        return instanceRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> findByTenant(UUID tenantId, int limit) {
        return instanceRepo.findByTenant(tenantId, limit);
    }

    @Transactional
    public WorkflowInstance pause(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status().name();
        var updated = instanceRepo.save(i.pause());
        audit(actorUserId, updated, null, WorkflowTransitionAudit.Action.PAUSE,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowInstance resume(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status().name();
        var updated = instanceRepo.save(i.resume());
        audit(actorUserId, updated, null, WorkflowTransitionAudit.Action.RESUME,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowInstance cancel(UUID tenantId, UUID id, UUID cancelledBy, String reason) {
        var i = load(tenantId, id);
        var oldStatus = i.status().name();
        var updated = instanceRepo.save(i.cancel(cancelledBy, reason));
        audit(cancelledBy, updated, null, WorkflowTransitionAudit.Action.CANCEL,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowInstance complete(UUID tenantId, UUID id, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status().name();
        // Mark the current step_instance as COMPLETED (if it exists and is in progress)
        completeCurrentStepInstance(tenantId, i, "Workflow completed");
        var updated = instanceRepo.save(i.complete());
        audit(actorUserId, updated, null, WorkflowTransitionAudit.Action.COMPLETE,
                oldStatus, updated.status().name());
        return updated;
    }

    @Transactional
    public WorkflowInstance fail(UUID tenantId, UUID id, String reason, UUID actorUserId) {
        var i = load(tenantId, id);
        var oldStatus = i.status().name();
        // Mark the current step_instance as FAILED (if it exists and is in progress)
        failCurrentStepInstance(tenantId, i, reason);
        var updated = instanceRepo.save(i.fail());
        audit(actorUserId, updated, null, WorkflowTransitionAudit.Action.FAIL,
                oldStatus, updated.status().name());
        return updated;
    }

    /**
     * Advance the instance to the next step. This:
     * <ol>
     *   <li>Marks the current step_instance (matching {@code instance.currentStepKey()}) as COMPLETED</li>
     *   <li>Advances the instance to {@code nextStepKey}</li>
     *   <li>Creates a new PENDING {@link WorkflowStepInstance} for the next step</li>
     *   <li>Records an ADVANCE audit event</li>
     * </ol>
     */
    @Transactional
    public WorkflowInstance advanceToNextStep(UUID tenantId, UUID instanceId,
                                              String nextStepKey, UUID actorUserId) {
        var i = load(tenantId, instanceId);
        var oldStepKey = i.currentStepKey();
        // 1. Complete the current step_instance.
        completeCurrentStepInstance(tenantId, i, "Advanced to " + nextStepKey);
        // 2. Advance the instance pointer.
        var updated = instanceRepo.save(i.advanceToStep(nextStepKey));
        // 3. Create the next PENDING step_instance.
        createPendingStepInstance(updated, nextStepKey);
        // 4. Audit.
        audit(actorUserId, updated, null, WorkflowTransitionAudit.Action.ADVANCE,
                oldStepKey, nextStepKey);
        return updated;
    }

    // ===== Helpers =====

    private WorkflowInstance load(UUID tenantId, UUID id) {
        return instanceRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found: " + id));
    }

    private void createPendingStepInstance(WorkflowInstance instance, String stepKey) {
        if (stepKey == null) {
            return;
        }
        var step = findStep(instance.tenantId(), instance.workflowDefinitionId(), stepKey);
        Instant dueAt = step.slaHours() != null
                ? Instant.now().plus(Duration.ofHours(step.slaHours()))
                : null;
        var stepInstance = WorkflowStepInstance.create(
                instance.tenantId(), instance.id(), step.id(),
                step.stepKey(), dueAt, null, step.requiredRole());
        stepInstanceRepo.save(stepInstance);
    }

    private void completeCurrentStepInstance(UUID tenantId, WorkflowInstance instance, String result) {
        if (instance.currentStepKey() == null) {
            return;
        }
        findCurrentStepInstance(instance.id(), instance.currentStepKey())
                .filter(si -> si.status() == WorkflowStepInstance.Status.IN_PROGRESS)
                .ifPresent(si -> stepInstanceRepo.save(si.complete(result)));
    }

    private void failCurrentStepInstance(UUID tenantId, WorkflowInstance instance, String reason) {
        if (instance.currentStepKey() == null) {
            return;
        }
        findCurrentStepInstance(instance.id(), instance.currentStepKey())
                .filter(si -> si.status() == WorkflowStepInstance.Status.IN_PROGRESS
                        || si.status() == WorkflowStepInstance.Status.PENDING)
                .ifPresent(si -> {
                    // PENDING step instances need to be started before they can be failed.
                    var started = si.status() == WorkflowStepInstance.Status.PENDING
                            ? stepInstanceRepo.save(si.start())
                            : si;
                    stepInstanceRepo.save(started.fail(reason));
                });
    }

    private Optional<WorkflowStepInstance> findCurrentStepInstance(UUID instanceId, String stepKey) {
        return stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(si -> stepKey.equals(si.stepKey()))
                .findFirst();
    }

    private WorkflowStep findStep(UUID tenantId, UUID workflowDefinitionId, String stepKey) {
        return defRepo.findSteps(workflowDefinitionId).stream()
                .filter(s -> stepKey.equals(s.stepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "WorkflowStep not found: definition=" + workflowDefinitionId
                                + " stepKey=" + stepKey));
    }

    private void audit(UUID actorUserId, WorkflowInstance instance, UUID stepInstanceId,
                       WorkflowTransitionAudit.Action action,
                       String fromState, String toState) {
        auditRepo.save(WorkflowTransitionAudit.create(
                instance.tenantId(), instance.id(), stepInstanceId,
                actorUserId, action, fromState, toState,
                instance.correlationId(), null
        ));
        log.info("WorkflowInstance event: action={} tenant={} instanceId={} fromState={} toState={} actor={}",
                action.name(), instance.tenantId(), instance.id(), fromState, toState, actorUserId);
    }
}
