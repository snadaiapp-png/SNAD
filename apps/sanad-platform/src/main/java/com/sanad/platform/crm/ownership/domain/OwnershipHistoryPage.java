package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Page of ownership history with cursor pagination.
 * The cursor is the last ID in the current page — pass it to fetch the next page.
 */
public record OwnershipHistoryPage(
        List<OwnershipHistory> entries,
        UUID nextCursor,
        boolean hasMore
) {
    public OwnershipHistoryPage {
        entries = List.copyOf(entries);
    }

    public static OwnershipHistoryPage empty() {
        return new OwnershipHistoryPage(List.of(), null, false);
    }
}
