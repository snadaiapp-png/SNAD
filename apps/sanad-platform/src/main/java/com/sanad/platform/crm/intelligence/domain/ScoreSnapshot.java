package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of all scores at a point in time.
 */
public record ScoreSnapshot(
        UUID snapshotId,
        UUID accountId,
        UUID tenantId,
        CustomerScores scores,
        Instant capturedAt,
        String triggerReason
) {
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_EVENT_DRIVEN = "EVENT_DRIVEN";

    public ScoreSnapshot {
        if (snapshotId == null) throw new IllegalArgumentException("snapshotId is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (scores == null) throw new IllegalArgumentException("scores is required");
        if (capturedAt == null) capturedAt = Instant.now();
        if (triggerReason == null) triggerReason = TRIGGER_SCHEDULED;
    }
}
