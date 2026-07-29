package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.CustomerLifetimeValue;
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

class CustomerLifetimeValueServiceTest {

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
    private final CustomerLifetimeValueService service = new CustomerLifetimeValueService(
            aiOrchestrator, scoringPort, queryAdapter, eventPublisher,
            timeline, cache, validator, mapper);

    @BeforeEach
    void setUp() {
        AccountRecord account = new AccountRecord(
                ACCOUNT_ID, 0, "Test Account", "Test Account", "CUSTOMER", "ACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
        when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);
    }

    @Nested
    @DisplayName("calculateCLV")
    class CalculateCLVTests {

        @Test
        @DisplayName("should calculate AI-enhanced CLV when AI available")
        void shouldUseAiEnhanced() {
            // Arrange
            when(aiOrchestrator.buildClvIndicators(anyDouble(), anyInt(), anyDouble(),
                    anyInt(), anyDouble()))
                    .thenReturn(mapper.createObjectNode());
            AiGatewayPort.AiResult aiResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "AI projection", null, "CLV projection", 0.8,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(aiResult);

            StoredScore expected = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "CLV", 330000.0, "HIGH_VALUE",
                    "{}", 0.8, Instant.now(), "MANUAL", 0);
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.calculateCLV(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID,
                    100000.0, 50, 2000.0, 24, 0.15);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.scoreBand()).isEqualTo("HIGH_VALUE");
            verify(cache).invalidateAll(TENANT_ID, ACCOUNT_ID);
            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should fallback to linear projection when AI unavailable")
        void shouldFallbackToLinear() {
            // Arrange
            when(aiOrchestrator.buildClvIndicators(anyDouble(), anyInt(), anyDouble(),
                    anyInt(), anyDouble()))
                    .thenReturn(mapper.createObjectNode());
            when(aiOrchestrator.requestScore(any(), any(), any(), anyString(), any(), any()))
                    .thenReturn(null);

            StoredScore expected = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "CLV", 115000.0, "MID_VALUE",
                    "{}", 0.3, Instant.now(), "MANUAL", 0);
            when(scoringPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            // Act
            StoredScore result = service.calculateCLV(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID,
                    100000.0, 50, 2000.0, 24, 0.15);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.scoreValue()).isBetween(114999.0, 115001.0);
            verify(scoringPort).saveScore(eq(TENANT_ID), eq(ACCOUNT_ID), eq("CLV"),
                    anyDouble(), anyString(), anyString(), eq(0.3), eq("MANUAL"), eq(ACTOR_ID));
        }

        @Test
        @DisplayName("should reject inactive customer")
        void shouldRejectInactiveCustomer() {
            AccountRecord inactive = new AccountRecord(
                    ACCOUNT_ID, 0, "Inactive", "Inactive", "CUSTOMER", "INACTIVE", "USD", "en-US", "UTC", "MANUAL", null, ACTOR_ID, Instant.now(), Instant.now());
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(inactive);

            assertThatThrownBy(() -> service.calculateCLV(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID, 100000.0, 50, 2000.0, 24, 0.15))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }
    }

    @Nested
    @DisplayName("getLatestCLV")
    class GetLatestCLVTests {

        @Test
        @DisplayName("should return CLV when score exists")
        void shouldReturnClv() {
            StoredScore score = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "CLV", 150000.0, "HIGH_VALUE",
                    "{}", 0.8, Instant.now(), "MANUAL", 0);
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "CLV"))
                    .thenReturn(Optional.of(score));

            Optional<CustomerLifetimeValue> result = service.getLatestCLV(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isPresent();
            assertThat(result.get().predictedValue()).isEqualTo(150000.0);
            assertThat(result.get().tier()).isEqualTo("HIGH_VALUE");
        }

        @Test
        @DisplayName("should return empty when no score exists")
        void shouldReturnEmpty() {
            when(queryAdapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "CLV"))
                    .thenReturn(Optional.empty());

            Optional<CustomerLifetimeValue> result = service.getLatestCLV(TENANT_ID, ACCOUNT_ID);

            assertThat(result).isEmpty();
        }
    }
}
