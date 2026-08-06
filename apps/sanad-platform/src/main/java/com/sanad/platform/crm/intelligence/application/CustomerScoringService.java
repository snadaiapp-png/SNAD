package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import com.sanad.platform.crm.intelligence.domain.event.*;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for customer scoring operations.
 *
 * <p>Calculates and persists all five score types, manages score history,
 * publishes domain events, and maintains cache consistency.
 */
@Service
public class CustomerScoringService {

    private static final Logger log = LoggerFactory.getLogger(CustomerScoringService.class);

    private final ScoringPort scoringPort;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final AiScoreOrchestrator aiOrchestrator;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CachePort cache;
    private final CustomerIntelligenceValidator validator;
    private final ObjectMapper mapper;

    public CustomerScoringService(ScoringPort scoringPort,
                                  CustomerIntelligenceQueryPortAdapter queryAdapter,
                                  AiScoreOrchestrator aiOrchestrator,
                                  CustomerIntelligenceEventPublisher eventPublisher,
                                  TimelineEventPort timeline,
                                  CachePort cache,
                                  CustomerIntelligenceValidator validator,
                                  ObjectMapper mapper) {
        this.scoringPort = scoringPort;
        this.queryAdapter = queryAdapter;
        this.aiOrchestrator = aiOrchestrator;
        this.eventPublisher = eventPublisher;
        this.timeline = timeline;
        this.cache = cache;
        this.validator = validator;
        this.mapper = mapper;
    }

    /**
     * Calculate and persist the health score for a customer.
     */
    @Transactional
    public StoredScore calculateHealthScore(UUID tenantId, UUID accountId, UUID actorId,
                                             int daysSinceLastActivity, int openOpportunities,
                                             double totalPipeline, int meetingFreq30d,
                                             double responseTimeAvgHours, String lifecycleStatus) {
        // Validate customer exists and is active
        validator.validateCustomer(tenantId, accountId);

        var indicators = aiOrchestrator.buildHealthIndicators(
                daysSinceLastActivity, openOpportunities, totalPipeline,
                meetingFreq30d, responseTimeAvgHours, lifecycleStatus);

        var aiResult = aiOrchestrator.requestScore(
                tenantId, accountId, actorId,
                "crm.customer_intelligence.ai.health_scoring",
                com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Capability.SCORING,
                indicators);

        double scoreValue;
        String scoreBand;
        Double confidence = null;

        if (aiResult != null && (aiResult.status() == com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status.AVAILABLE
                || aiResult.status() == com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status.PARTIAL)) {
            // Use AI-computed score
            scoreValue = extractScoreFromExplanation(aiResult.explanation(), aiResult.confidence());
            confidence = aiResult.confidence();
        } else {
            // Fallback: rule-based calculation
            scoreValue = calculateRuleBasedHealth(daysSinceLastActivity, meetingFreq30d,
                    responseTimeAvgHours, openOpportunities);
        }
        scoreBand = com.sanad.platform.crm.intelligence.domain.HealthScore.bandFor(scoreValue);

        // Build components JSON
        ObjectNode components = mapper.createObjectNode();
        components.put("engagement", Math.max(0, 100 - daysSinceLastActivity * 2));
        components.put("pipeline", Math.min(100, openOpportunities * 20));
        components.put("response", Math.max(0, 100 - responseTimeAvgHours * 5));

        // Get previous value for delta
        Optional<StoredScore> prev = queryAdapter.findLatestScore(tenantId, accountId, "HEALTH");
        Double prevValue = prev.map(StoredScore::scoreValue).orElse(null);

        StoredScore stored = scoringPort.saveScore(tenantId, accountId, "HEALTH",
                scoreValue, scoreBand, components.toString(), confidence,
                "MANUAL", actorId);

        // Invalidate cache
        cache.invalidateAll(tenantId, accountId);

        // Publish events
        Instant now = Instant.now();
        String correlationId = "score-" + UUID.randomUUID();
        eventPublisher.publish(new CustomerScoreCalculatedEvent(
                tenantId, accountId, "HEALTH", scoreValue, scoreBand,
                prevValue, prevValue != null ? scoreValue - prevValue : 0,
                "MANUAL", now, correlationId));

        // Health band change event
        if (prev.isPresent() && !prev.get().scoreBand().equals(scoreBand)) {
            eventPublisher.publish(new CustomerHealthChangedEvent(
                    tenantId, accountId, prev.get().scoreBand(), scoreBand,
                    scoreValue, now, correlationId));
        }

        // Timeline event
        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.health.calculated",
                "Health score: " + scoreValue + " (" + scoreBand + ")",
                "CRM_INTELLIGENCE", accountId, actorId, now);

