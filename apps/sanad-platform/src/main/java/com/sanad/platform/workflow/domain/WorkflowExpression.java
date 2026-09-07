package com.sanad.platform.workflow.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Safe declarative condition AST (design decision U3). The parser accepts
 * only this closed, normalized structure — never strings compiled or
 * evaluated, never reflection, never DB/network/secret access.
 *
 * <p>Depth and complexity limits are enforced by
 * {@code WorkflowExpressionEvaluator}; publish-time validation rejects
 * structures beyond the bound (MAX_CONDITION_DEPTH).</p>
 */
public sealed interface WorkflowExpression {

    record And(List<WorkflowExpression> items) implements WorkflowExpression {}
    record Or(List<WorkflowExpression> items) implements WorkflowExpression {}
    record Not(WorkflowExpression item) implements WorkflowExpression {}

    record Equals(String path, WorkflowValue value) implements WorkflowExpression {}
    record NotEquals(String path, WorkflowValue value) implements WorkflowExpression {}
    record Compare(String path, Operator operator, WorkflowValue value) implements WorkflowExpression {
        public enum Operator { GT, GTE, LT, LTE }
    }
    record In(String path, List<WorkflowValue> values) implements WorkflowExpression {}
    record Exists(String path) implements WorkflowExpression {}
    record Contains(String path, WorkflowValue value) implements WorkflowExpression {}

    /** Typed literal values allowed inside expressions. */
    record WorkflowValue(Kind kind, String text, BigDecimal number, Boolean bool) {
        public enum Kind { STRING, NUMBER, BOOLEAN, NULL }

        public static WorkflowValue of(String value) {
            return new WorkflowValue(Kind.STRING, value, null, null);
        }

        public static WorkflowValue of(BigDecimal value) {
            return new WorkflowValue(Kind.NUMBER, null, value, null);
        }

        public static WorkflowValue of(boolean value) {
            return new WorkflowValue(Kind.BOOLEAN, null, null, value);
        }

        public static WorkflowValue nullValue() {
            return new WorkflowValue(Kind.NULL, null, null, null);
        }
    }
}
