package com.sanad.platform.security.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.access.evaluation.AuthorizationDecision;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Short-lived decision cache keyed by authorization_version. Immediate mutation
 * events evict every subject entry; version is also part of the key so a stale
 * ALLOW cannot be reused after a missed eviction.
 */
@Component
public class AuthorizationDecisionCache {
    private final Cache<Key, AuthorizationDecision> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(50_000)
            .build();

    public AuthorizationDecision getOrEvaluate(
            UUID tenantId, UUID userId, long authorizationVersion,
            String capabilityCode, UUID organizationId,
            Supplier<AuthorizationDecision> evaluator) {
        Key key = new Key(tenantId, userId, authorizationVersion, capabilityCode, organizationId);
        AuthorizationDecision cached = cache.getIfPresent(key);
        if (cached != null) return cached;
        AuthorizationDecision evaluated = evaluator.get();
        if (evaluated != null) cache.put(key, evaluated);
        return evaluated;
    }

    public void invalidateSubject(UUID tenantId, UUID userId) {
        cache.asMap().keySet().removeIf(key ->
                key.tenantId().equals(tenantId) && key.userId().equals(userId));
    }

    private record Key(UUID tenantId, UUID userId, long authorizationVersion,
                       String capabilityCode, UUID organizationId) {}
}
