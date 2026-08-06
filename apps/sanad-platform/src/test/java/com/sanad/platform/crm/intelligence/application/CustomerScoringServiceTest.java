package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.infrastructure.CustomerIntelligenceCache;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerScoringServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final ScoringPort scoringPort = mock(ScoringPort.class);
    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final AiScoreOrchestrator aiOrchestrator = mock(AiScoreOrchestrator.class);
    private final CustomerIntelligenceEventPublisher eventPublisher = mock(CustomerIntelligenceEventPublisher.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);
    private final CustomerScoringService service = new CustomerScoringService(
            scoringPort, queryAdapter, aiOrchestrator, eventPublisher,
            timeline, cache, validator, mapper);

    @BeforeEach
    void setUp() {
        // Stub validator: account exists and active
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);
    }

    @Nested
    @DisplayName("calculateHealthScore")
    class CalculateHealthScoreTests {

        @Test
        @DisplayName("should calculate AI-enhanced health score when AI available")
        void shouldUseAiScore_whenAiAvailable() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());

            AiGatewayPort.AiResult aiResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "Score: 85", null, "Health score: 85", 0.85,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(aiResult);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            StoredScore expected = mockStoredScore("HEALTH", 85.0, "HEALTHY");
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert
            assertThat(result).isNotNull();
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher, atLeastOnce()).publish(any());
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.health.calculated"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(ACCOUNT_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should fallback to rule-based when AI unavailable")
        void shouldUseRuleBased_whenAiUnavailable() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            StoredScore expected = mockStoredScore("HEALTH", 62.5, "HEALTHY");
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert
            assertThat(result).isNotNull();
            verify(scoringPort).saveScore(eq(TENANT_ID), eq(ACCOUNT_ID), eq("HEALTH"),
                    anyDouble(), anyString(), anyString(), isNull(), eq("MANUAL"), eq(ACTOR_ID));
        }

        @Test
        @DisplayName("should publish health changed event when band changes")
        void shouldPublishHealthChangedEvent_whenBandChanges() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);

            // Previous score with different band
            StoredScore prevScore = mockStoredScore("HEALTH", 20.0, "CRITICAL");
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.of(prevScore));
            StoredScore newScore = mockStoredScore("HEALTH", 80.0, "HEALTHY");
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(newScore);

            // Act
            service.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 1, 5, 100000.0, 10, 1.0, "ACTIVE");

            // Assert — should publish both ScoreCalculated and HealthChanged events
            verify(eventPublisher, times(2)).publish(any());
        }

        @Test
        @DisplayName("should reject inactive customer")
        void shouldRejectInactiveCustomer() {
            // Arrange
            AccountRecord inactiveAccount = new AccountRecord(
                    ACCOUNT_ID, 0, "Inactive", "Inactive", "CUSTOMER", "INACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(inactiveAccount);

            // Act & Assert
            assertThatThrownBy(() -> service.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE"))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> assertThat(((CustomerIntelligenceValidator.CustomerValidationException) e).code())
                            .isEqualTo("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("should reject non-existent customer")
        void shouldRejectNonExistentCustomer() {
            // Arrange
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE"))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> assertThat(((CustomerIntelligenceValidator.CustomerValidationException) e).code())
                            .isEqualTo("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("refreshAllScores")
    class RefreshAllScoresTests {

        @Test
        @DisplayName("should refresh all scores and return latest")
        void shouldRefreshScores() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(mockStoredScore("HEALTH", 50.0, "HEALTHY"));
            List<StoredScore> allScores = List.of(
                    mockStoredScore("HEALTH", 50.0, "HEALTHY"),
                    mockStoredScore("CLV", 25000.0, "MID_VALUE"));
            when(queryAdapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(allScores);

            // Act
            List<StoredScore> result = service.refreshAllScores(TENANT_ID, ACCOUNT_ID, ACTOR_ID);

            // Assert
            assertThat(result).hasSize(2);
            verify(cache, atLeastOnce()).invalidateAll(TENANT_ID, ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("getScores")
    class GetScoresTests {

        @Test
        @DisplayName("should return cached scores when available")
        void shouldReturnCachedScores() {
            // Arrange
            List<StoredScore> cached = List.of(mockStoredScore("HEALTH", 80.0, "HEALTHY"));
            when(cache.getScores(TENANT_ID, ACCOUNT_ID)).thenReturn(cached);

            // Act
            List<StoredScore> result = service.getScores(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEqualTo(cached);
            verifyNoInteractions(queryAdapter);
        }

        @Test
        @DisplayName("should query and cache when not cached")
        void shouldQueryAndCache_whenNotCached() {
            // Arrange
            when(cache.getScores(TENANT_ID, ACCOUNT_ID)).thenReturn(null);
            List<StoredScore> dbScores = List.of(mockStoredScore("HEALTH", 75.0, "HEALTHY"));
            when(queryAdapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(dbScores);

            // Act
            List<StoredScore> result = service.getScores(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEqualTo(dbScores);
            verify(cache).putScores(TENANT_ID, ACCOUNT_ID, dbScores);
        }
    }

    @Nested
    @DisplayName("getScoreHistory")
    class GetScoreHistoryTests {

        @Test
        @DisplayName("should delegate to query adapter")
        void shouldDelegateToQueryAdapter() {
            // Arrange
            List<ScoreHistoryEntry> history = List.of(
                    new ScoreHistoryEntry(null, TENANT_ID, ACCOUNT_ID, "HEALTH",
                            75.0, "HEALTHY", 80.0, "HEALTHY", 5.0,
                            Instant.now(), ACTOR_ID, "MANUAL"),
                    new ScoreHistoryEntry(null, TENANT_ID, ACCOUNT_ID, "HEALTH",
                            null, null, 75.0, "HEALTHY", 75.0,
                            Instant.now().minusSeconds(3600), ACTOR_ID, "MANUAL"));
            when(queryAdapter.findScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10))
                    .thenReturn(history);

            // Act
            var result = service.getScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10);

            // Assert
            assertThat(result).isEqualTo(history);
        }
    }

    private StoredScore mockStoredScore(String type, double value, String band) {
        return new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, type, value, band,
                "components", 0.85, Instant.now(), "MANUAL", 0);
    }
}
