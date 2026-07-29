package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single entry in the score history (immutable audit trail).
 * Maps to crm_customer_score_history table.
 */
public record ScoreHistoryEntry(
        UUID id,
        UUID tenantId,
        UUID accountId,
        String scoreType,
        Double previousValue,
        String previousBand,
        double newValue,
        String newBand,
        double delta,
        Instant changedAt,
        UUID changedBy,
        String triggerReason
) {}
