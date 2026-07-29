package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.SegmentMembership;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate insight service combining all intelligence data
 * into a unified insight response for a customer.
 */
@Service
public class CustomerInsightService {

    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerHealthService healthService;
    private final CustomerLifetimeValueService clvService;
    private final ChurnPredictionService churnService;

    public CustomerInsightService(CustomerIntelligenceQueryPortAdapter queryAdapter,
                                  CustomerHealthService healthService,
                                  CustomerLifetimeValueService clvService,
                                  ChurnPredictionService churnService) {
        this.queryAdapter = queryAdapter;
        this.healthService = healthService;
        this.clvService = clvService;
        this.churnService = churnService;
    }

    /**
     * Get a complete intelligence summary for a customer.
     */
    public Map<String, Object> getCustomerInsights(UUID tenantId, UUID accountId) {
        Map<String, Object> insights = new HashMap<>();

        // Scores
        List<StoredScore> scores = queryAdapter.findLatestScores(tenantId, accountId);
        if (!scores.isEmpty()) {
            Map<String, Object> scoresMap = new HashMap<>();
            for (StoredScore score : scores) {
                Map<String, Object> scoreData = new HashMap<>();
                scoreData.put("value", score.scoreValue());
                scoreData.put("band", score.scoreBand());
                scoreData.put("calculatedAt", score.calculatedAt());
                scoresMap.put(score.scoreType().toLowerCase(), scoreData);
            }
            insights.put("scores", scoresMap);
        }

        // Next Best Actions
        List<NextBestAction> nbas = queryAdapter.findNextBestActions(tenantId, accountId);
        if (!nbas.isEmpty()) {
            insights.put("nextBestActions", nbas);
        }

        // Segments
        List<SegmentMembership> segments = queryAdapter.findActiveSegments(tenantId, accountId);
        if (!segments.isEmpty()) {
            insights.put("segments", segments);
        }

        // Summary assessment
        Map<String, String> summary = new HashMap<>();
        healthService.getLatestHealth(tenantId, accountId)
                .ifPresent(h -> summary.put("healthBand", h.band()));
        clvService.getLatestCLV(tenantId, accountId)
                .ifPresent(c -> summary.put("clvTier", c.tier()));
        churnService.getLatestRisk(tenantId, accountId)
                .ifPresent(r -> summary.put("riskBand", r.band()));
        if (!summary.isEmpty()) {
            insights.put("summary", summary);
        }

        return insights;
    }
}
