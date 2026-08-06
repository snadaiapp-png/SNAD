package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiScoreOrchestratorTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final AiGatewayPort aiGateway = mock(AiGatewayPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditPort audit = mock(AuditPort.class);
    private final CustomerIntelligenceProperties properties = new CustomerIntelligenceProperties();

    private final AiScoreOrchestrator orchestrator = new AiScoreOrchestrator(
            aiGateway, mapper, audit, properties);

    @BeforeEach
    void setUp() {
        properties.getScoring().setMinConfidence(0.6);
    }

    @Nested
    @DisplayName("requestScore")
    class RequestScoreTests {

        @Test
        @DisplayName("should return AI result when available")
        void shouldReturnAiResult() {
            // Arrange
            AiGatewayPort.AiResult aiResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.AVAILABLE, "Score: 85", null, "Health score: 85", 0.85,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiGateway.request(any(IntegrationEnvelope.class), any(), any()))
                    .thenReturn(aiResult);

            ObjectNode indicators = mapper.createObjectNode();
            indicators.put("metric", 50);

            // Act
            AiGatewayPort.AiResult result = orchestrator.requestScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID,
                    "crm.customer_intelligence.ai.health_scoring",
                    AiGatewayPort.Capability.SCORING, indicators);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(AiGatewayPort.Status.AVAILABLE);
            assertThat(result.confidence()).isEqualTo(0.85);
            verify(audit).record(eq(TENANT_ID), eq(ACTOR_ID), eq("AI_REQUEST"),
                    eq("INTEGRATION_REQUEST"), eq(ACCOUNT_ID), any(), any());
        }

        @Test
        @DisplayName("should return null when AI gateway throws exception")
        void shouldReturnNull_whenException() {
            // Arrange
            when(aiGateway.request(any(IntegrationEnvelope.class), any(), any()))
                    .thenThrow(new RuntimeException("Connection timeout"));

            ObjectNode indicators = mapper.createObjectNode();

            // Act
            AiGatewayPort.AiResult result = orchestrator.requestScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID,
                    "crm.customer_intelligence.ai.health_scoring",
                    AiGatewayPort.Capability.SCORING, indicators);

            // Assert
            assertThat(result).isNull();
            verify(audit).record(eq(TENANT_ID), eq(ACTOR_ID), eq("AI_REQUEST_FAILED"),
                    eq("INTEGRATION_REQUEST"), eq(ACCOUNT_ID), any(), any());
        }

        @Test
        @DisplayName("should log warning when confidence below threshold")
        void shouldLogWarning_whenLowConfidence() {
            // Arrange
            AiGatewayPort.AiResult lowConfResult = new AiGatewayPort.AiResult(
                    AiGatewayPort.Status.PARTIAL, "Low confidence", null, "Low confidence result", 0.4,
                    Instant.now(), Instant.now().plusSeconds(30), false, null, null, null);
            when(aiGateway.request(any(IntegrationEnvelope.class), any(), any()))
                    .thenReturn(lowConfResult);

            ObjectNode indicators = mapper.createObjectNode();

            // Act
            AiGatewayPort.AiResult result = orchestrator.requestScore(
                    TENANT_ID, ACCOUNT_ID, ACTOR_ID,
                    "crm.customer_intelligence.ai.health_scoring",
                    AiGatewayPort.Capability.SCORING, indicators);

            // Assert — should still return result (not null), just log warning
            assertThat(result).isNotNull();
            assertThat(result.confidence()).isEqualTo(0.4);
        }
    }

    @Nested
    @DisplayName("buildHealthIndicators")
    class BuildHealthIndicatorsTests {

        @Test
        @DisplayName("should build indicators with all fields")
        void shouldBuildAllFields() {
            com.fasterxml.jackson.databind.JsonNode result = orchestrator.buildHealthIndicators(
                    5, 3, 75000.0, 8, 2.5, "ACTIVE");

            assertThat(result.get("daysSinceLastActivity").asInt()).isEqualTo(5);
            assertThat(result.get("openOpportunities").asInt()).isEqualTo(3);
            assertThat(result.get("totalPipelineAmount").asDouble()).isEqualTo(75000.0);
            assertThat(result.get("meetingFrequency30d").asInt()).isEqualTo(8);
            assertThat(result.get("responseTimeAvgHours").asDouble()).isEqualTo(2.5);
            assertThat(result.get("lifecycleStatus").asText()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("buildClvIndicators")
    class BuildClvIndicatorsTests {

        @Test
        @DisplayName("should build CLV indicators with all fields")
        void shouldBuildClvFields() {
            com.fasterxml.jackson.databind.JsonNode result = orchestrator.buildClvIndicators(
                    100000.0, 50, 2000.0, 24, 0.15);

            assertThat(result.get("totalRevenue").asDouble()).isEqualTo(100000.0);
            assertThat(result.get("transactionCount").asInt()).isEqualTo(50);
            assertThat(result.get("avgDealSize").asDouble()).isEqualTo(2000.0);
            assertThat(result.get("customerSinceMonths").asInt()).isEqualTo(24);
            assertThat(result.get("growthRate").asDouble()).isEqualTo(0.15);
        }
    }

    @Nested
    @DisplayName("buildChurnIndicators")
    class BuildChurnIndicatorsTests {

        @Test
        @DisplayName("should build churn indicators with all fields")
        void shouldBuildChurnFields() {
            com.fasterxml.jackson.databind.JsonNode result = orchestrator.buildChurnIndicators(
                    30, 0.25, 3, 45);

            assertThat(result.get("daysSinceLastActivity").asInt()).isEqualTo(30);
            assertThat(result.get("engagementDeclinePct").asDouble()).isEqualTo(0.25);
            assertThat(result.get("openIssuesUnresolved").asInt()).isEqualTo(3);
            assertThat(result.get("contractRenewalDays").asInt()).isEqualTo(45);
        }
    }
}
