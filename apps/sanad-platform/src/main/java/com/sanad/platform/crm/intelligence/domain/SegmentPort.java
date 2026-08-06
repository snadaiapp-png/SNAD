package com.sanad.platform.crm.intelligence.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for segment CRUD and membership management.
 */
public interface SegmentPort {

    /**
     * Create a new segment.
     */
    Segment createSegment(UUID tenantId, String segmentCode, String segmentName,
                          String segmentType, String description, String criteriaJson);

    /**
     * Find a segment by code.
     */
    Optional<Segment> findByCode(UUID tenantId, String segmentCode);

    /**
     * Assign an account to a segment.
     */
    SegmentMembership assignSegment(UUID tenantId, UUID accountId, UUID segmentId,
                                     String membershipType, UUID assignedBy);

    /**
     * Deactivate a segment membership.
     */
    void deactivateMembership(UUID tenantId, UUID accountId, UUID segmentId);

    /**
     * Find all active memberships for an account.
     */
    List<SegmentMembership> findActiveMemberships(UUID tenantId, UUID accountId);
}
