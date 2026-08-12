package com.sanad.platform.crm.mobile.sync.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Response model for batch sync push.
 * Returns per-mutation ACK with status.
 *
 * Requirements: API-004 (Batch Sync Push API), SYNC-017 (Per-Mutation ACK)
 */
public record PushSyncResponse(
    int totalMutations,
    int applied,
    int rejected,
    int duplicates,
    List<MutationResult> results
) {
    public record MutationResult(
        String idempotencyKey,
        String entityId,
        String status, // "APPLIED", "REJECTED", "DUPLICATE", "CONFLICT"
        String httpStatus,
        Long serverVersion,
        String etag,
        JsonNode conflictInfo, // null if no conflict
        String errorMessage
    ) {}
}
