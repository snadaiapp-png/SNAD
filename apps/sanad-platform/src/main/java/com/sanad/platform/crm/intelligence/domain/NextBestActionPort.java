package com.sanad.platform.crm.intelligence.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Write port for next-best-action lifecycle.
 */
public interface NextBestActionPort {

    /**
     * Create a new next-best-action recommendation.
     */
    NextBestAction create(UUID tenantId, UUID accountId, String actionCode,
                          String description, double confidence, String reasoning,
                          java.time.Instant expiresAt, boolean humanConfirmationRequired);

    /**
     * Transition a NBA to ACCEPTED or REJECTED.
     */
    Optional<NextBestAction> resolve(UUID tenantId, UUID actionId, String resolution,
                                      UUID resolvedBy, long expectedVersion);

    /**
     * Expire stale NBAs past their expiry.
     */
    int expireStale(UUID tenantId);
}
