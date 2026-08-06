package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.HealthScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service for customer health scoring.
 * Delegates to {@link CustomerScoringService} for persistence and
 * {@link AiScoreOrchestrator} for AI computation.
 */
@Service
public class CustomerHealthService {

    private final CustomerScoringService scoringService;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;

    public CustomerHealthService(CustomerScoringService scoringService,
                                 CustomerIntelligenceQueryPortAdapter queryAdapter) {
        this.scoringService = scoringService;
        this.queryAdapter = queryAdapter;
    }

    @Transactional
    public StoredScore calculateHealth(UUID tenantId, UUID accountId, UUID actorId,
                                        int daysSinceLastActivity, int openOpportunities,
                                        double totalPipeline, int meetingFreq30d,
                                        double responseTimeAvgHours, String lifecycleStatus) {
        return scoringService.calculateHealthScore(tenantId, accountId, actorId,
                daysSinceLastActivity, openOpportunities, totalPipeline,
                meetingFreq30d, responseTimeAvgHours, lifecycleStatus);
    }

    public Optional<HealthScore> getLatestHealth(UUID tenantId, UUID accountId) {
        return queryAdapter.findLatestScore(tenantId, accountId, "HEALTH")
                .map(s -> new HealthScore(s.scoreValue(), s.scoreBand(), s.calculatedAt(), java.util.List.of()));
    }
}
