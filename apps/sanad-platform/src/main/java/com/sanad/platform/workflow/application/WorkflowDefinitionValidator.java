package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionValidation;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Y2 publish validator (design decision AN3, spec section 18).
 *
 * <p>Performs deterministic structural checks over the persisted graph of one
 * concrete definition version. No version may publish through the Y2 path
 * while any check fails. Checks that depend on later Y2 subsystems
 * (sub-workflow resolution depth, SLA calendar references, typed mapping
 * schemas) are validated structurally here and deepened by their subsystem
 * tasks — the error-code contract stays stable.</p>
 *
 * <p>The validator never executes expressions, never touches the network,
 * and never reads tenant business data: it inspects definition metadata
 * only.</p>
 */
@Service
public class WorkflowDefinitionValidator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionValidator.class);

    /** Maximum condition AST nesting depth accepted at publish time (U3). */
    static final int MAX_CONDITION_DEPTH = 32;

    /**
     * JSON keys that must never appear inside step configuration or condition
     * ASTs — arbitrary executable code and embedded secrets are forbidden
     * (design invariants: safe AST conditions, no secrets in definitions).
     */
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "script", "eval", "function", "javascript", "sql", "shell", "command",
            "exec", "password", "secret", "apikey", "api_key", "token",
            "credential", "credentials", "connectionstring");

    private final WorkflowDefinitionRepository definitionRepo;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionValidator(WorkflowDefinitionRepository definitionRepo,
                                       ObjectMapper objectMapper) {
        this.definitionRepo = definitionRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionValidation validate(UUID tenantId, UUID definitionId) {
        WorkflowDefinition definition = definitionRepo.findById(tenantId, definitionId)
                .orElse(null);
        if (definition == null) {
            return WorkflowDefinitionValidation.of(List.of(
                    new WorkflowDefinitionValidation.Error(
                            "DEFINITION_NOT_FOUND", "Definition does not exist in this tenant", null)));
        }

        List<WorkflowStep> steps = definitionRepo.findSteps(definitionId);
        List<WorkflowTransition> transitions = definitionRepo.findTransitions(definitionId);
        List<WorkflowDefinitionValidation.Error> errors = new ArrayList<>();

        checkStartStep(steps, errors);
        checkEndStep(steps, errors);
        checkTransitionOwnership(steps, transitions, errors);
        checkApprovalOutcomes(steps, transitions, errors);
        checkForkJoinStructure(steps, transitions, errors);
        checkReachability(steps, transitions, errors);
        checkConditionAsts(transitions, errors);
        checkConfigurationPayload(steps, errors);
        checkAssignmentConfiguration(steps, errors);

        log.debug("WorkflowDefinition validated: tenant={} definition={} errors={}",
                tenantId, definitionId, errors.size());
        return WorkflowDefinitionValidation.of(errors);
    }

    private void checkStartStep(List<WorkflowStep> steps,
                                List<WorkflowDefinitionValidation.Error> errors) {
        long starts = steps.stream()
                .filter(s -> s.stepType() == WorkflowStep.StepType.START)
                .count();
        if (starts != 1) {
            errors.add(new WorkflowDefinitionValidation.Error(
                    "START_COUNT_INVALID",
                    "Graph must contain exactly one START step, found " + starts,
                    null));
        }
    }

    private void checkEndStep(List<WorkflowStep> steps,
                              List<WorkflowDefinitionValidation.Error> errors) {
        if (steps.stream().noneMatch(s -> s.stepType() == WorkflowStep.StepType.END)) {
            errors.add(new WorkflowDefinitionValidation.Error(
                    "END_MISSING", "Graph must contain at least one END step", null));
        }
    }

    private void checkTransitionOwnership(List<WorkflowStep> steps,
                                          List<WorkflowTransition> transitions,
                                          List<WorkflowDefinitionValidation.Error> errors) {
        Set<UUID> stepIds = new HashSet<>();
        steps.forEach(s -> stepIds.add(s.id()));
        for (WorkflowTransition t : transitions) {
            if (!stepIds.contains(t.fromStepId()) || !stepIds.contains(t.toStepId())) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "TRANSITION_OWNER_INVALID",
                        "Transition " + t.transitionKey() + " references steps outside this version",
                        t.fromStepId()));
            }
        }
    }

    private void checkApprovalOutcomes(List<WorkflowStep> steps,
                                       List<WorkflowTransition> transitions,
                                       List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.APPROVAL
                    && step.stepType() != WorkflowStep.StepType.HUMAN_TASK) {
                continue;
            }
            Set<String> outcomes = new HashSet<>();
            transitions.stream()
                    .filter(t -> t.fromStepId().equals(step.id()))
                    .forEach(t -> {
                        if (t.outcome() != null) outcomes.add(t.outcome().toUpperCase(Locale.ROOT));
                    });
            if (step.stepType() == WorkflowStep.StepType.APPROVAL) {
                if (!outcomes.contains("APPROVE") || !outcomes.contains("REJECT")) {
                    errors.add(new WorkflowDefinitionValidation.Error(
                            "APPROVAL_OUTCOME_MISSING",
                            "Approval step '" + step.stepKey()
                                    + "' must declare both APPROVE and REJECT transitions",
                            step.id()));
                }
            } else if (outcomes.isEmpty()) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "STEP_NO_OUTGOING",
                        "Human task step '" + step.stepKey() + "' has no outgoing transition",
                        step.id()));
            }
        }
    }

    private void checkForkJoinStructure(List<WorkflowStep> steps,
                                        List<WorkflowTransition> transitions,
                                        List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            long outgoing = transitions.stream()
                    .filter(t -> t.fromStepId().equals(step.id()))
                    .count();
            long incoming = transitions.stream()
                    .filter(t -> t.toStepId().equals(step.id()))
                    .count();
            if (step.stepType() == WorkflowStep.StepType.PARALLEL_FORK && outgoing < 2) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "FORK_STRUCTURE_INVALID",
                        "PARALLEL_FORK step '" + step.stepKey() + "' needs at least two branches",
                        step.id()));
            }
            if (step.stepType() == WorkflowStep.StepType.PARALLEL_JOIN && incoming < 2) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "JOIN_STRUCTURE_INVALID",
                        "PARALLEL_JOIN step '" + step.stepKey() + "' needs at least two incoming branches",
                        step.id()));
            }
        }
    }

    private void checkReachability(List<WorkflowStep> steps,
                                   List<WorkflowTransition> transitions,
                                   List<WorkflowDefinitionValidation.Error> errors) {
        WorkflowStep start = steps.stream()
                .filter(s -> s.stepType() == WorkflowStep.StepType.START)
                .findFirst().orElse(null);
        if (start == null) {
            return; // START_COUNT_INVALID already reported
        }
        Set<UUID> reachable = new HashSet<>();
        LinkedList<UUID> queue = new LinkedList<>();
        queue.add(start.id());
        reachable.add(start.id());
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            transitions.stream()
                    .filter(t -> t.fromStepId().equals(current))
                    .forEach(t -> {
                        if (reachable.add(t.toStepId())) queue.add(t.toStepId());
                    });
        }
        for (WorkflowStep step : steps) {
            if (!reachable.contains(step.id())) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "STEP_UNREACHABLE",
                        "Step '" + step.stepKey() + "' is not reachable from START",
                        step.id()));
            }
        }
    }

    private void checkConditionAsts(List<WorkflowTransition> transitions,
                                    List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowTransition t : transitions) {
            if (t.conditionAst() == null || t.conditionAst().isBlank()) continue;
            try {
                JsonNode ast = objectMapper.readTree(t.conditionAst());
                if (scanForForbiddenKeys(ast, errors, t.fromStepId())) {
                    continue;
                }
                if (depth(ast) > MAX_CONDITION_DEPTH) {
                    errors.add(new WorkflowDefinitionValidation.Error(
                            "EXPRESSION_DEPTH_EXCEEDED",
                            "Condition AST on transition '" + t.transitionKey()
                                    + "' exceeds the maximum depth of " + MAX_CONDITION_DEPTH,
                            t.fromStepId()));
                }
            } catch (Exception e) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "EXPRESSION_AST_INVALID",
                        "Condition AST on transition '" + t.transitionKey()
                                + "' is not a valid JSON structure",
                        t.fromStepId()));
            }
        }
    }

    private void checkConfigurationPayload(List<WorkflowStep> steps,
                                           List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.configuration() == null || step.configuration().isBlank()) continue;
            try {
                JsonNode config = objectMapper.readTree(step.configuration());
                scanForForbiddenKeys(config, errors, step.id());
            } catch (Exception e) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "STEP_CONFIG_INVALID",
                        "Step '" + step.stepKey() + "' configuration is not valid JSON",
                        step.id()));
            }
        }
    }

    /** Returns true when a forbidden-key error was reported for this node tree. */
    private boolean scanForForbiddenKeys(JsonNode node,
                                         List<WorkflowDefinitionValidation.Error> errors,
                                         UUID stepId) {
        boolean reported = false;
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (FORBIDDEN_KEYS.contains(field.toLowerCase(Locale.ROOT))) {
                    errors.add(new WorkflowDefinitionValidation.Error(
                            "FORBIDDEN_FIELD_DETECTED",
                            "Field '" + field + "' is forbidden in workflow definitions",
                            stepId));
                    reported = true;
                }
            }
            for (JsonNode child : node) {
                reported |= scanForForbiddenKeys(child, errors, stepId);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                reported |= scanForForbiddenKeys(child, errors, stepId);
            }
        }
        return reported;
    }

    private int depth(JsonNode node) {
        if (node.isObject() || node.isArray()) {
            int max = 0;
            for (JsonNode child : node) {
                max = Math.max(max, depth(child));
            }
            return max + 1;
        }
        return 1;
    }

    private void checkAssignmentConfiguration(List<WorkflowStep> steps,
                                              List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.HUMAN_TASK
                    && step.stepType() != WorkflowStep.StepType.APPROVAL) {
                continue;
            }
            boolean hasAssignment = step.requiredCapability() != null && !step.requiredCapability().isBlank()
                    || step.requiredRole() != null && !step.requiredRole().isBlank();
            if (!hasAssignment && step.configuration() != null) {
                try {
                    JsonNode config = objectMapper.readTree(step.configuration());
                    JsonNode assignment = config.get("assignment");
                    hasAssignment = assignment != null && assignment.isObject()
                            && assignment.has("type");
                } catch (Exception ignored) {
                    // reported by STEP_CONFIG_INVALID already
                }
            }
            if (!hasAssignment) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "ASSIGNMENT_CONFIG_MISSING",
                        "Human step '" + step.stepKey()
                                + "' must declare an assignment rule, capability, or role",
                        step.id()));
            }
        }
    }
}
