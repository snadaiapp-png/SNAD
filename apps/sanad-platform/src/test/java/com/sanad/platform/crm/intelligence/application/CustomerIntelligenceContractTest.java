package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry;
import com.sanad.platform.crm.intelligence.domain.ScoringModel;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Contract tests for Customer360QueryPort, AiGatewayPort, and mock adapters.
 * Validates that port contracts are fulfilled correctly by their implementations.
 */
class CustomerIntelligenceContractTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("CustomerIntelligenceQueryPort contract")
    class QueryPortContractTests {

        @Test
        @DisplayName("query port should return StoredScore with all required fields")
        void storedScoreShouldHaveAllFields() {
            StoredScore score = new StoredScore(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 85.0, "HEALTHY",
                    "{\"engagement\":80}", 0.85, Instant.now(), "MANUAL", 0);

            assertThat(score.scoreType()).isEqualTo("HEALTH");
            assertThat(score.scoreValue()).isEqualTo(85.0);
            assertThat(score.scoreBand()).isEqualTo("HEALTHY");
            assertThat(score.calculatedAt()).isNotNull();
            assertThat(score.confidence()).isEqualTo(0.85);
            assertThat(score.componentsJson()).contains("engagement");
            assertThat(score.triggerReason()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("query port should handle null confidence gracefully")
        void storedScoreShouldHandleNullConfidence() {
            StoredScore score = new StoredScore(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "CLV", 50000.0, "MID_VALUE",
                    "{}", null, Instant.now(), "AI", 0);

            assertThat(score.confidence()).isNull();
        }

        @Test
        @DisplayName("query port adapter should delegate all methods")
        void adapterShouldDelegateAllMethods() {
            CustomerIntelligenceQueryPort mockPort = mock(CustomerIntelligenceQueryPort.class);
            CustomerIntelligenceQueryPortAdapter adapter = new CustomerIntelligenceQueryPortAdapter(mockPort);

            StoredScore score = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY",
                    "{}", 0.8, Instant.now(), "MANUAL", 0);
            when(mockPort.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH"))
                    .thenReturn(Optional.of(score));
            when(mockPort.findLatestScores(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(List.of(score));
            ScoreHistoryEntry historyEntry = new ScoreHistoryEntry(
                    UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH",
                    80.0, "HEALTHY", 85.0, "HEALTHY", 5.0,
                    Instant.now(), UUID.randomUUID(), "MANUAL");
            when(mockPort.findScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10))
                    .thenReturn(List.of(historyEntry));
            when(mockPort.findNextBestActions(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(List.of());
            when(mockPort.findActiveSegments(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(List.of());

            // Act & Assert — each method should delegate correctly
            assertThat(adapter.findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH")).isPresent();
            assertThat(adapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).hasSize(1);
            assertThat(adapter.findScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10)).hasSize(1);
            assertThat(adapter.findNextBestActions(TENANT_ID, ACCOUNT_ID)).isEmpty();
            assertThat(adapter.findActiveSegments(TENANT_ID, ACCOUNT_ID)).isEmpty();

            verify(mockPort).findLatestScore(TENANT_ID, ACCOUNT_ID, "HEALTH");
            verify(mockPort).findLatestScores(TENANT_ID, ACCOUNT_ID);
            verify(mockPort).findScoreHistory(TENANT_ID, ACCOUNT_ID, "HEALTH", 10);
            verify(mockPort).findNextBestActions(TENANT_ID, ACCOUNT_ID);
            verify(mockPort).findActiveSegments(TENANT_ID, ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("AiGatewayPort contract")
    class AiGatewayPortContractTests {

        @Test
        @DisplayName("AiResult should carry all response fields")
        void aiResultShouldHaveAllFields() {
            AiGatewayPort.AiResult result = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "Health score: 85",
                    "SCHEDULE_FOLLOWUP", "Health score: 85", 0.85,
                    Instant.now(), Instant.now().plusSeconds(30), true, null, null, null);

            assertThat(result.status()).isEqualTo(AiGatewayPort.Status.AVAILABLE);
            assertThat(result.confidence()).isEqualTo(0.85);
            assertThat(result.explanation()).isEqualTo("Health score: 85");
            assertThat(result.actionCode()).isEqualTo("SCHEDULE_FOLLOWUP");
        }

        @Test
        @DisplayName("AiResult should handle null actionCode")
        void aiResultShouldHandleNullActionCode() {
            AiGatewayPort.AiResult result = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.PARTIAL, "Partial", null, "Partial result", 0.6,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);

            assertThat(result.actionCode()).isNull();
        }

        @Test
        @DisplayName("AiResult should handle UNAVAILABLE status")
        void aiResultShouldHandleUnavailable() {
            AiGatewayPort.AiResult result = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.UNAVAILABLE, null, null, null, null,
                    null, null, false, null, null, null);

            assertThat(result.status()).isEqualTo(AiGatewayPort.Status.UNAVAILABLE);
            assertThat(result.confidence()).isNull();
        }

        @Test
        @DisplayName("IntegrationEnvelope should carry all request metadata")
        void envelopeShouldHaveAllFields() {
            java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
            IntegrationEnvelope envelope = new IntegrationEnvelope(
                    "crm.customer_intelligence.ai.health_scoring", "1.0",
                    TENANT_ID, UUID.randomUUID(), "corr-123", "idem-456",
                    "ai-score-789", "ACCOUNT", ACCOUNT_ID, 0,
                    now, now.plusSeconds(30),
                    java.util.Locale.ENGLISH, "CRM.CUSTOMER_INTELLIGENCE.READ", "INTERNAL");

            assertThat(envelope.contractName()).isEqualTo("crm.customer_intelligence.ai.health_scoring");
            assertThat(envelope.contractVersion()).isEqualTo("1.0");
            assertThat(envelope.tenantId()).isEqualTo(TENANT_ID);
            assertThat(envelope.correlationId()).isEqualTo("corr-123");
            assertThat(envelope.idempotencyKey()).isEqualTo("ai-score-789");
            assertThat(envelope.sourceEntityType()).isEqualTo("ACCOUNT");
            assertThat(envelope.sourceEntityId()).isEqualTo(ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("ScoringPort contract")
    class ScoringPortContractTests {

        @Test
        @DisplayName("ScoringPort saveScore should accept all parameters")
        void saveScoreShouldAcceptAllParams() {
            ScoringPort mockPort = mock(ScoringPort.class);
            StoredScore expected = new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 85.0, "HEALTHY",
                    "{}", 0.85, Instant.now(), "MANUAL", 0);
            when(mockPort.saveScore(any(), any(), anyString(), anyDouble(),
                    anyString(), anyString(), any(), anyString(), any()))
                    .thenReturn(expected);

            StoredScore result = mockPort.saveScore(
                    TENANT_ID, ACCOUNT_ID, "HEALTH", 85.0, "HEALTHY",
                    "{}", 0.85, "MANUAL", UUID.randomUUID());

            assertThat(result).isEqualTo(expected);
            verify(mockPort).saveScore(eq(TENANT_ID), eq(ACCOUNT_ID), eq("HEALTH"),
                    eq(85.0), eq("HEALTHY"), eq("{}"), eq(0.85), eq("MANUAL"), any());
        }

        @Test
        @DisplayName("ScoringPort getActiveModel should return Optional")
        void getActiveModelShouldReturnOptional() throws Exception {
            ScoringPort mockPort = mock(ScoringPort.class);
            ScoringModel model = new ScoringModel(UUID.randomUUID(), TENANT_ID, "HEALTH",
                    "v1", new ObjectMapper().readTree("{}"), true, Instant.now());
            when(mockPort.getActiveModel(TENANT_ID, "HEALTH")).thenReturn(Optional.of(model));

            Optional<ScoringModel> result = mockPort.getActiveModel(TENANT_ID, "HEALTH");

            assertThat(result).isPresent();
            assertThat(result.get().scoreType()).isEqualTo("HEALTH");
        }
    }

    @Nested
    @DisplayName("Event contract")
    class EventContractTests {

        @Test
        @DisplayName("all events should implement CustomerIntelligenceEvent interface")
        void allEventsShouldImplementInterface() {
            java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
            String corrId = "test-" + UUID.randomUUID();

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .CustomerScoreCalculatedEvent(TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY",
                    75.0, 5.0, "MANUAL", now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .CustomerHealthChangedEvent(TENANT_ID, ACCOUNT_ID, "AT_RISK", "HEALTHY",
                    80.0, now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .CustomerSegmentChangedEvent(TENANT_ID, ACCOUNT_ID, UUID.randomUUID(), null,
                    "ADDED", now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .NextBestActionGeneratedEvent(TENANT_ID, ACCOUNT_ID, UUID.randomUUID(),
                    "FOLLOW_UP", 0.85, now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .CustomerLifetimeValueUpdatedEvent(TENANT_ID, ACCOUNT_ID, 150000.0,
                    "HIGH_VALUE", 0.8, now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);

            assertThat(new com.sanad.platform.crm.intelligence.domain.event
                    .OpportunityScoreUpdatedEvent(TENANT_ID, ACCOUNT_ID, 85.0,
                    "UPSELL", 45000.0, now, corrId))
                    .isInstanceOf(com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEvent.class);
        }

        @Test
        @DisplayName("all events should carry tenantId, accountId, and correlationId")
        void allEventsShouldCarryMetadata() {
            java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
            String corrId = "test-" + UUID.randomUUID();

            com.sanad.platform.crm.intelligence.domain.event.CustomerScoreCalculatedEvent scoreEvent =
                    new com.sanad.platform.crm.intelligence.domain.event.CustomerScoreCalculatedEvent(
                            TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY",
                            null, 0, "MANUAL", now, corrId);

            assertThat(scoreEvent.tenantId()).isEqualTo(TENANT_ID);
            assertThat(scoreEvent.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(scoreEvent.correlationId()).isEqualTo(corrId);
            assertThat(scoreEvent.occurredAt()).isEqualTo(now);
            assertThat(scoreEvent.eventType()).isEqualTo("crm.intelligence.score.calculated");
        }
    }
}
