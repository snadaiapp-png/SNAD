package com.sanad.platform.crm.mobile.sync.model;

import java.time.Instant;
import java.util.Map;

/**
 * Response model for sync status endpoint.
 *
 * Requirements: API-005 (Sync Status API)
 */
public record SyncStatusResponse(
    String deviceId,
    Instant lastSyncAt,
    Map<String, EntitySyncStatus> entityStatuses,
    int pendingMutations,
    int unresolvedConflicts,
    String overallStatus
) {
    public record EntitySyncStatus(
        String entityType,
        Instant lastPullAt,
        long syncVersion,
        int localEntityCount
    ) {}
}
