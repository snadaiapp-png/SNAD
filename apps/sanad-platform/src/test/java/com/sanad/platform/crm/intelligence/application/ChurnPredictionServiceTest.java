package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.RiskScore;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChurnPredictionServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final AiScoreOrchestrator aiOrchestrator = mock(AiScoreOrchestrator.class);
    private final ScoringPort scoringPort = mock(ScoringPort.class);
    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final CustomerIntelligenceEventPublisher eventPublisher = mock(CustomerIntelligenceEventPublisher.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);
    private final ChurnPredictionService service = new ChurnPredictionService(
            aiOrchestrator, scoringPort, queryAdapter, eventPublisher,
            timeline, cache, validator, mapper);

    @BeforeEach
    void setUp() {
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);
    }

    @Nested
    @DisplayName("predictChurnRisk")
    class PredictChurnRiskTests {

        @Test
        @DisplayName("should calculate risk score with AI when available")
        void shouldCalculateWithAi() {
            // Arrange
            when(aiOrchestrator.buildChurnIndicators(anyInt(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(mapper.createObjectNode());
            AiGatewayPort.AiResult aiResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "High risk", null, "Churn risk assessment", 0.8,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(aiResult);

            StoredScore expected = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "RISK", 75.0, "HIGH_RISK",
                    "{}", 0.8, Instant.now(), "MANUAL", 0);
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.predictChurnRisk(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 30, 0.25, 3, 45);

            // Assert
            assertThat(result).isNotNull();
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should use rule-based fallback when AI unavailable")
        void shouldUseFallback() {
            // Arrange
            when(aiOrchestrator.buildChurnIndicators(anyInt(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);

            StoredScore expected = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "RISK", 45.0, "MEDIUM_RISK",
                    "{}", null, Instant.now(), "MANUAL", 0);
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.predictChurnRisk(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 30, 0.25, 3, 45);

            // Assert
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should reject non-existent customer")
        void shouldRejectNonExistent() {
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.predictChurnRisk(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 30, 0.25, 3, 45))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }
    }

    @Nested
    @DisplayName("getLatestRisk")
    class GetLatestRiskTests {

        @Test
        @DisplayName("should return risk score when exists")
        void shouldReturnRisk() {
            StoredScore score = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "RISK", 75.0, "HIGH_RISK",
                    "{}", 0.8, Instant.now(), "MANUAL", 0);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "RISK"))
                    .thenReturn(Optional.of(score));

            Optional<RiskScore> result = service.getLatestRisk(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo(75.0);
            assertThat(result.get().band()).isEqualTo("HIGH_RISK");
        }

        @Test
        @DisplayName("should return empty when no risk score")
        void shouldReturnEmpty() {
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "RISK"))
                    .thenReturn(Optional.empty());

            Optional<RiskScore> result = service.getLatestRisk(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isEmpty();
        }
    }
}
