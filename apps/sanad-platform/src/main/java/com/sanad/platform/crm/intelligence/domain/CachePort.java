package com.sanad.platform.crm.intelligence.domain;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;

import java.util.List;
import java.util.UUID;

/**
 * Port for caching customer intelligence read models.
 * Infrastructure implements this with Caffeine; application layer depends only on this interface.
 */
public interface CachePort {

    // ── Scores Cache ──

    List<StoredScore> getScores(UUID tenantId, UUID accountId);

    void putScores(UUID tenantId, UUID accountId, List<StoredScore> scores);

    void invalidateScores(UUID tenantId, UUID accountId);

    // ── View Cache ──

    <T> T getView(UUID tenantId, UUID accountId, Class<T> type);

    <T> void putView(UUID tenantId, UUID accountId, T view);

    void invalidateView(UUID tenantId, UUID accountId);

    // ── Bulk Operations ──

    void invalidateAll(UUID tenantId, UUID accountId);
}
