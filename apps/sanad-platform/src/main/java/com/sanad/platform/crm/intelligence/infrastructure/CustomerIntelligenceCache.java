package com.sanad.platform.crm.intelligence.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Caffeine-based cache for customer intelligence read models.
 *
 * <p>Cache strategy (per ADR):
 * <ul>
 *   <li>Customer360View: 5-minute TTL</li>
 *   <li>Customer Scores: 5-minute TTL</li>
 *   <li>Segment Memberships: 5-minute TTL</li>
 * </ul>
 *
 * <p>All cache keys are tenant-scoped to preserve isolation.
 */
@Component
public class CustomerIntelligenceCache implements CachePort {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_SIZE = 10_000;

    private final Cache<String, List<StoredScore>> scoresCache;
    private final Cache<String, Object> viewCache;

    public CustomerIntelligenceCache() {
        this.scoresCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .recordStats()
                .build();
        this.viewCache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .recordStats()
                .build();
    }

    // ── Scores Cache ──

    private static String scoresKey(UUID tenantId, UUID accountId) {
        return "scores:v1:" + tenantId + ":" + accountId;
    }

    @Override
    public List<StoredScore> getScores(UUID tenantId, UUID accountId) {
        return scoresCache.getIfPresent(scoresKey(tenantId, accountId));
    }

    @Override
    public void putScores(UUID tenantId, UUID accountId, List<StoredScore> scores) {
        scoresCache.put(scoresKey(tenantId, accountId), List.copyOf(scores));
    }

    @Override
    public void invalidateScores(UUID tenantId, UUID accountId) {
        scoresCache.invalidate(scoresKey(tenantId, accountId));
    }

    // ── View Cache ──

    private static String viewKey(UUID tenantId, UUID accountId) {
        return "view:v1:" + tenantId + ":" + accountId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getView(UUID tenantId, UUID accountId, Class<T> type) {
        Object cached = viewCache.getIfPresent(viewKey(tenantId, accountId));
        return cached != null && type.isInstance(cached) ? (T) cached : null;
    }

    @Override
    public <T> void putView(UUID tenantId, UUID accountId, T view) {
        if (view instanceof java.util.Map<?, ?> m) {
            viewCache.put(viewKey(tenantId, accountId), Collections.unmodifiableMap(new java.util.HashMap<>(m)));
        } else {
            viewCache.put(viewKey(tenantId, accountId), view);
        }
    }

    @Override
    public void invalidateView(UUID tenantId, UUID accountId) {
        viewCache.invalidate(viewKey(tenantId, accountId));
    }

    // ── Bulk Operations ──

    @Override
    public void invalidateAll(UUID tenantId, UUID accountId) {
        invalidateScores(tenantId, accountId);
        invalidateView(tenantId, accountId);
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats scoresStats() {
        return scoresCache.stats();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats viewStats() {
        return viewCache.stats();
    }
}
