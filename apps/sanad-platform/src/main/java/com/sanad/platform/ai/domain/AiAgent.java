package com.sanad.platform.ai.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * AI Agent — a registered AI agent definition.
 *
 * <p>State machine: DRAFT → ACTIVE → INACTIVE → ARCHIVED
 *
 * <p>Agents are advisory-only: their inferences never mutate business state directly.
 * When an agent proposes an action, the action must be reviewed and approved
 * through the Workflow Engine before any business state changes.
 */
public record AiAgent(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        Provider provider,
        String modelName,
        String systemPrompt,
        String configuration,
        Status status,
        Integer maxTokens,
        Double temperature,
        UUID createdBy,
        long versionLock,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { DRAFT, ACTIVE, INACTIVE, ARCHIVED }

    public enum Provider {
        DETERMINISTIC, OPENAI, ANTHROPIC, AZURE_OPENAI, CUSTOM
    }

    public static AiAgent create(
            UUID tenantId, String code, String name, String description,
            Provider provider, String modelName, String systemPrompt,
            String configuration, Integer maxTokens, Double temperature,
            UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new AiAgent(
                UUID.randomUUID(), tenantId, code, name, description,
                provider != null ? provider : Provider.DETERMINISTIC,
                modelName, systemPrompt, configuration,
                Status.DRAFT, maxTokens, temperature, createdBy,
                0, 0, now, now
        );
    }

    public AiAgent activate() {
        if (status != Status.DRAFT && status != Status.INACTIVE)
            throw new IllegalStateException("Cannot activate from " + status);
        return withStatus(Status.ACTIVE);
    }

    public AiAgent deactivate() {
        requireStatus(Status.ACTIVE, "deactivate");
        return withStatus(Status.INACTIVE);
    }

    public AiAgent archive() {
        requireStatus(Status.INACTIVE, "archive");
        return withStatus(Status.ARCHIVED);
    }

    private AiAgent withStatus(Status newStatus) {
        return new AiAgent(id, tenantId, code, name, description, provider, modelName,
                systemPrompt, configuration, newStatus, maxTokens, temperature, createdBy,
                versionLock + 1, version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
