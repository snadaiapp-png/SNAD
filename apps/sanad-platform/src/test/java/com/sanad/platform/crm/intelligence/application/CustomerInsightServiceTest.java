package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.HealthScore;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.CustomerLifetimeValue;
import com.sanad.platform.crm.intelligence.domain.RiskScore;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerInsightServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private final CustomerIntelligenceQueryPortAdapter queryAdapter = mock(CustomerIntelligenceQueryPortAdapter.class);
    private final CustomerHealthService healthService = mock(CustomerHealthService.class);
    private final CustomerLifetimeValueService clvService = mock(CustomerLifetimeValueService.class);
    private final ChurnPredictionService churnService = mock(ChurnPredictionService.class);

    private final CustomerInsightService service = new CustomerInsightService(
            queryAdapter, healthService, clvService, churnService);

    @Nested
    @DisplayName("getCustomerInsights")
    class GetCustomerInsightsTests {

        @Test
        @DisplayName("should aggregate all intelligence data")
        void shouldAggregateAll() {
            // Arrange
            when(queryAdapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY", "{}", 0.8, Instant.now(), "MANUAL", 0),
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "CLV", 150000.0, "HIGH_VALUE", "{}", 0.7, Instant.now(), "MANUAL", 0)));

            NextBestAction nba = new NextBestAction(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID,
                    "FOLLOW_UP", "Follow up", 0.85, "reason", "PENDING",
                    Instant.now(), Instant.now().plusSeconds(86400), true, null, null, 0);
            when(queryAdapter.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(nba));

            when(queryAdapter.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(
                    new SegmentMembership(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID,
                            UUID.randomUUID(), "MANUAL", Instant.now(), UUID.randomUUID(), true)));

            when(healthService.getLatestHealth(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(Optional.of(new HealthScore(80.0, "HEALTHY", Instant.now(), List.of())));
            when(clvService.getLatestCLV(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(Optional.of(new CustomerLifetimeValue(150000.0, 0, "HIGH_VALUE", Instant.now(), 0.8)));
            when(churnService.getLatestRisk(TENANT_ID, ACCOUNT_ID))
                    .thenReturn(Optional.of(new RiskScore(25.0, "LOW_RISK", Instant.now(), List.of())));

            // Act
            Map<String, Object> result = service.getCustomerInsights(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).containsKey("scores");
            assertThat(result).containsKey("nextBestActions");
            assertThat(result).containsKey("segments");
            assertThat(result).containsKey("summary");

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertThat(summary).containsEntry("healthBand", "HEALTHY");
            assertThat(summary).containsEntry("clvTier", "HIGH_VALUE");
            assertThat(summary).containsEntry("riskBand", "LOW_RISK");
        }

        @Test
        @DisplayName("should return empty map when no data exists")
        void shouldReturnEmptyMap() {
            // Arrange
            when(queryAdapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(queryAdapter.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(queryAdapter.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(healthService.getLatestHealth(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
            when(clvService.getLatestCLV(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
            when(churnService.getLatestRisk(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

            // Act
            Map<String, Object> result = service.getCustomerInsights(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should include scores map with correct keys")
        void shouldIncludeScoresMap() {
            // Arrange
            when(queryAdapter.findLatestScores(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of(
                    new StoredScore(UUID.randomUUID(), TENANT_ID, ACCOUNT_ID, "HEALTH", 80.0, "HEALTHY", "{}", 0.8, Instant.now(), "MANUAL", 0)));
            when(queryAdapter.findNextBestActions(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(queryAdapter.findActiveSegments(TENANT_ID, ACCOUNT_ID)).thenReturn(List.of());
            when(healthService.getLatestHealth(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
            when(clvService.getLatestCLV(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());
            when(churnService.getLatestRisk(TENANT_ID, ACCOUNT_ID)).thenReturn(Optional.empty());

            // Act
            Map<String, Object> result = service.getCustomerInsights(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).containsKey("scores");
            @SuppressWarnings("unchecked")
            Map<String, Object> scores = (Map<String, Object>) result.get("scores");
            assertThat(scores).containsKey("health");
            @SuppressWarnings("unchecked")
            Map<String, Object> health = (Map<String, Object>) scores.get("health");
            assertThat(health).containsEntry("value", 80.0);
            assertThat(health).containsEntry("band", "HEALTHY");
        }
    }
}
