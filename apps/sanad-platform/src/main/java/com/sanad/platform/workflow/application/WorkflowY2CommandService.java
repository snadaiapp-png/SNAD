package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Generation-explicit command facade for Y2 browser/API clients.
 *
 * <p>The authoritative graph engine remains {@link WorkflowGraphExecutionService}.
 * This facade only supplies the missing DIRECT assignment context: when an
 * actionable human step has no pool rule, the employee linked to the actor
 * entering the step becomes the concrete DIRECT assignee. All other graph
 * shapes continue through the authoritative engine.</p>
 */
@Service
public class WorkflowY2CommandService {

    private final WorkflowGraphExecutionService graph;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowStepInstanceRepository stepInstanceRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowWorkItemService workItemService;
    private final WorkflowActionabilityService actionabilityService;

    public WorkflowY2CommandService(
            WorkflowGraphExecutionService graph,
            WorkflowInstanceRepository instanceRepo,
            WorkflowStepInstanceRepository stepInstanceRepo,
            WorkflowDefinitionRepository definitionRepo,
            WorkflowWorkItemService workItemService,
            WorkflowActionabilityService actionabilityService) {
        this.graph = graph;
        this.instanceRepo = instanceRepo;
        this.stepInstanceRepo = stepInstanceRepo;
        this.definitionRepo = definitionRepo;
        this.workItemService = workItemService;
        this.actionabilityService = actionabilityService;
    }

    @Transactional
    public WorkflowInstance advance(UUID tenantId, UUID instanceId, String outcome, UUID actorUserId) {
        WorkflowInstance instance = instanceRepo.findById(tenantId, instanceId)
                .orElseThrow(() -> new IllegalArgumentException("WorkflowInstance not found: " + instanceId));
        if (instance.engineGeneration() != WorkflowInstance.EngineGeneration.Y2) {
            throw new IllegalStateException("Only Y2 instances may use the Y2 command API");
        }
        if (instance.status() != WorkflowInstance.Status.RUNNING) {
            throw new IllegalStateException("Cannot advance instance in status " + instance.status());
        }

        WorkflowStepInstance current = stepInstanceRepo.findByInstance(instanceId).stream()
                .filter(step -> step.stepKey().equals(instance.currentStepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current step instance for key " + instance.currentStepKey()));

        List<WorkflowTransition> candidates = definitionRepo.findTransitions(instance.definitionVersionId()).stream()
                .filter(t -> t.fromStepId().equals(current.workflowStepId()))
                .filter(t -> outcomeMatches(t, outcome))
                .sorted(Comparator.comparingInt(WorkflowTransition::priority).reversed())
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "Graph resolution incident: expected exactly one transition for outcome " + outcome
                            + " from step " + current.stepKey() + ", found " + candidates.size());
        }

        WorkflowTransition selected = candidates.get(0);
        WorkflowStep nextStep = definitionRepo.findSteps(instance.definitionVersionId()).stream()
                .filter(step -> step.id().equals(selected.toStepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Transition target step missing: " + selected.toStepId()));

        boolean directHuman = (nextStep.stepType() == WorkflowStep.StepType.HUMAN_TASK
                || nextStep.stepType() == WorkflowStep.StepType.APPROVAL)
                && isBlank(nextStep.requiredCapability())
                && isBlank(nextStep.requiredRole());
        if (!directHuman) {
            return graph.advance(tenantId, instanceId, outcome, actorUserId);
        }

        WorkflowStepInstance started = current.status() == WorkflowStepInstance.Status.PENDING
                ? stepInstanceRepo.save(current.start())
                : current;
        stepInstanceRepo.save(started.complete("Advanced via " + selected.transitionKey()));

        WorkflowInstance updated = instance.advanceToStep(nextStep.stepKey());
        Instant dueAt = nextStep.slaHours() != null
                ? Instant.now().plus(Duration.ofHours(nextStep.slaHours()))
                : null;
        WorkflowStepInstance nextStepInstance = stepInstanceRepo.save(WorkflowStepInstance.create(
                tenantId, instanceId, nextStep.id(), nextStep.stepKey(), dueAt, null, null));

        UUID assigneeEmployeeId = actionabilityService
                .requireActionableEmployee(tenantId, actorUserId)
                .id();
        WorkflowWorkItem item = WorkflowWorkItem.create(
                tenantId,
                instance.id(),
                nextStepInstance.id(),
                nextStep.stepType() == WorkflowStep.StepType.APPROVAL
                        ? WorkflowWorkItem.Type.APPROVAL
                        : WorkflowWorkItem.Type.HUMAN_TASK,
                WorkflowWorkItem.AssignmentMode.DIRECT,
                assigneeEmployeeId,
                "WORKFLOW",
                "INSTANCE",
                instance.id(),
                nextStep.name(),
                null,
                0,
                dueAt,
                dueAt);
        workItemService.create(item, List.of());
        return instanceRepo.save(updated);
    }

    private boolean outcomeMatches(WorkflowTransition transition, String outcome) {
        if (outcome == null) return true;
        if (outcome.equalsIgnoreCase(transition.outcome())) return true;
        return "SUCCESS".equalsIgnoreCase(transition.outcome())
                && outcome.equalsIgnoreCase(transition.transitionKey());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
