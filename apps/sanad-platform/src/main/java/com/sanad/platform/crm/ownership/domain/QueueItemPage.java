package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.UUID;

/** Stable cursor page for currently queued CRM records. */
public record QueueItemPage(
        List<QueueItemSummary> items,
        UUID nextCursor,
        boolean hasMore
) {
    public QueueItemPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
