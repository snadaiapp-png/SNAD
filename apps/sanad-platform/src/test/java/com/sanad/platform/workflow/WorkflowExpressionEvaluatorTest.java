package com.sanad.platform.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowContextService;
import com.sanad.platform.workflow.application.WorkflowExpressionEvaluator;
import com.sanad.platform.workflow.domain.WorkflowExpression;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 2 / Task 11 — typed context and safe expression AST (S3/U3).
 *
 * <p>Proves expressions evaluate typed context without executing code, that
 * depth and structure bounds fail closed, and that step output namespaces
 * are write-once.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowExpressionEvaluatorTest {

    @Autowired
    private WorkflowExpressionEvaluator evaluator;

    @Autowired
    private WorkflowContextService contextService;

    @Autowired
    private ObjectMapper objectMapper;

    private com.fasterxml.jackson.databind.JsonNode context() {
        try {
            return objectMapper.readTree("""
                    {
                      "source": {"amount": "100.00", "currency": "SAR"},
                      "requester": {"departmentId": "d-1"},
                      "stepOutputs": {"check": {"ok": true}}
                    }
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void expressionCanCompareTypedContextWithoutExecutingCode() {
        var expr = new WorkflowExpression.And(List.of(
                new WorkflowExpression.Equals("source/amount",
                        WorkflowExpression.WorkflowValue.of(new BigDecimal("100.00"))),
                new WorkflowExpression.In("source/currency", List.of(
                        WorkflowExpression.WorkflowValue.of("SAR"),
                        WorkflowExpression.WorkflowValue.of("USD")))));
        assertThat(evaluator.evaluate(expr, context())).isTrue();
    }

    @Test
    void comparisonOperatorsAndContainsWorkOnTypedValues() {
        var greater = new WorkflowExpression.Compare("source/amount",
                WorkflowExpression.Compare.Operator.GTE,
                WorkflowExpression.WorkflowValue.of(new BigDecimal("99.99")));
        assertThat(evaluator.evaluate(greater, context())).isTrue();

        var contains = new WorkflowExpression.Contains("source/currency",
                WorkflowExpression.WorkflowValue.of("AR"));
        assertThat(evaluator.evaluate(contains, context())).isTrue();

        var missing = new WorkflowExpression.Exists("source/discount");
        assertThat(evaluator.evaluate(missing, context())).isFalse();
    }

    @Test
    void expressionDepthIsBounded() {
        WorkflowExpression built = new WorkflowExpression.Exists("source/amount");
        for (int i = 0; i < 40; i++) {
            built = new WorkflowExpression.Not(built);
        }
        final WorkflowExpression deep = built;
        assertThatThrownBy(() -> evaluator.evaluate(deep, context()))
                .isInstanceOf(WorkflowExpressionEvaluator.WorkflowExpressionLimitException.class);
    }

    @Test
    void booleanGroupsWithoutItemsFailClosed() {
        assertThatThrownBy(() -> evaluator.evaluate(
                new WorkflowExpression.And(List.of()), context()))
                .isInstanceOf(WorkflowExpressionEvaluator.WorkflowExpressionLimitException.class);
    }

    @Test
    void stepOutputNamespaceIsWriteOnce() {
        var out = objectMapper.createObjectNode().put("total", 3);
        var written = contextService.writeStepOutput(null, "totals", out);
        assertThat(contextService.read(written, "/stepOutputs/totals/total").asInt()).isEqualTo(3);

        var replacement = objectMapper.createObjectNode().put("total", 4);
        assertThatThrownBy(() -> contextService.writeStepOutput(written, "totals", replacement))
                .isInstanceOf(IllegalStateException.class);
        // unchanged
        assertThat(contextService.read(written, "/stepOutputs/totals/total").asInt()).isEqualTo(3);
    }
}
