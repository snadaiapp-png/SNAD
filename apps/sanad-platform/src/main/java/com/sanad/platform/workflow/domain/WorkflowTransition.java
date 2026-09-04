package com.sanad.platform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Explicit graph transition between two steps of one concrete definition
 * version (design decision H3/R3). Runtime routing follows transitions;
 * {@code sequence_order} is presentation/backward-compatibility metadata.
 *
 * <p>{@code outcome} is the routing token consumed by the graph engine, for
 * example SUCCESS / APPROVE / REJECT / TRUE / FALSE. {@code conditionAst}
 * holds the normalized safe-expression AST (design decision U3) evaluated
 * only by the bounded expression engine — never arbitrary code.</p>
 */
public record WorkflowTransition(
        UUID id,
        UUID tenantId,
        UUID workflowDefinitionId,
        UUID fromStepId,
        UUID toStepId,
        String transitionKey,
        String outcome,
        String conditionAst,
        int priority,
        String metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowTransition {
        if (transitionKey == null || transitionKey.isBlank()) {
            throw new IllegalArgumentException("transitionKey must not be blank");
        }
        if (fromStepId == null || toStepId == null) {
            throw new IllegalArgumentException("transition endpoints must not be null");
        }
        if (fromStepId.equals(toStepId)) {
            throw new IllegalArgumentException("transition endpoints must differ");
        }
    }

    public static WorkflowTransition create(UUID tenantId, UUID definitionId,
            UUID fromStepId, UUID toStepId, String key, String outcome,
            String conditionAst, int priority, String metadata) {
        var now = Instant.now();
        return new WorkflowTransition(UUID.randomUUID(), tenantId, definitionId,
                fromStepId, toStepId, key, outcome, conditionAst, priority,
                metadata == null ? "{}" : metadata, now, now);
    }
}
