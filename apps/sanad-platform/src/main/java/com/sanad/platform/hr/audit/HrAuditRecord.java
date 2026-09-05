package com.sanad.platform.hr.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutation audit fact for the immutable HR audit ledger (WS4 Task 4).
 *
 * <p>Carries safe forensic metadata only: actor id, action, resource
 * type/id, organization/legal-entity ids, classification, reason code,
 * correlation/request ids, result, and redacted before/after state. The
 * central {@link HrRedactionGuard} masks known sensitive keys before
 * persistence — raw PII/secrets must never reach the ledger or the outbox.</p>
 */
public record HrAuditRecord(
        UUID tenantId,
        UUID actorUserId,
        String action,
        String resourceType,
        UUID resourceId,
        UUID organizationId,
        UUID legalEntityId,
        String dataClassification,
        String reason,
        JsonNode beforeState,
        JsonNode afterState,
        String result,
        UUID correlationId,
        UUID requestId,
        Instant occurredAt) {

    public HrAuditRecord {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(resourceType, "resourceType");
        if (result != null && !"SUCCESS".equals(result) && !"FAILURE".equals(result)) {
            throw new IllegalArgumentException("HRM_AUDIT_INVALID_RESULT: " + result);
        }
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
