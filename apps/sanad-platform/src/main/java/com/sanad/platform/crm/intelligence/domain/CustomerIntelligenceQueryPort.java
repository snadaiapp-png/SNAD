package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port for customer intelligence data.
 * Returns typed read models — never writes.
 */
public interface CustomerIntelligenceQueryPort {

    /**
     * Find the latest score for a given account and score type.
     */
    Optional<StoredScore> findLatestScore(UUID tenantId, UUID accountId, String scoreType);

    /**
     * Find all latest scores for an account (one per type).
     */
    List<StoredScore> findLatestScores(UUID tenantId, UUID accountId);

    /**
     * Find score history for an account and optional score type.
     */
    List<ScoreHistoryEntry> findScoreHistory(UUID tenantId, UUID accountId, String scoreType, int limit);

    /**
     * Find pending next-best-actions for an account.
     */
    List<NextBestAction> findNextBestActions(UUID tenantId, UUID accountId);

    /**
     * Find active segment memberships for an account.
     */
    List<SegmentMembership> findActiveSegments(UUID tenantId, UUID accountId);

    /**
     * Find all segments for a tenant.
     */
    List<Segment> findAllSegments(UUID tenantId);

    /**
     * Latest stored score row (maps to crm_customer_scores table).
     */
    record StoredScore(
            UUID id,
            UUID tenantId,
            UUID accountId,
            String scoreType,
            double scoreValue,
            String scoreBand,
            String componentsJson,
            Double confidence,
            Instant calculatedAt,
            String triggerReason,
            long version
    ) {}
}
