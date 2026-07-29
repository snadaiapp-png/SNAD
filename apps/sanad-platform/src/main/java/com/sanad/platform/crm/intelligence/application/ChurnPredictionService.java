package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.RiskScore;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.domain.event.CustomerScoreCalculatedEvent;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for churn risk prediction.
 */
@Service
public class ChurnPredictionService {

    private final AiScoreOrchestrator aiOrchestrator;
    private final ScoringPort scoringPort;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CachePort cache;
    private final CustomerIntelligenceValidator validator;
    private final ObjectMapper mapper;

    public ChurnPredictionService(AiScoreOrchestrator aiOrchestrator,
                                  ScoringPort scoringPort,
                                  CustomerIntelligenceQueryPortAdapter queryAdapter,
                                  CustomerIntelligenceEventPublisher eventPublisher,
                                  TimelineEventPort timeline,
                                  CachePort cache,
                                  CustomerIntelligenceValidator validator,
                                  ObjectMapper mapper) {
        this.aiOrchestrator = aiOrchestrator;
        this.scoringPort = scoringPort;
        this.queryAdapter = queryAdapter;
        this.eventPublisher = eventPublisher;
        this.timeline = timeline;
        this.cache = cache;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Transactional
    public StoredScore predictChurnRisk(UUID tenantId, UUID accountId, UUID actorId,
                                         int daysSinceLastActivity, double engagementDeclinePct,
                                         int openIssuesUnresolved, int contractRenewalDays) {
        validator.validateCustomer(tenantId, accountId);

        var indicators = aiOrchestrator.buildChurnIndicators(
                daysSinceLastActivity, engagementDeclinePct,
                openIssuesUnresolved, contractRenewalDays);

        var aiResult = aiOrchestrator.requestScore(
                tenantId, accountId, actorId,
                "crm.customer_intelligence.ai.churn_prediction",
                AiGatewayPort.Capability.SCORING, indicators);

        double riskValue;
        Double confidence = null;

        if (aiResult != null && (aiResult.status() == Status.AVAILABLE || aiResult.status() == Status.PARTIAL)) {
            riskValue = Math.min(100, daysSinceLastActivity * 1.5 + engagementDeclinePct * 0.5);
            confidence = aiResult.confidence();
        } else {
            // Fallback: rule-based risk
            riskValue = Math.min(100, daysSinceLastActivity * 1.5 + engagementDeclinePct * 0.5);
        }

        String band = RiskScore.bandFor(riskValue);

        ObjectNode components = mapper.createObjectNode();
        components.put("daysSinceLastActivity", daysSinceLastActivity);
        components.put("engagementDeclinePct", engagementDeclinePct);
        components.put("openIssues", openIssuesUnresolved);

        StoredScore stored = scoringPort.saveScore(tenantId, accountId, "RISK",
                riskValue, band, components.toString(), confidence, "MANUAL", actorId);

        cache.invalidateAll(tenantId, accountId);

        eventPublisher.publish(new CustomerScoreCalculatedEvent(
                tenantId, accountId, "RISK", riskValue, band,
                null, 0, "MANUAL", Instant.now(), "risk-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.risk.calculated",
                "Risk score: " + riskValue + " (" + band + ")",
                "CRM_INTELLIGENCE", accountId, actorId, Instant.now());

        return stored;
    }

    public Optional<RiskScore> getLatestRisk(UUID tenantId, UUID accountId) {
        return queryAdapter.findLatestScore(tenantId, accountId, "RISK")
                .map(s -> new RiskScore(s.scoreValue(), s.scoreBand(), s.calculatedAt(), java.util.List.of()));
    }
}
