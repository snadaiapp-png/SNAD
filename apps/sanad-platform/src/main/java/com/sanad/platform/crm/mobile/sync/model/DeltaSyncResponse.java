package com.sanad.platform.crm.mobile.sync.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * Response model for delta sync pull.
 * Returns changed entities since client's last cursor.
 *
 * Requirements: API-003 (Delta Sync Pull API), SYNC-002 (Delta Pull)
 */
public record DeltaSyncResponse(
    String entityType,
    String nextCursor,
    int entityCount,
    List<EntityDelta> entities,
    Instant serverTimestamp,
    boolean hasMore
) {
    public record EntityDelta(
        String entityId,
        String operation, // "CREATE", "UPDATE", "DELETE"
        long version,
        JsonNode data,
        Instant updatedAt
    ) {}
}
