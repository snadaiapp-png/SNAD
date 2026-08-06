package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.infrastructure.CustomerIntelligenceCache;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.query.domain.Customer360QueryPort;
import com.sanad.platform.crm.query.domain.Customer360QueryPort.Customer360View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class Customer360ApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private final Customer360QueryPort customer360QueryPort = mock(Customer360QueryPort.class);
    private final CustomerIntelligenceQueryPortAdapter intelligenceQuery = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);

    private final Customer360ApplicationService service = new Customer360ApplicationService(
            customer360QueryPort, intelligenceQuery, accountRepository, cache);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(cache.getView(TENANT_ID, ACCOUNT_ID, Map.class)).thenReturn(null);
    }

    @Nested
    @DisplayName("loadCustomer360")
    class LoadCustomer360Tests {

        @Test
        @DisplayName("should return cached view when available")
        void shouldReturnCachedView() {
            // Arrange
            Map<String, Object> cachedView = Map.of("accountId", ACCOUNT_ID, "cached", true);
            when(cache.getView(TENANT_ID, ACCOUNT_ID, Map.class)).thenReturn(cachedView);

            // Act
            Map<String, Object> result = service.loadCustomer360(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEqualTo(cachedView);
            verifyNoInteractions(customer360QueryPort, intelligenceQuery, accountRepository);
        }

        @Test
        @DisplayName("should build unified view from all sources")
        void shouldBuildUnifiedView() {
            // Arrange
            AccountRecord account = new AccountRecord(
                    ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, UUID.randomUUID(), Instant.now(), Instant.now());
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);

            Customer360View baseView = new Customer360View(
                    ACCOUNT_ID, "Test Account", "CUSTOMER", "ACTIVE",
                    0, 0, 0, 0,
                    List.of(), List.of(), List.of());
            when(customer360QueryPort.getCustomer360(TENANT_ID, ACCOUNT_ID)).thenReturn(baseView);

            when(intelligenceQuery.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY", "{}", 0.8, Instant.now(), "MANUAL", 0)));
            when(intelligenceQuery.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(intelligenceQuery.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());

            // Act
            Map<String, Object> result = service.loadCustomer360(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result.get("accountId")).isEqualTo(ACCOUNT_ID);
            assertThat(result.get("tenantId")).isEqualTo(TENANT_ID);
            assertThat(result.get("displayName")).isEqualTo("Test Account");
            assertThat(result.get("primaryCurrencyCode")).isEqualTo("USD");
            assertThat(result).containsKey("intelligence");

            @SuppressWarnings("unchecked")
            Map<String, Object> intelligence = (Map<String, Object>) result.get("intelligence");
            assertThat(intelligence).containsKey("scores");

            verify(cache).putView(TENANT_ID, ACCOUNT_ID, result);
        }

        @Test
        @DisplayName("should handle null account record gracefully")
        void shouldHandleNullAccount() {
            // Arrange
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);
            Customer360View baseView = new Customer360View(
                    ACCOUNT_ID, "Test Account", "CUSTOMER", "ACTIVE",
                    0, 0, 0, 0,
                    List.of(), List.of(), List.of());
            when(customer360QueryPort.getCustomer360(TENANT_ID, ACCOUNT_ID)).thenReturn(baseView);
            when(intelligenceQuery.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(intelligenceQuery.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(intelligenceQuery.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());

            // Act
            Map<String, Object> result = service.loadCustomer360(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result.get("displayName")).isEqualTo("Test Account");
            assertThat(result).doesNotContainKey("primaryCurrencyCode");
        }
    }

    @Nested
    @DisplayName("getScores")
    class GetScoresTests {

        @Test
        @DisplayName("should return cached scores when available")
        void shouldReturnCached() {
            List<StoredScore> cached = List.of(
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY", "{}", 0.8, Instant.now(), "MANUAL", 0));
            when(cache.getScores(TENANT_ID, ACCOUNT_ID)).thenReturn(cached);

            List<StoredScore> result = service.getScores(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isEqualTo(cached);
            verifyNoInteractions(intelligenceQuery);
        }

        @Test
        @DisplayName("should query and cache when not cached")
        void shouldQueryAndCache() {
            when(cache.getScores(TENANT_ID, ACCOUNT_ID)).thenReturn(null);
            List<StoredScore> dbScores = List.of(
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY", "{}", 0.8, Instant.now(), "MANUAL", 0));
            when(intelligenceQuery.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(dbScores);

            List<StoredScore> result = service.getScores(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isEqualTo(dbScores);
            verify(cache).putScores(TENANT_ID, ACCOUNT_ID, dbScores);
        }
    }

    @Nested
    @DisplayName("getScoreHistory")
    class GetScoreHistoryTests {

        @Test
        @DisplayName("should delegate to query adapter")
        void shouldDelegate() {
            List<ScoreHistoryEntry> history = List.of();
            when(intelligenceQuery.findScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10))
                    .thenReturn(history);

            var result = service.getScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10);

            assertThat(result).isEqualTo(history);
        }
    }
}
