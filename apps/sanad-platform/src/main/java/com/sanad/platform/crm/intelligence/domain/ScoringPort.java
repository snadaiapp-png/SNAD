package com.sanad.platform.crm.intelligence.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Write port for customer scoring operations.
 * All writes are tenant-scoped and audited.
 */
public interface ScoringPort {

    /**
     * Insert or update the latest score for an account/type.
     * Records the change in score_history.
     *
     * @return the stored score row
     */
    CustomerIntelligenceQueryPort.StoredScore saveScore(
            UUID tenantId,
            UUID accountId,
            String scoreType,
            double scoreValue,
            String scoreBand,
            String componentsJson,
            Double confidence,
            String triggerReason,
            UUID actorId);

    /**
     * Get the active scoring model for a tenant and score type.
     */
    Optional<ScoringModel> getActiveModel(UUID tenantId, String scoreType);

    /**
     * Create or activate a scoring model.
     */
    ScoringModel saveModel(UUID tenantId, String scoreType, String version,
                           String weightsJson, boolean active);
}
