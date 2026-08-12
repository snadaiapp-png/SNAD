package com.sanad.platform.crm.mobile.conflict.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * Response model for conflict operations.
 *
 * Requirements: ARCH-002 (12 Conflict Classes), SYNC-005 (Conflict Detection)
 */
public record ConflictResponse(
    String conflictId,
    String entityType,
    String entityId,
    String conflictType,
    String conflictClass,
    String status,
    JsonNode clientState,
    JsonNode serverState,
    JsonNode conflictMetadata,
    Instant detectedAt,
    String resolution
) {
    public record ConflictListResponse(
        int totalConflicts,
        List<ConflictResponse> conflicts,
        int openCount,
        int resolvedCount
    ) {}
}
