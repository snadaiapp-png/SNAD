package com.sanad.platform.crm.integration.orchestration;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, tenant-scoped envelope shared by CRM workflow and AI integration requests.
 * Client-controlled payloads must never be used to derive tenantId or actorId.
 */
public record IntegrationEnvelope(
        String contractName,
        String contractVersion,
        UUID tenantId,
        UUID actorId,
        String correlationId,
        String causationId,
        String idempotencyKey,
        String sourceEntityType,
        UUID sourceEntityId,
        long sourceEntityVersion,
        Instant requestedAt,
        Instant expiresAt,
        Locale locale,
        String requiredCapability,
        String dataClassification
) {
    public IntegrationEnvelope {
        contractName = requiredText(contractName, "contractName");
        contractVersion = requiredText(contractVersion, "contractVersion");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        actorId = Objects.requireNonNull(actorId, "actorId");
        correlationId = requiredText(correlationId, "correlationId");
        causationId = requiredText(causationId, "causationId");
        idempotencyKey = requiredText(idempotencyKey, "idempotencyKey");
        sourceEntityType = requiredText(sourceEntityType, "sourceEntityType");
        sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        if (sourceEntityVersion < 0) {
            throw new IllegalArgumentException("sourceEntityVersion must be non-negative");
        }
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("expiresAt must be after requestedAt");
        }
        locale = Objects.requireNonNullElse(locale, Locale.ENGLISH);
        requiredCapability = requiredText(requiredCapability, "requiredCapability");
        dataClassification = requiredText(dataClassification, "dataClassification");
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