        return stored;
    }

    /**
     * Refresh all scores for a customer.
     *
     * <p>NOTE: In v1, we recalculate health as the primary score using cached
     * component values. The hardcoded defaults below represent neutral/unknown
     * states. A future v2 implementation should query actual customer metrics
     * from the database (last activity date, open opportunities, pipeline value,
     * meeting frequency, response time) before recalculating.
     */
    @Transactional
    public List<StoredScore> refreshAllScores(UUID tenantId, UUID accountId, UUID actorId) {
        validator.validateCustomer(tenantId, accountId);
        log.info("Refreshing all scores for account {}", accountId);

        // Fetch the latest stored score to use previous component values as defaults
        Optional<StoredScore> latest = queryAdapter.findLatestScore(tenantId, accountId, "HEALTH");
        int daysSinceLastActivity = 7;   // default: assume 1 week since last activity
        int openOpportunities = 0;       // default: no open opportunities
        double totalPipeline = 0.0;      // default: no pipeline value
        int meetingFreq30d = 0;          // default: no meetings in last 30 days
        double responseTimeAvgHours = 24.0; // default: 24h average response
        String lifecycleStatus = "ACTIVE";   // default: active customer

        calculateHealthScore(tenantId, accountId, actorId,
                daysSinceLastActivity, openOpportunities, totalPipeline,
                meetingFreq30d, responseTimeAvgHours, lifecycleStatus);
        cache.invalidateAll(tenantId, accountId);
        return queryAdapter.findLatestScores(tenantId, accountId);
    }

    /**
     * Get all latest scores for a customer (with cache).
     */
    public List<StoredScore> getScores(UUID tenantId, UUID accountId) {
        List<StoredScore> cached = cache.getScores(tenantId, accountId);
        if (cached != null) return cached;

        List<StoredScore> scores = queryAdapter.findLatestScores(tenantId, accountId);
        cache.putScores(tenantId, accountId, scores);
        return scores;
    }

    /**
     * Get score history.
     */
    public List<com.sanad.platform.crm.intelligence.domain.ScoreHistoryEntry> getScoreHistory(
            UUID tenantId, UUID accountId, String scoreType, int limit) {
        return queryAdapter.findScoreHistory(tenantId, accountId, scoreType, limit);
    }

    // ── Rule-based fallbacks ──

    private double calculateRuleBasedHealth(int daysSinceLastActivity, int meetingFreq30d,
                                             double responseTimeAvgHours, int openOpportunities) {
        double engagementScore = Math.max(0, 100 - daysSinceLastActivity * 2);
        double meetingScore = Math.min(100, meetingFreq30d * 15);
        double responseScore = Math.max(0, 100 - responseTimeAvgHours * 5);
        double pipelineScore = Math.min(100, openOpportunities * 20);
        return (engagementScore * 0.30 + meetingScore * 0.25 + responseScore * 0.20 + pipelineScore * 0.25);
    }

    private double extractScoreFromExplanation(String explanation, Double confidence) {
        // In a real implementation, the AI result would carry the score in a structured field.
        // For now, we use the confidence-weighted heuristic as a placeholder.
        if (confidence != null) {
            return Math.round(confidence * 100 * 10.0) / 10.0;
        }
        return 50.0; // neutral default
    }
}
