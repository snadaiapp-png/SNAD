package com.sanad.platform.crm.mobile.sync.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request model for batch sync push.
 * Client sends array of mutation envelopes; server returns per-mutation results.
 *
 * Requirements: API-004 (Batch Sync Push API), SYNC-017 (Per-Mutation ACK)
 */
public record PushSyncRequest(
    @NotEmpty @Valid List<MutationEnvelope> mutations
) {
    public record MutationEnvelope(
        String idempotencyKey,
        String entityType,
        String entityId,
        String operation, // "CREATE", "UPDATE", "DELETE"
        Long expectedVersion,
        JsonNode payload,
        String clientTimestamp
    ) {}
}
