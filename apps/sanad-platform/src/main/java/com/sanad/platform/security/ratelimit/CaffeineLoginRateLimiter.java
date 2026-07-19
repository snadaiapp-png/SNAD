package com.sanad.platform.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.security.config.SecurityProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Default in-memory {@link LoginRateLimiter} implementation backed by Caffeine.
 *
 * <p>Suitable for single-instance deployments and integration tests. A production
 * multi-instance deployment SHOULD register a distributed bean (e.g. Redis-backed)
 * and mark it {@link Primary} so it takes precedence over this in-memory default.
 *
 * <p>We intentionally avoid {@code @ConditionalOnMissingBean} here because sliced
 * Spring test contexts (e.g. {@code @WebMvcTest}) would otherwise fail with a
 * missing bean. A distributed adapter simply declares itself {@code @Primary} to
 * override this default in production.
 *
 * <p>Operators must register a distributed adapter (and document it) before going
 * multi-instance — see {@code docs/deployment/RUNTIME-CONFIGURATION-MATRIX.md}.
 */
@Component
public class CaffeineLoginRateLimiter implements LoginRateLimiter {

    private final Cache<String, Integer> failureCounts;
    private final int maxAttempts;
    private final Duration window;

    public CaffeineLoginRateLimiter(SecurityProperties securityProperties) {
        SecurityProperties.LoginRateLimit cfg = securityProperties.getLoginRateLimit();
        this.maxAttempts = cfg.getMaxAttempts();
        this.window = cfg.getWindow();
        this.failureCounts = Caffeine.newBuilder()
                .expireAfterWrite(window.toSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Decision check(String compositeKey) {
        Integer current = failureCounts.getIfPresent(compositeKey);
        if (current == null || current < maxAttempts) {
            return Decision.allow();
        }
        return Decision.deny(window);
    }

    @Override
    public void recordFailure(String compositeKey) {
        // Using get(key, k -> ...) atomically initializes the counter and is
        // safe under concurrent failures for the same key.
        failureCounts.asMap().compute(compositeKey, (k, current) -> {
            int next = (current == null ? 0 : current) + 1;
            return next >= maxAttempts ? next : next;
        });
    }

    @Override
    public void recordSuccess(String compositeKey) {
        failureCounts.invalidate(compositeKey);
    }
}
