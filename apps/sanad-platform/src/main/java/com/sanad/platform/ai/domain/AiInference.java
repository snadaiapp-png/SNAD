package com.sanad.platform.ai.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * AI Inference — an immutable record of a single AI agent invocation.
 *
 * <p>ALL inferences are advisory-only. The {@code advisory} field is always TRUE
 * and cannot be set to FALSE — this enforces the AI safety principle that AI
 * output never directly mutates business state.
 *
 * <p>State machine: PENDING → COMPLETED | FAILED | TIMEOUT | CANCELLED
 *
 * <p>The inference log is append-only: no update or delete operations are
 * supported. This creates an immutable audit trail of every AI operation.
 */
public record AiInference(
        UUID id,
        UUID tenantId,
        UUID agentId,
        UUID invokedBy,
        String inputSummary,
        String inputHash,
        String outputSummary,
        String outputHash,
        boolean advisory,
        Status status,
        String errorMessage,
        Integer tokensInput,
        Integer tokensOutput,
        Long latencyMs,
        int costCents,
        UUID correlationId,
        String businessEntityType,
        UUID businessEntityId,
        UUID workflowInstanceId,
        Instant createdAt
) {
    public enum Status { PENDING, COMPLETED, FAILED, TIMEOUT, CANCELLED }

    /** Advisory is ALWAYS true — AI output never directly mutates business state. */
    public static final boolean ADVISORY_ONLY = true;

    public static AiInference start(
            UUID tenantId, UUID agentId, UUID invokedBy,
            String inputSummary, String inputHash,
            UUID correlationId,
            String businessEntityType, UUID businessEntityId) {
        var now = Instant.now();
        return new AiInference(
                UUID.randomUUID(), tenantId, agentId, invokedBy,
                inputSummary, inputHash,
                null, null,
                ADVISORY_ONLY, Status.PENDING, null,
                null, null, null, 0,
                correlationId, businessEntityType, businessEntityId, null,
                now
        );
    }

    public AiInference complete(String outputSummary, String outputHash,
                                 Integer tokensInput, Integer tokensOutput,
                                 Long latencyMs, int costCents,
                                 UUID workflowInstanceId) {
        requireStatus(Status.PENDING, "complete");
        return new AiInference(
                id, tenantId, agentId, invokedBy,
                inputSummary, inputHash,
                outputSummary, outputHash,
                ADVISORY_ONLY, Status.COMPLETED, null,
                tokensInput, tokensOutput, latencyMs, costCents,
                correlationId, businessEntityType, businessEntityId,
                workflowInstanceId,
                createdAt
        );
    }

    public AiInference fail(String errorMessage, Long latencyMs) {
        requireStatus(Status.PENDING, "fail");
        return new AiInference(
                id, tenantId, agentId, invokedBy,
                inputSummary, inputHash,
                null, null,
                ADVISORY_ONLY, Status.FAILED, errorMessage,
                null, null, latencyMs, 0,
                correlationId, businessEntityType, businessEntityId, null,
                createdAt
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
