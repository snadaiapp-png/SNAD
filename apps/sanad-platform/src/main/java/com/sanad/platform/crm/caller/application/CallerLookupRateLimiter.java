package com.sanad.platform.crm.caller.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal anti-enumeration protection for caller lookups (G8-02 §29).
 *
 * <p>The platform's global {@code RateLimitFilter} exists but does not engage
 * for JWT-authenticated traffic (its tenant resolution does not match the
 * SANAD principal), so G8 applies the smallest compatible protection here:
 * a per-authenticated-caller windowed quota plus a short burst guard, both
 * in-memory (single-instance — consistent with
 * {@code CaffeineLoginRateLimiter}'s documented staging posture).
 */
@Component
public class CallerLookupRateLimiter {

    /** Lookups per caller per rolling window (per tenant+user). */
    static final int PER_CALLER_LIMIT = 60;
    /** Burst guard: lookups per caller per second. */
    static final int BURST_LIMIT = 10;
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final Duration BURST_WINDOW = Duration.ofSeconds(1);

    private final Cache<String, AtomicInteger> windowCounts;
    private final Cache<String, AtomicInteger> burstCounts;
    private final int perCallerLimit;
    private final int burstLimit;

    public CallerLookupRateLimiter() {
        this(PER_CALLER_LIMIT, BURST_LIMIT, WINDOW, BURST_WINDOW);
    }

    /** Test-harness constructor with tunable quotas. */
    public CallerLookupRateLimiter(int perCallerLimit, int burstLimit, java.time.Duration window) {
        this(perCallerLimit, burstLimit, window, BURST_WINDOW);
    }

    private CallerLookupRateLimiter(int perCallerLimit, int burstLimit,
                                    Duration window, Duration burstWindow) {
        this.perCallerLimit = perCallerLimit;
        this.burstLimit = burstLimit;
        this.windowCounts = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
        this.burstCounts = Caffeine.newBuilder().expireAfterWrite(burstWindow).maximumSize(100_000).build();
    }

    /** Returns true when the caller is within quota and may proceed. */
    public boolean tryAcquire(UUID tenantId, UUID userId) {
        String key = tenantId + ":" + userId;
        if (increment(windowCounts, key) > perCallerLimit) return false;
        return increment(burstCounts, key) <= burstLimit;
    }

    private static int increment(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger counter = cache.get(key, ignored -> new AtomicInteger());
        return counter.incrementAndGet();
    }
}
