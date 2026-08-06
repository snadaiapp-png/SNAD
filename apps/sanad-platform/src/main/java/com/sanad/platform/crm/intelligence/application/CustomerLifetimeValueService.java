package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status;
import com.sanad.platform.crm.intelligence.domain.CustomerIntelligenceQueryPort.StoredScore;
import com.sanad.platform.crm.intelligence.domain.CustomerLifetimeValue;
import com.sanad.platform.crm.intelligence.domain.ScoringPort;
import com.sanad.platform.crm.intelligence.domain.event.*;
import com.sanad.platform.crm.intelligence.domain.CachePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for Customer Lifetime Value (CLV) forecasting.
 */
@Service
public class CustomerLifetimeValueService {

    private final AiScoreOrchestrator aiOrchestrator;
    private final ScoringPort scoringPort;
    private final CustomerIntelligenceQueryPortAdapter queryAdapter;
    private final CustomerIntelligenceEventPublisher eventPublisher;
    private final TimelineEventPort timeline;
    private final CachePort cache;
    private final CustomerIntelligenceValidator validator;
    private final ObjectMapper mapper;

    public CustomerLifetimeValueService(AiScoreOrchestrator aiOrchestrator,
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
    public StoredScore calculateCLV(UUID tenantId, UUID accountId, UUID actorId,
                                     double totalRevenue, int transactionCount,
                                     double avgDealSize, int customerSinceMonths,
                                     double growthRate) {
        validator.validateCustomer(tenantId, accountId);

        var indicators = aiOrchestrator.buildClvIndicators(
                totalRevenue, transactionCount, avgDealSize, customerSinceMonths, growthRate);

        var aiResult = aiOrchestrator.requestScore(
                tenantId, accountId, actorId,
                "crm.customer_intelligence.ai.clv_forecast",
                AiGatewayPort.Capability.SCORING, indicators);

        double predictedValue;
        double confidence = 0.0;

        if (aiResult != null && (aiResult.status() == Status.AVAILABLE || aiResult.status() == Status.PARTIAL)) {
            predictedValue = totalRevenue * (1 + growthRate) * 2; // AI-enhanced projection
            confidence = aiResult.confidence() != null ? aiResult.confidence() : 0.5;
        } else {
            // Fallback: simple linear projection
            predictedValue = totalRevenue * (1 + growthRate);
            confidence = 0.3;
        }

        String tier = CustomerLifetimeValue.tierFor(predictedValue);

        ObjectNode components = mapper.createObjectNode();
        components.put("historicalRevenue", totalRevenue);
        components.put("projectedRevenue", predictedValue);
        components.put("growthRate", growthRate);

        StoredScore stored = scoringPort.saveScore(tenantId, accountId, "CLV",
                predictedValue, tier, components.toString(), confidence,
                "MANUAL", actorId);

        cache.invalidateAll(tenantId, accountId);

        eventPublisher.publish(new CustomerLifetimeValueUpdatedEvent(
                tenantId, accountId, predictedValue, tier, confidence,
                Instant.now(), "clv-" + UUID.randomUUID()));

        timeline.record(tenantId, "ACCOUNT", accountId,
                "crm.intelligence.clv.calculated",
                "CLV: " + predictedValue + " (" + tier + ")",
                "CRM_INTELLIGENCE", accountId, actorId, Instant.now());

        return stored;
    }

    public Optional<CustomerLifetimeValue> getLatestCLV(UUID tenantId, UUID accountId) {
        return queryAdapter.findLatestScore(tenantId, accountId, "CLV")
                .map(s -> new CustomerLifetimeValue(
                        s.scoreValue(), 0, s.scoreBand(), s.calculatedAt(),
                        s.confidence() != null ? s.confidence() : 0.0));
    }
}
