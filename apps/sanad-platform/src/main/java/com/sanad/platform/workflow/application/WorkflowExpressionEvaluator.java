package com.sanad.platform.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.workflow.domain.WorkflowExpression;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bounded evaluator for the safe condition AST (design decision U3).
 *
 * <p>Reads only the typed context document handed to it. No database, no
 * network, no secrets, no reflection, no arbitrary code. Depth is capped;
 * exceeding the cap throws {@link WorkflowExpressionLimitException}.</p>
 */
@Service
public class WorkflowExpressionEvaluator {

    public static final int MAX_DEPTH = 32;
    private static final int MAX_ITEMS = 64;

    public boolean evaluate(WorkflowExpression expression, JsonNode context) {
        return evaluate(expression, context, 1);
    }

    private boolean evaluate(WorkflowExpression expression, JsonNode context, int depth) {
        if (depth > MAX_DEPTH) {
            throw new WorkflowExpressionLimitException(
                    "Expression depth exceeds the maximum of " + MAX_DEPTH);
        }
        if (expression instanceof WorkflowExpression.And and) {
            requireItems(and.items());
            return and.items().stream().allMatch(e -> evaluate(e, context, depth + 1));
        }
        if (expression instanceof WorkflowExpression.Or or) {
            requireItems(or.items());
            return or.items().stream().anyMatch(e -> evaluate(e, context, depth + 1));
        }
        if (expression instanceof WorkflowExpression.Not not) {
            return !evaluate(not.item(), context, depth + 1);
        }
        if (expression instanceof WorkflowExpression.Equals e) {
            return compareValues(resolve(context, e.path()), e.value()) == 0;
        }
        if (expression instanceof WorkflowExpression.NotEquals e) {
            return compareValues(resolve(context, e.path()), e.value()) != 0;
        }
        if (expression instanceof WorkflowExpression.Compare c) {
            int cmp = compareValues(resolve(context, c.path()), c.value());
            return switch (c.operator()) {
                case GT -> cmp > 0;
                case GTE -> cmp >= 0;
                case LT -> cmp < 0;
                case LTE -> cmp <= 0;
            };
        }
        if (expression instanceof WorkflowExpression.In in) {
            if (in.values().size() > MAX_ITEMS) {
                throw new WorkflowExpressionLimitException("IN list exceeds " + MAX_ITEMS + " items");
            }
            JsonNode actual = resolve(context, in.path());
            return in.values().stream().anyMatch(v -> compareValues(actual, v) == 0);
        }
        if (expression instanceof WorkflowExpression.Exists e) {
            return !resolve(context, e.path()).isMissingNode();
        }
        if (expression instanceof WorkflowExpression.Contains c) {
            JsonNode actual = resolve(context, c.path());
            if (actual.isTextual() && c.value().kind() == WorkflowExpression.WorkflowValue.Kind.STRING) {
                return actual.asText().contains(c.value().text());
            }
            if (actual.isArray()) {
                return actual.contains(jsonOf(c.value()));
            }
            return false;
        }
        throw new WorkflowExpressionLimitException(
                "Unsupported expression node: " + expression.getClass().getSimpleName());
    }

    private static final JsonNode MISSING =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.missingNode();

    /** Slash-separated path resolution into the typed context document. */
    private JsonNode resolve(JsonNode context, String path) {
        JsonNode current = context;
        for (String segment : path.split("/")) {
            if (segment.isBlank()) continue;
            if (current == null) return MISSING;
            current = current.get(segment);
        }
        return current == null ? MISSING : current;
    }

    private int compareValues(JsonNode actual, WorkflowExpression.WorkflowValue expected) {
        if (expected.kind() == WorkflowExpression.WorkflowValue.Kind.NULL) {
            return actual.isNull() || actual.isMissingNode() ? 0 : 1;
        }
        if (actual.isMissingNode() || actual.isNull()) {
            return -1;
        }
        return switch (expected.kind()) {
            case STRING -> actual.asText().compareTo(expected.text());
            case NUMBER -> {
                BigDecimal a;
                try {
                    a = new BigDecimal(actual.asText());
                } catch (NumberFormatException e) {
                    yield -1;
                }
                yield a.compareTo(expected.number());
            }
            case BOOLEAN -> Boolean.compare(actual.asBoolean(), expected.bool());
            case NULL -> actual.isNull() ? 0 : 1;
        };
    }

    private com.fasterxml.jackson.databind.node.JsonNode jsonOf(WorkflowExpression.WorkflowValue value) {
        var factory = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        return switch (value.kind()) {
            case STRING -> factory.textNode(value.text());
            case NUMBER -> factory.numberNode(value.number());
            case BOOLEAN -> factory.booleanNode(value.bool());
            case NULL -> factory.nullNode();
        };
    }

    private void requireItems(List<WorkflowExpression> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new WorkflowExpressionLimitException(
                    "Boolean groups accept 1.." + MAX_ITEMS + " items");
        }
    }

    /** Expression exceeded a structural or evaluation bound (U3). */
    public static class WorkflowExpressionLimitException extends IllegalStateException {
        public WorkflowExpressionLimitException(String message) {
            super(message);
        }
    }
}
