package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status;
import com.sanad.platform.crm.intelligence.domain.NextBestAction;
import com.sanad.platform.crm.intelligence.domain.event.CustomerIntelligenceEventPublisher;
import com.sanad.platform.crm.intelligence.domain.event.OpportunityScoreUpdatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for opportunity scoring and detection.
 */
@Service
public class OpportunityScoringService {

    private final AiScoreOrchestrator aiOrchestrator;
    private final NextBestActionService nbaService;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CustomerIntelligenceValidator validator;
    private final ObjectMapper mapper;

    public OpportunityScoringService(AiScoreOrchestrator aiOrchestrator,
                                     NextBestActionService nbaService,
                                     CustomerIntelligenceEventPublisher eventPublisher,
                                     TimelineEventPort timeline,
                                     CustomerIntelligenceValidator validator,
                                     ObjectMapper mapper) {
        this.aiOrchestrator = aiOrchestrator;
        this.nbaService = nbaService;
        this.eventPublisher = eventPublisher;
        this.timeline = timeline;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Transactional
    public NextBestAction detectOpportunity(UUID tenantId, UUID accountId, UUID actorId,
                                             String[] recentInquiries, String budgetIndicator,
                                             String decisionMakerEngagement) {
        validator.validateCustomer(tenantId, accountId);

        ObjectNode indicators = mapper.createObjectNode();
        indicators.put("recentInquiries", String.join(",", recentInquiries));
        indicators.put("budgetIndicator", budgetIndicator);
        indicators.put("decisionMakerEngagement", decisionMakerEngagement);

        var aiResult = aiOrchestrator.requestScore(
                tenantId, accountId, actorId,
                "crm.customer_intelligence.ai.opportunity_scoring",
                AiGatewayPort.Capability.SCORING, indicators);

        String actionCode = "CREATE_UPSELL_OPPORTUNITY";
        String description = "Upsell opportunity detected";
        double confidence = 0.7;
        double estimatedValue = 45000;
        String oppType = "UPSELL";

        if (aiResult != null && (aiResult.status() == Status.AVAILABLE || aiResult.status() == Status.PARTIAL)) {
            if (aiResult.confidence() != null) confidence = aiResult.confidence();
            if (aiResult.actionCode() != null && !aiResult.actionCode().isBlank()) {
                actionCode = aiResult.actionCode();
            }
        }

        NextBestAction nba = nbaService.generateRecommendation(
                tenantId, accountId, actorId, actionCode, description,
                confidence, "Opportunity detected via AI", true);

        eventPublisher.publish(new OpportunityScoreUpdatedEvent(
                tenantId, accountId, confidence * 100, oppType, estimatedValue,
                Instant.now(), "opp-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.opportunity.detected",
                "Opportunity: " + actionCode + " (score: " + (confidence * 100) + ")",
                "CRM_INTELLIGENCE", nba.actionId(), actorId, Instant.now());

        return nba;
    }
}
