package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowAssignmentRule;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import com.sanad.platform.workflow.domain.WorkflowWorkItemCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative Y2 graph transition command (design decisions H3/R3/AA3).
 *
 * <p>Routes exclusively by the persisted instance generation and the pinned
 * definition version: a Y2 instance never consults the legacy linear runtime,
 * and a LEGACY instance can never be advanced through this service. Outcome
 * selection matches the current step's outgoing transitions by outcome token
 * with deterministic priority ordering; zero or ambiguous matches raise a
 * graph-resolution incident instead of guessing.</p>
 */
@Service
public class WorkflowGraphExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGraphExecutionService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowWorkItemService workItemService;
    private final WorkflowAssignmentResolver assignmentResolver;

    public WorkflowGraphExecutionService(
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository definitionRepo,
            WorkflowWorkItemService workItemService,
            WorkflowAssignmentResolver assignmentResolver) {
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.definitionRepo = definitionRepo;
        this.workItemService = workItemService;
        this.assignmentResolver = assignmentResolver;
    }

    @Transactional
    public WorkflowInstance advance(UUID tenantId, UUID instanceId, String outcome, UUID actorUserId) {
        var instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found: " + instanceId));
        if (instance.engineGeneration() != WorkflowInstance.EngineGeneration.Y2) {
            throw new IllegalStateException(
                    "LEGACY instances must advance through the legacy runtime, not the Y2 graph");
        }
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new IllegalStateException("Cannot advance instance in status " + instance.status());
        }

        WorkflowStepInstance current = stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(si -> si.stepKey().equals(instance.currentStepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current step instance for key " + instance.currentStepKey()));

        List<WorkflowTransition> candidates = definitionRepo
                .findTransitions(instance.definitionVersionId()).stream()
                .filter(t -> t.fromStepId().equals(current.workflowStepId()))
                .filter(t -> outcomeMatches(t, outcome))
                .sorted(Comparator.comparingInt(WorkflowTransition::priority).reversed())
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Graph resolution incident: expected exactly one transition for outcome " + outcome
                            + " from step " + current.stepKey() + ", found " + candidates.size()
                            + " (instance=" + instanceId + ")");
        }
        WorkflowTransition selected = candidates.get(0);

        WorkflowStep nextStep = definitionRepo.findSteps(instance.definitionVersionId()).stream()
                .filter(s -> s.id().equals(selected.toStepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transition target step missing: " + selected.toStepId()));

        // Complete the current step instance (PENDING -> IN_PROGRESS -> COMPLETED
        // mirrors the legacy lifecycle semantics), then move the pointer.
        WorkflowStepInstance started = current.status() == WorkflowStepInstance.Status.PENDING
                ? stepInstanceRepo.save(current.start())
                : current;
        stepInstanceRepo.save(started.complete("Advanced via " + selected.transitionKey()));

        WorkflowInstance updated = instance.advanceToStep(nextStep.stepKey());

        if (nextStep.stepType() == WorkflowStep.StepType.END) {
            var saved = instanceRepo.save(updated.complete());
            log.info("Y2 instance completed: tenant={} instance={} via {}",
                    tenantId, instanceId, selected.transitionKey());
            return saved;
        }

        Instant dueAt = nextStep.slaHours() != null
                ? Instant.now().plus(Duration.ofHours(nextStep.slaHours()))
                : null;
        WorkflowStepInstance nextStepInstance = stepInstanceRepo.save(WorkflowStepInstance.create(
                tenantId, instanceId, nextStep.id(), nextStep.stepKey(), dueAt,
                null, nextStep.requiredRole()));

        if (nextStep.stepType() == WorkflowStep.StepType.HUMAN_TASK
                || nextStep.stepType() == WorkflowStep.StepType.APPROVAL) {
            activateWorkItem(tenantId, updated, nextStepInstance, nextStep);
        }

        var saved = instanceRepo.save(updated);
        log.info("Y2 instance advanced: tenant={} instance={} step={} outcome={}",
                tenantId, instanceId, nextStep.stepKey(), outcome);
        return saved;
    }

    private boolean outcomeMatches(WorkflowTransition transition, String outcome) {
        if (outcome == null) {
            return true;
        }
        if (outcome.equals(transition.outcome())) {
            return true;
        }
        // Unkeyed transitions fall back to matching the transition key.
        return "SUCCESS".equalsIgnoreCase(transition.outcome())
                && outcome.equalsIgnoreCase(transition.transitionKey());
    }

    private void activateWorkItem(UUID tenantId, WorkflowInstance instance,
                                  WorkflowStepInstance stepInstance, WorkflowStep step) {
        boolean hasCapability = step.requiredCapability() != null && !step.requiredCapability().isBlank();
        boolean hasRole = step.requiredRole() != null && !step.requiredRole().isBlank();
        WorkflowWorkItem.AssignmentMode mode =
                hasCapability || hasRole
                        ? WorkflowWorkItem.AssignmentMode.WORK_POOL
                        : WorkflowWorkItem.AssignmentMode.DIRECT;

        List<WorkflowWorkItemCandidate> candidates = List.of();
        UUID assignee = null;
        if (mode == WorkflowWorkItem.AssignmentMode.WORK_POOL) {
            WorkflowAssignmentRule rule = hasCapability
                    ? new WorkflowAssignmentRule.Permission(step.requiredCapability())
                    : new WorkflowAssignmentRule.Role(step.requiredRole());
            ResolvedAssignment resolved = assignmentResolver.resolve(tenantId, rule,
                    new WorkflowAssignmentContext(null));
            candidates = resolved.employeeIds().stream()
                    .map(employeeId -> WorkflowWorkItemCandidate.create(
                            tenantId, null, employeeId, resolved.resolutionSource()))
                    .toList();
        }

        var item = WorkflowWorkItem.create(tenantId, instance.id(), stepInstance.id(),
                step.stepType() == WorkflowStep.StepType.APPROVAL
                        ? WorkflowWorkItem.Type.APPROVAL : WorkflowWorkItem.Type.HUMAN_TASK,
                mode, assignee, "WORKFLOW", "INSTANCE", instance.id(),
                step.name(), null, 0, null, null);
        // Bind candidates to the concrete WorkItem id now that it exists.
        var created = workItemService.create(item, candidates.stream()
                .map(c -> new WorkflowWorkItemCandidate(c.tenantId(), item.id(), c.employeeId(),
                        c.resolutionSource(), c.resolvedAt(), c.snapshotMetadata()))
                .toList());
    }
}
