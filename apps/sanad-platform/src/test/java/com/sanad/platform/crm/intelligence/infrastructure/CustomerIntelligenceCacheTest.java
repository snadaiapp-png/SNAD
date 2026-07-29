package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CustomerIntelligenceCache}.
 */
class CustomerIntelligenceCacheTest {

    private CustomerIntelligenceCache cache;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache = new CustomerIntelligenceCache();
    }

    @Test
    void putScores_thenGetReturnsSameScores() {
        List<StoredScore> scores = List.of(createScore("HEALTH", 75.0, "HEALTHY"));
        cache.putScores(TENANT_ID, ACCOUNT_ID, scores);

        List<StoredScore> retrieved = cache.getScores(TENANT_ID, ACCOUNT_ID);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).scoreType()).isEqualTo("HEALTH");
    }

    @Test
    void getScores_returnsNullWhenNotCached() {
        List<StoredScore> retrieved = cache.getScores(TENANT_ID, ACCOUNT_ID);
        assertThat(retrieved).isNull();
    }

    @Test
    void invalidateScores_clearsCache() {
        cache.putScores(TENANT_ID, ACCOUNT_ID, List.of(createScore("HEALTH", 75.0, "HEALTHY")));
        cache.invalidateScores(TENANT_ID, ACCOUNT_ID);
        assertThat(cache.getScores(TENANT_ID, ACCOUNT_ID)).isNull();
    }

    @Test
    void putView_thenGetReturnsSameView() {
        var view = java.util.Map.of("key", "value");
        cache.putView(TENANT_ID, ACCOUNT_ID, view);

        @SuppressWarnings("unchecked")
        var retrieved = (java.util.Map<String, String>) cache.getView(TENANT_ID, ACCOUNT_ID, java.util.Map.class);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.get("key")).isEqualTo("value");
    }

    @Test
    void getView_returnsNullWhenTypeDoesNotMatch() {
        cache.putView(TENANT_ID, ACCOUNT_ID, "string-value");
        Integer retrieved = cache.getView(TENANT_ID, ACCOUNT_ID, Integer.class);
        assertThat(retrieved).isNull();
    }

    @Test
    void invalidateAll_clearsBothCaches() {
        cache.putScores(TENANT_ID, ACCOUNT_ID, List.of(createScore("HEALTH", 75.0, "HEALTHY")));
        cache.putView(TENANT_ID, ACCOUNT_ID, "view");
        cache.invalidateAll(TENANT_ID, ACCOUNT_ID);
        assertThat(cache.getScores(TENANT_ID, ACCOUNT_ID)).isNull();
        assertThat(cache.getView(TENANT_ID, ACCOUNT_ID, String.class)).isNull();
    }

    @Test
    void tenantIsolation_differentTenantsDoNotShareCache() {
        UUID tenant2 = UUID.randomUUID();
        cache.putScores(TENANT_ID, ACCOUNT_ID, List.of(createScore("HEALTH", 75.0, "HEALTHY")));
        assertThat(cache.getScores(tenant2, ACCOUNT_ID)).isNull();
    }

    private StoredScore createScore(String type, double value, String band) {
        return new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, type, value, band,
                "{}", 0.85, Instant.now(), "MANUAL", 0);
    }
}
