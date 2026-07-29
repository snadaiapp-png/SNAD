package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import com.sanad.platform.crm.intelligence.domain.*;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.domain.event.CustomerScoreCalculatedEvent;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-level tests for the customer intelligence application services.
 * Verifies cross-service orchestration, AI gateway integration,
 * transaction boundary behavior, and event publication flow.
 */
class CustomerIntelligenceIntegrationTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ScoringPort scoringPort = mock(ScoringPort.class);
    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final AiScoreOrchestrator aiOrchestrator = mock(AiScoreOrchestrator.class);
    private final CustomerIntelligenceEventPublisher eventPublisher = mock(CustomerIntelligenceEventPublisher.class);
    private final TimelineEventPort timeline = mock(TimelineEventPort.class);
    private final CustomerIntelligenceCache cache = mock(CustomerIntelligenceCache.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);

    private CustomerScoringService scoringService;
    private CustomerHealthService healthService;
    private ChurnPredictionService churnService;
    private CustomerLifetimeValueService clvService;

    @BeforeEach
    void setUp() {
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);

        scoringService = new CustomerScoringService(
                scoringPort, queryAdapter, aiOrchestrator, eventPublisher,
                timeline, cache, validator, mapper);
        healthService = new CustomerHealthService(scoringService, queryAdapter);
        churnService = new ChurnPredictionService(
                aiOrchestrator, scoringPort, queryAdapter, eventPublisher,
                timeline, cache, validator, mapper);
        clvService = new CustomerLifetimeValueService(
                aiOrchestrator, scoringPort, queryAdapter, eventPublisher,
                timeline, cache, validator, mapper);
    }

    @Nested
    @DisplayName("AI Gateway orchestration flow")
    class AiOrchestrationFlowTests {

        @Test
        @DisplayName("should orchestrate AI score with audit trail")
        void shouldOrchestrateWithAuditTrail() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());

            // Simulate AI gateway returning a result
            AiGatewayPort.AiResult aiResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "Health score: 82", null, "AI health assessment", 0.82,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(aiResult);

            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        return new StoredScore(
                                null,
                                invocation.getArgument(0, UUID.class),
                                invocation.getArgument(1, UUID.class),
                                invocation.getArgument(2, String.class),
                                invocation.getArgument(3, Double.class),
                                invocation.getArgument(4, String.class),
                                invocation.getArgument(5, String.class),
                                invocation.getArgument(6, Double.class),
                                Instant.now(),
                                invocation.getArgument(7, String.class),
                                0L);
                    });

            // Act
            scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert — verify full orchestration chain
            verify(aiOrchestrator).requestScore(
                    eq(TENANT_ID), eq(ACCOUNT_ID), eq(ACTOR_ID),
                    eq("crm.customer_intelligence.ai.health_scoring"),
                    eq(AiGatewayPort.Capability.SCORING), any());
            verify(scoringPort).saveScore(eq(TENANT_ID), eq(ACCOUNT_ID), eq("HEALTH"),
                    anyDouble(), anyString(), anyString(), eq(0.82), eq("MANUAL"), eq(ACTOR_ID));
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher, atLeastOnce()).publish(any());
            verify(timeline).record(eq(TENANT_ID), eq("ACCOUNT"), eq(ACCOUNT_ID),
                    eq("crm.intelligence.health.calculated"), anyString(),
                    eq("CRM_INTELLIGENCE"), eq(ACCOUNT_ID), eq(ACTOR_ID), any());
        }

        @Test
        @DisplayName("should fall back gracefully when AI gateway fails")
        void shouldFallBackOnAiFailure() {
            // Arrange
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null); // AI unavailable

            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        return new StoredScore(
                                null,
                                invocation.getArgument(0, UUID.class),
                                invocation.getArgument(1, UUID.class),
                                invocation.getArgument(2, String.class),
                                invocation.getArgument(3, Double.class),
                                invocation.getArgument(4, String.class),
                                invocation.getArgument(5, String.class),
                                invocation.getArgument(6, Double.class),
                                Instant.now(),
                                invocation.getArgument(7, String.class),
                                0L);
                    });

            // Act — should not throw, should use rule-based fallback
            var result = scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert
            assertThat(result).isNotNull();
            verify(scoringPort).saveScore(eq(TENANT_ID), eq(ACCOUNT_ID), eq("HEALTH"),
                    anyDouble(), anyString(), anyString(), isNull(), eq("MANUAL"), eq(ACTOR_ID));
        }
    }

    @Nested
    @DisplayName("Event publication flow")
    class EventPublicationFlowTests {

        @Test
        @DisplayName("should publish score calculated event with correct fields")
        void shouldPublishScoreCalculatedEvent() {
            // Arrange
            AtomicReference<CustomerScoreCalculatedEvent> capturedEvent = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedEvent.set(invocation.getArgument(0));
                return null;
            }).when(eventPublisher).publish(any(CustomerScoreCalculatedEvent.class));

            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        return new StoredScore(
                                null,
                                invocation.getArgument(0, UUID.class),
                                invocation.getArgument(1, UUID.class),
                                invocation.getArgument(2, String.class),
                                invocation.getArgument(3, Double.class),
                                invocation.getArgument(4, String.class),
                                invocation.getArgument(5, String.class),
                                invocation.getArgument(6, Double.class),
                                Instant.now(),
                                invocation.getArgument(7, String.class),
                                0L);
                    });

            // Act
            scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert
            assertThat(capturedEvent.get()).isNotNull();
            assertThat(capturedEvent.get().tenantId()).isEqualTo(TENANT_ID);
            assertThat(capturedEvent.get().accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(capturedEvent.get().scoreType()).isEqualTo("HEALTH");
            assertThat(capturedEvent.get().correlationId()).startsWith("score-");
        }

        @Test
        @DisplayName("should publish health changed event only when band changes")
        void shouldPublishHealthChangedOnlyOnBandChange() {
            // Arrange — previous score has different band
            StoredScore prevScore = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 20.0, "CRITICAL",
                    "{}", 0.8, Instant.now().minusSeconds(3600), "MANUAL", 0);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.of(prevScore));

            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        return new StoredScore(
                                null,
                                invocation.getArgument(0, UUID.class),
                                invocation.getArgument(1, UUID.class),
                                invocation.getArgument(2, String.class),
                                invocation.getArgument(3, Double.class),
                                invocation.getArgument(4, String.class),
                                invocation.getArgument(5, String.class),
                                invocation.getArgument(6, Double.class),
                                Instant.now(),
                                invocation.getArgument(7, String.class),
                                0L);
                    });

            // Act
            scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 1, 5, 100000.0, 10, 1.0, "ACTIVE");

            // Assert — both events should be published (band changed from CRITICAL to HEALTHY)
            verify(eventPublisher, times(2)).publish(any());
        }
    }

    @Nested
    @DisplayName("Validation enforcement across services")
    class ValidationEnforcementTests {

        @Test
        @DisplayName("should reject scoring for non-existent customer")
        void shouldRejectScoringForNonExistent() {
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            assertThatThrownBy(() -> scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE"))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);

            verify(scoringPort, never()).saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("should reject CLV for inactive customer")
        void shouldRejectClvForInactive() {
            AccountRecord inactive = new AccountRecord(
                    ACCOUNT_ID, 0, "Inactive", "Inactive", "CUSTOMER", "INACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(inactive);

            assertThatThrownBy(() -> clvService.calculateCLV(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 100000.0, 50, 2000.0, 24, 0.15))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);

            verify(scoringPort, never()).saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("should reject churn prediction for non-existent customer")
        void shouldRejectChurnForNonExistent() {
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            assertThatThrownBy(() -> churnService.predictChurnRisk(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 30, 0.25, 3, 45))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);

            verify(scoringPort, never()).saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Cache behavior")
    class CacheBehaviorTests {

        @Test
        @DisplayName("should invalidate all caches after score calculation")
        void shouldInvalidateCacheAfterCalculation() {
            when(aiOrchestrator.buildHealthIndicators(anyInt(), anyInt(), anyDouble(),
                    anyInt(), anyDouble(), anyString()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.empty());
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenAnswer(invocation -> {
                        return new StoredScore(
                                null,
                                invocation.getArgument(0, UUID.class),
                                invocation.getArgument(1, UUID.class),
                                invocation.getArgument(2, String.class),
                                invocation.getArgument(3, Double.class),
                                invocation.getArgument(4, String.class),
                                invocation.getArgument(5, String.class),
                                invocation.getArgument(6, Double.class),
                                Instant.now(),
                                invocation.getArgument(7, String.class),
                                0L);
                    });

            // Act
            scoringService.calculateHealthScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 3, 2, 50000.0, 5, 2.0, "ACTIVE");

            // Assert
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(cache, never()).putScores(any(), any(), any());
        }

        @Test
        @DisplayName("should serve from cache when available")
        void shouldServeFromCache() {
            // Arrange
            java.util.List<CustomerIntelligenceQueryPort.StoredScore> cached = List.of(
                    new CustomerIntelligenceQueryPort.StoredScore(
                            UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY",
                            "{}", 0.8, Instant.now(), "MANUAL", 0));
            when(cache.getScores(TENANT_ID, ACCOUNT_ID)).thenReturn(cached);

            // Act
            var result = scoringService.getScores(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEqualTo(cached);
            verifyNoInteractions(queryAdapter);
        }
    }
}
