package com.sanad.platform.security.filter;

import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionVersionCache}.
 *
 * <p>Pins the CRM-033 root-cause fix: the JWT session-version lookup that
 * previously fired a SQL query on every authenticated request is served from a
 * short-TTL cache, and every {@code session_version} mutation path
 * ({@link #invalidate}) drops the entry so revocation still propagates at once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionVersionCacheTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Mock private UserRepository userRepository;

    private SessionVersionCache cache;

    @BeforeEach
    void setUp() {
        cache = new SessionVersionCache(userRepository);
    }

    @Test
    void get_withinTtl_servesFromCacheAndQueriesRepositoryOnce() {
        // Given the repository holds session_version = 7L
        when(userRepository.findSessionVersionByTenantIdAndId(TENANT, USER)).thenReturn(7L);

        // When several concurrent requests arrive within the TTL window
        Long first = cache.get(TENANT, USER);
        Long second = cache.get(TENANT, USER);
        Long third = cache.get(TENANT, USER);

        // Then all see the authoritative value
        assertThat(first).isEqualTo(7L);
        assertThat(second).isEqualTo(7L);
        assertThat(third).isEqualTo(7L);
        // And the database was hit exactly once (the CRM-033 bottleneck path)
        verify(userRepository, times(1)).findSessionVersionByTenantIdAndId(TENANT, USER);
    }

    @Test
    void invalidate_dropsCachedValue_soNextGetRequeriesRepository() {
        when(userRepository.findSessionVersionByTenantIdAndId(TENANT, USER))
                .thenReturn(7L)   // before logout / password change
                .thenReturn(8L);  // after session_version increment

        assertThat(cache.get(TENANT, USER)).isEqualTo(7L);   // loads + caches 7

        // Revocation path (logout / credential rotation / admin reset) invalidates
        cache.invalidate(TENANT, USER);

        assertThat(cache.get(TENANT, USER)).isEqualTo(8L);   // re-queries, sees new value
        verify(userRepository, times(2)).findSessionVersionByTenantIdAndId(TENANT, USER);
    }

    @Test
    void get_whenUserDeleted_returnsNull_andKeeps401PathLive() {
        // A deleted user resolves to null; the filter must 401 rather than
        // honour a stale cached "present" value. Caffeine does not cache the
        // null, so the lookup stays live (the documented, intended behaviour).
        when(userRepository.findSessionVersionByTenantIdAndId(TENANT, USER)).thenReturn(null);

        assertThat(cache.get(TENANT, USER)).isNull();
        assertThat(cache.get(TENANT, USER)).isNull();
        verify(userRepository, times(2)).findSessionVersionByTenantIdAndId(TENANT, USER);
    }
}
