package com.sanad.platform.workflow.application;

import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionValidation;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

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
     * follows the highest-priority outgoing transition whose condition matches
     * the supplied context and never executes any
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
        List<UUID> traversal = new ArrayList<>();
        LinkedList<UUID> queue = new LinkedList<>();
        var contextNode = JSON.valueToTree(context);
        steps.stream()
                .filter(s -> s.stepType() == WorkflowStep.StepType.START)
                .findFirst()
                .ifPresent(start -> queue.add(start.id()));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) continue;
            traversal.add(current);
            transitions.stream()
                    .filter(t -> t.fromStepId().equals(current))
                    .sorted(Comparator.comparingInt(WorkflowTransition::priority)
                            .thenComparing(WorkflowTransition::transitionKey))
                    .filter(t -> conditionMatches(t.conditionAst(), contextNode))
                    .findFirst()
                    .ifPresent(t -> queue.add(t.toStepId()));
        }

        log.info("Workflow simulation executed (stub-only): tenant={} definition={} visitedSteps={}",
                tenantId, definitionId, visited.size());
        return new SimulationResult(
                true,
                true,
                List.copyOf(traversal),
                List.of(
                        "Simulation is non-production: no source-module side effects occurred",
                        "System actions, notifications, and sub-workflows were stubbed",
                        "Transition conditions were evaluated against the supplied context"),
                validation);
    }

    private boolean conditionMatches(String conditionAst,
            com.fasterxml.jackson.databind.JsonNode context) {
        if (conditionAst == null || conditionAst.isBlank() || conditionAst.equals("{}")) return true;
        try {
            var ast = JSON.readTree(conditionAst);
            var conditions = ast.path("conditions");
            if (!conditions.isArray() || conditions.isEmpty()) return false;
            boolean useOr = "OR".equalsIgnoreCase(ast.path("type").asText("AND"));
            boolean result = !useOr;
            for (var clause : conditions) {
                boolean matched = evaluateClause(context, clause);
                result = useOr ? result || matched : result && matched;
            }
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Workflow simulation skipped malformed condition AST", ex);
            return false;
        }
    }

    private boolean evaluateClause(com.fasterxml.jackson.databind.JsonNode context,
            com.fasterxml.jackson.databind.JsonNode clause) {
        var actual = resolve(context, clause.path("field").asText());
        var operator = clause.path("operator").asText();
        var expected = clause.path("value").asText();
        return switch (operator) {
            case "EXISTS" -> !actual.isMissingNode() && !actual.isNull();
            case "EQ" -> scalarEquals(actual, expected);
            case "NEQ" -> !scalarEquals(actual, expected);
            case "GT", "GTE", "LT", "LTE" -> compare(actual, expected, operator);
            case "IN", "NOT_IN" -> {
                boolean contains = java.util.Arrays.stream(expected.split(","))
                        .map(String::trim).anyMatch(item -> scalarEquals(actual, item));
                yield "IN".equals(operator) ? contains : !contains;
            }
            default -> false;
        };
    }

    private com.fasterxml.jackson.databind.JsonNode resolve(
            com.fasterxml.jackson.databind.JsonNode context, String dottedPath) {
        var current = context;
        for (var part : dottedPath.split("\\.")) current = current.path(part);
        return current;
    }

    private boolean scalarEquals(com.fasterxml.jackson.databind.JsonNode actual, String expected) {
        if (actual.isBoolean()) return actual.booleanValue() == Boolean.parseBoolean(expected);
        if (actual.isNumber()) {
            try { return actual.decimalValue().compareTo(new BigDecimal(expected)) == 0; }
            catch (NumberFormatException ignored) { return false; }
        }
        return actual.isValueNode() && actual.asText().equals(expected);
    }

    private boolean compare(com.fasterxml.jackson.databind.JsonNode actual, String expected, String operator) {
        if (!actual.isNumber()) return false;
        try {
            int comparison = actual.decimalValue().compareTo(new BigDecimal(expected));
            return switch (operator) {
                case "GT" -> comparison > 0;
                case "GTE" -> comparison >= 0;
                case "LT" -> comparison < 0;
                case "LTE" -> comparison <= 0;
                default -> false;
            };
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return false;
        }
    }
}
