package com.sanad.platform.integration.zid.dto;

import java.time.Instant;

/**
 * Sync summary for the admin dashboard. Counts and timestamps only —
 * no payloads, no customer data, no token material.
 */
public record ZidSyncSummaryResponse(
        String status,
        String zidStoreId,
        String zidStoreName,
        Instant lastSyncAt,
        int productCount,
        int publishedProductCount,
        int stockCount,
        int orderCount,
        int failedWebhookEvents,
        int recentConflicts) {

    public static ZidSyncSummaryResponse notLinked() {
        return new ZidSyncSummaryResponse("NOT_LINKED", null, null, null,
                0, 0, 0, 0, 0, 0);
    }
}
