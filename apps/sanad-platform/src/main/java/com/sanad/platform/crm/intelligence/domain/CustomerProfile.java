package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for Customer 360 & Unified Customer Intelligence.
 *
 * <p>Represents a unified view of a customer with embedded intelligence scores,
 * segment memberships, and next-best-action recommendations.</p>
 */
public record CustomerProfile(
        UUID accountId,
        UUID tenantId,
        String displayName,
        String accountType,
        String lifecycleStatus,
        String customerSegment,
        String customerTier,
        String riskRating,
        CustomerScores scores,
        List<SegmentMembership> segments,
        Instant lastScoredAt,
        long version
) {
    public CustomerProfile {
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName is required");
    }
}
