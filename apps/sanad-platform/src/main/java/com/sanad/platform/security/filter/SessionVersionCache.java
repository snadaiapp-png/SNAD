package com.sanad.platform.security.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Short-TTL in-process cache for JWT session-version validation.
 *
 * <p>{@code JwtAuthenticationFilter} validates, on every authenticated request,
 * that the {@code session_version} claim in the presented JWT still matches the
 * value stored on the user row (DEFECT-001 revocation control). Without caching
 * that lookup fires a SQL query per request and, under load, dominates tail
 * latency by serializing on the connection pool (measured: pool of 10 with 98
 * threads pending, acquire avg 137 ms / max 5.9 s).
 *
 * <p>This bean caches the scalar result for {@link #TTL_SECONDS} seconds. The
 * security posture is preserved because every path that increments
 * {@code session_version} (logout, password change, credential reset, admin
 * reset) calls {@link #invalidate}; the TTL is the fallback bound for any
 * out-of-band DB edit. This matches the granularity JWT expiry already imposes.
 *
 * <p>A {@code null} result (user deleted) is cached as absence via
 * {@link Cache} semantics only when wrapped; here we fetch-through and cache
 * the returned {@code Long} (which may be {@code null}). Caffeine does not cache
 * {@code null} by default from {@code get()}, so a deleted user re-queries —
 * acceptable and correct, since it keeps the 401 path live.
 */
@Component
public class SessionVersionCache {

    /** Cache TTL in seconds. Revocation propagation bound for out-of-band edits. */
    static final long TTL_SECONDS = 5L;

    private final UserRepository userRepository;
    private final Cache<Key, Long> cache;

    private record Key(UUID tenantId, UUID userId) { }

    public SessionVersionCache(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Returns the current session version for the user, serving it from cache
     * when fresh. Loads through to the repository on miss.
     *
     * @return the session version, or {@code null} if the user no longer exists
     */
    public Long get(UUID tenantId, UUID userId) {
        return cache.get(new Key(tenantId, userId),
                k -> userRepository.findSessionVersionByTenantIdAndId(k.tenantId(), k.userId()));
    }

    /**
     * Drops the cached entry for a user. Called immediately after any
     * {@code session_version} increment so revocation takes effect at once.
     */
    public void invalidate(UUID tenantId, UUID userId) {
        cache.invalidate(new Key(tenantId, userId));
    }
}
