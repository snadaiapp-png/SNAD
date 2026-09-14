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
        // R0.G7 hardening — deterministic structural semantics (additive;
        // existing error codes and behaviors are unchanged).
        checkStartIncoming(steps, transitions, errors);
        checkEndOutgoing(steps, transitions, errors);
        checkPathToEnd(steps, transitions, errors);
        checkTransitionDeterminism(steps, transitions, errors);
        checkConditionOutcomes(steps, transitions, errors);
        checkForkJoinConsistency(steps, errors);
        checkSystemActionConfiguration(steps, errors);
        checkAssignmentTypeValidity(steps, errors);

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

    /** Known assignment-rule type tokens (sealed WorkflowAssignmentRule hierarchy). */
    private static final Set<String> ASSIGNMENT_TYPES = Set.of(
            "EMPLOYEE", "MANAGER", "POSITION", "DEPARTMENT", "ROLE", "PERMISSION");

    private void checkStartIncoming(List<WorkflowStep> steps,
                                    List<WorkflowTransition> transitions,
                                    List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.START) continue;
            boolean hasIncoming = transitions.stream().anyMatch(t -> t.toStepId().equals(step.id()));
            if (hasIncoming) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "START_INCOMING_INVALID",
                        "START step '" + step.stepKey() + "' must not have incoming transitions",
                        step.id()));
            }
        }
    }

    private void checkEndOutgoing(List<WorkflowStep> steps,
                                  List<WorkflowTransition> transitions,
                                  List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.END) continue;
            boolean hasOutgoing = transitions.stream().anyMatch(t -> t.fromStepId().equals(step.id()));
            if (hasOutgoing) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "END_OUTGOING_INVALID",
                        "END step '" + step.stepKey() + "' must not have outgoing transitions",
                        step.id()));
            }
        }
    }

    /** Reverse reachability: every non-END step must be able to reach an END. */
    private void checkPathToEnd(List<WorkflowStep> steps,
                                List<WorkflowTransition> transitions,
                                List<WorkflowDefinitionValidation.Error> errors) {
        boolean hasEnd = steps.stream().anyMatch(s -> s.stepType() == WorkflowStep.StepType.END);
        if (!hasEnd) return; // END_MISSING already reported
        Set<UUID> reachesEnd = new HashSet<>();
        LinkedList<UUID> queue = new LinkedList<>();
        for (WorkflowStep step : steps) {
            if (step.stepType() == WorkflowStep.StepType.END) {
                reachesEnd.add(step.id());
                queue.add(step.id());
            }
        }
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            transitions.stream()
                    .filter(t -> t.toStepId().equals(current))
                    .forEach(t -> {
                        if (reachesEnd.add(t.fromStepId())) queue.add(t.fromStepId());
                    });
        }
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.END && !reachesEnd.contains(step.id())) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "NO_PATH_TO_END",
                        "Step '" + step.stepKey() + "' has no path to an END step",
                        step.id()));
            }
        }
    }

    /**
     * Deterministic edge semantics: duplicate transitions (same source,
     * outcome and target) and ambiguous transitions (same source and outcome
     * but different targets) can never resolve to exactly one successor.
     * Duplicate transition keys are enforced by the database unique
     * constraint, re-checked here defensively for corrupted/imported graphs.
     */
    private void checkTransitionDeterminism(List<WorkflowStep> steps,
                                            List<WorkflowTransition> transitions,
                                            List<WorkflowDefinitionValidation.Error> errors) {
        // A PARALLEL_FORK intentionally fires ALL of its outgoing branches —
        // same-outcome fan-out from a fork is not ambiguity.
        Set<UUID> forkStepIds = steps.stream()
                .filter(s -> s.stepType() == WorkflowStep.StepType.PARALLEL_FORK)
                .map(WorkflowStep::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, java.util.List<WorkflowTransition>> bySourceOutcome = new java.util.HashMap<>();
        Set<String> seenExact = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();
        for (WorkflowTransition t : transitions) {
            String exact = t.fromStepId() + "|" + outcomeToken(t) + "|" + t.toStepId();
            if (!seenExact.add(exact)) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "TRANSITION_DUPLICATE",
                        "Transition '" + t.transitionKey()
                                + "' duplicates an existing source/outcome/target edge",
                        t.fromStepId()));
            }
            if (!forkStepIds.contains(t.fromStepId())) {
                bySourceOutcome.computeIfAbsent(t.fromStepId() + "|" + outcomeToken(t),
                                k -> new ArrayList<>())
                        .add(t);
            }
            if (!seenKeys.add(t.transitionKey())) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "TRANSITION_KEY_DUPLICATE",
                        "Transition key '" + t.transitionKey() + "' is used more than once",
                        t.fromStepId()));
            }
        }
        for (java.util.List<WorkflowTransition> group : bySourceOutcome.values()) {
            long distinctTargets = group.stream().map(WorkflowTransition::toStepId).distinct().count();
            if (distinctTargets > 1) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "TRANSITION_AMBIGUOUS",
                        "Source/outcome pair on transition '" + group.get(0).transitionKey()
                                + "' resolves to multiple targets",
                        group.get(0).fromStepId()));
            }
        }
    }

    private String outcomeToken(WorkflowTransition t) {
        return t.outcome() == null ? "" : t.outcome().toUpperCase(Locale.ROOT);
    }

    /** CONDITION steps must declare exactly one TRUE and one FALSE branch. */
    private void checkConditionOutcomes(List<WorkflowStep> steps,
                                        List<WorkflowTransition> transitions,
                                        List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.CONDITION) continue;
            List<WorkflowTransition> outgoing = transitions.stream()
                    .filter(t -> t.fromStepId().equals(step.id()))
                    .toList();
            java.util.Map<String, Long> counts = outgoing.stream().collect(
                    java.util.stream.Collectors.groupingBy(
                            t -> outcomeToken(t), java.util.stream.Collectors.counting()));
            if (counts.getOrDefault("TRUE", 0L) < 1 || counts.getOrDefault("FALSE", 0L) < 1) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "CONDITION_OUTCOME_MISSING",
                        "Condition step '" + step.stepKey()
                                + "' must declare exactly one TRUE and one FALSE transition",
                        step.id()));
            }
            boolean ambiguous = counts.entrySet().stream().anyMatch(e -> e.getValue() > 1);
            if (ambiguous) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "CONDITION_OUTCOME_AMBIGUOUS",
                        "Condition step '" + step.stepKey()
                                + "' has more than one transition for the same outcome",
                        step.id()));
            }
        }
    }

    /** A graph that contains only one of FORK/JOIN is structurally inconsistent. */
    private void checkForkJoinConsistency(List<WorkflowStep> steps,
                                          List<WorkflowDefinitionValidation.Error> errors) {
        boolean hasFork = steps.stream().anyMatch(s -> s.stepType() == WorkflowStep.StepType.PARALLEL_FORK);
        boolean hasJoin = steps.stream().anyMatch(s -> s.stepType() == WorkflowStep.StepType.PARALLEL_JOIN);
        if (hasFork ^ hasJoin) {
            errors.add(new WorkflowDefinitionValidation.Error(
                    "FORK_JOIN_MISMATCH",
                    "Graph contains " + (hasFork ? "PARALLEL_FORK without PARALLEL_JOIN"
                            : "PARALLEL_JOIN without PARALLEL_FORK"),
                    null));
        }
    }

    /** SYSTEM_ACTION steps must declare a registered adapter token. */
    private void checkSystemActionConfiguration(List<WorkflowStep> steps,
                                                List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.SYSTEM_ACTION) continue;
            String adapter = null;
            if (step.configuration() != null && !step.configuration().isBlank()) {
                try {
                    JsonNode config = objectMapper.readTree(step.configuration());
                    JsonNode node = config.get("adapter");
                    if (node != null && !node.isNull()) adapter = node.asText();
                } catch (Exception ignored) {
                    // STEP_CONFIG_INVALID already reported
                }
            }
            if (adapter == null || adapter.isBlank()) {
                errors.add(new WorkflowDefinitionValidation.Error(
                        "SYSTEM_ACTION_CONFIG_MISSING",
                        "System action step '" + step.stepKey()
                                + "' must declare configuration.adapter",
                        step.id()));
            }
        }
    }

    /**
     * Assignment configuration must reference the sealed assignment-rule
     * vocabulary with a resolvable structural target. Validation stays
     * metadata-only: existence of referenced employees/roles/capabilities is
     * resolved (fail-closed) at runtime, not here.
     */
    private void checkAssignmentTypeValidity(List<WorkflowStep> steps,
                                             List<WorkflowDefinitionValidation.Error> errors) {
        for (WorkflowStep step : steps) {
            if (step.stepType() != WorkflowStep.StepType.HUMAN_TASK
                    && step.stepType() != WorkflowStep.StepType.APPROVAL) continue;
            if (step.configuration() == null || step.configuration().isBlank()) continue;
            try {
                JsonNode config = objectMapper.readTree(step.configuration());
                JsonNode assignment = config.get("assignment");
                if (assignment == null || !assignment.isObject() || !assignment.has("type")) continue;
                String type = assignment.get("type").asText("").toUpperCase(Locale.ROOT);
                if (!ASSIGNMENT_TYPES.contains(type)) {
                    errors.add(new WorkflowDefinitionValidation.Error(
                            "ASSIGNMENT_CONFIG_INVALID",
                            "Assignment type '" + type + "' on step '" + step.stepKey()
                                    + "' is not a supported assignment rule",
                            step.id()));
                    continue;
                }
                if (!assignmentTargetPresent(assignment, type)) {
                    errors.add(new WorkflowDefinitionValidation.Error(
                            "ASSIGNMENT_CONFIG_INVALID",
                            "Assignment rule on step '" + step.stepKey()
                                    + "' of type " + type + " requires a target",
                            step.id()));
                }
            } catch (Exception ignored) {
                // STEP_CONFIG_INVALID already reported
            }
        }
    }

    private boolean assignmentTargetPresent(JsonNode assignment, String type) {
        String target = textOr(assignment.get("target"));
        return switch (type) {
            case "EMPLOYEE" -> uuidOr(textOr(assignment.get("employeeId")), target) != null;
            case "ROLE" -> !textOr(assignment.get("roleCode")).isBlank()
                    || !textOr(assignment.get("role")).isBlank() || !target.isBlank();
            case "PERMISSION" -> !textOr(assignment.get("capabilityCode")).isBlank()
                    || !textOr(assignment.get("capability")).isBlank() || !target.isBlank();
            case "POSITION" -> !textOr(assignment.get("positionId")).isBlank() || !target.isBlank();
            case "DEPARTMENT" -> !textOr(assignment.get("departmentId")).isBlank() || !target.isBlank();
            default -> true; // MANAGER resolves its subject at runtime
        };
    }

    private String textOr(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private UUID uuidOr(String primary, String fallback) {
        String candidate = primary == null || primary.isBlank() ? fallback : primary;
        if (candidate == null || candidate.isBlank()) return null;
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
