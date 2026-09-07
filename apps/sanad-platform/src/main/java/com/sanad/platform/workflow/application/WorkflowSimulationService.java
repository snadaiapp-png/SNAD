package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionValidation;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Side-effect-free simulation boundary (design decision AN3).
 *
 * <p>Simulation walks the graph with the supplied test context and stub
 * behavior only. It MUST NOT and DOES NOT create invoices, orders, users,
 * messages, webhook deliveries, payments, or any other production side
 * effect — system actions, notifications, and sub-workflow calls are stubbed
 * by design and reported as such in the result notes.</p>
 */
@Service
public class WorkflowSimulationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSimulationService.class);

    private final WorkflowDefinitionValidator validator;
    private final WorkflowDefinitionRepository definitionRepo;

    public WorkflowSimulationService(WorkflowDefinitionValidator validator,
                                     WorkflowDefinitionRepository definitionRepo) {
        this.validator = validator;
        this.definitionRepo = definitionRepo;
    }

    public record SimulationResult(
            boolean valid,
            boolean simulated,
            List<UUID> visitedStepIds,
            List<String> notes,
            WorkflowDefinitionValidation validation) {}

    /**
     * Simulates the graph of one concrete definition version. The traversal
     * follows every outgoing transition of each visited step (branches are
     * all explored, conditions treated as open) and never executes any
     * adapter, notification, or sub-workflow.
     */
    @Transactional(readOnly = true)
    public SimulationResult simulate(UUID tenantId, UUID definitionId, Map<String, Object> context) {
        WorkflowDefinitionValidation validation = validator.validate(tenantId, definitionId);
        if (!validation.valid()) {
            return new SimulationResult(false, false, List.of(),
                    List.of("Simulation blocked: definition failed validation"), validation);
        }

        List<WorkflowStep> steps = definitionRepo.findSteps(definitionId);
        List<WorkflowTransition> transitions = definitionRepo.findTransitions(definitionId);

        Set<UUID> visited = new HashSet<>();
        LinkedList<UUID> queue = new LinkedList<>();
        steps.stream()
                .filter(s -> s.stepType() == WorkflowStep.StepType.START)
                .findFirst()
                .ifPresent(start -> queue.add(start.id()));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) continue;
            transitions.stream()
                    .filter(t -> t.fromStepId().equals(current))
                    .forEach(t -> queue.add(t.toStepId()));
        }

        log.info("Workflow simulation executed (stub-only): tenant={} definition={} visitedSteps={}",
                tenantId, definitionId, visited.size());
        return new SimulationResult(
                true,
                true,
                List.copyOf(visited),
                List.of(
                        "Simulation is non-production: no source-module side effects occurred",
                        "System actions, notifications, and sub-workflows were stubbed"),
                validation);
    }
}
