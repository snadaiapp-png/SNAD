package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer's membership in a segment.
 */
public record SegmentMembership(
        UUID id,
        UUID tenantId,
        UUID accountId,
        UUID segmentId,
        String membershipType,
        Instant assignedAt,
        UUID assignedBy,
        boolean active
) {
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String TYPE_AUTO = "AUTO";

    public SegmentMembership {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (segmentId == null) throw new IllegalArgumentException("segmentId is required");
        if (membershipType == null) membershipType = TYPE_MANUAL;
        if (assignedAt == null) assignedAt = Instant.now();
    }
}
