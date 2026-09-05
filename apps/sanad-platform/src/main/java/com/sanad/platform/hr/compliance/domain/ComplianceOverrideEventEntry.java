package com.sanad.platform.hr.compliance.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * WS3-facing domain event entry handed to {@code ComplianceEventPort}.
 * Event type names follow the HRM versioned naming pattern
 * {@code HRM.COMPLIANCE_OVERRIDE.<ACTION>.v1}. Payload metadata must be
 * redacted/safe; the WS4 adapter applies the central redaction guard before
 * persisting to the producer-local outbox in the caller's transaction.
 */
public record ComplianceOverrideEventEntry(
        UUID tenantId,
        UUID requestId,
        String eventType,
        UUID actorUserId,
        UUID correlationId,
        UUID causationId,
        JsonNode payload) {
}
