package com.sanad.platform.crm.intelligence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.AiResult;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Capability;
import com.sanad.platform.crm.integration.orchestration.AiGatewayPort.Status;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.intelligence.config.CustomerIntelligenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates AI Gateway requests for customer intelligence capabilities.
 *
 * <p>Handles timeout, retry, confidence thresholds, fallback behavior, and audit logging.
 * All AI requests go through the governed {@link AiGatewayPort} from CRM-009.
 */
@Component
public class AiScoreOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiScoreOrchestrator.class);

    private final AiGatewayPort aiGateway;
    private final ObjectMapper mapper;
    private final AuditPort audit;
    private final CustomerIntelligenceProperties properties;

    public AiScoreOrchestrator(AiGatewayPort aiGateway, ObjectMapper mapper,
                               AuditPort audit, CustomerIntelligenceProperties properties) {
        this.aiGateway = aiGateway;
        this.mapper = mapper;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * Request an AI score calculation from the gateway.
     *
     * @return the AI result, or null if unavailable (fail-closed)
     */
    public AiResult requestScore(UUID tenantId, UUID accountId, UUID actorId,
                                  String contractName, Capability capability,
                                  JsonNode indicators) {
        Instant now = Instant.now();
        String correlationId = "intel-" + UUID.randomUUID();
        String idempotencyKey = "ai-score-" + tenantId + "-" + accountId + "-" + capability.name() + "-" + now.truncatedTo(ChronoUnit.SECONDS);

        IntegrationEnvelope envelope = new IntegrationEnvelope(
                contractName, "1.0", tenantId, actorId, correlationId,
                correlationId, idempotencyKey,
                "ACCOUNT", accountId, 0,
                now, now.plus(30, ChronoUnit.SECONDS),
                Locale.ENGLISH, "CRM.CUSTOMER_INTELLIGENCE.READ", "INTERNAL");

        try {
            AiResult result = aiGateway.request(envelope, capability, indicators);

            // Audit the AI request
            ObjectNode auditAfter = mapper.createObjectNode();
            auditAfter.put("capability", capability.name());
            auditAfter.put("status", result.status().name());
            if (result.confidence() != null) auditAfter.put("confidence", result.confidence());
            audit.record(tenantId, actorId, "AI_REQUEST", "INTEGRATION_REQUEST",
                    accountId, new AuditChange(null, auditAfter), now);

            // Confidence threshold check
            if (result.status() == Status.AVAILABLE || result.status() == Status.PARTIAL) {
                double minConfidence = properties.getScoring().getMinConfidence();
                if (result.confidence() != null && result.confidence() < minConfidence) {
                    log.warn("AI result below confidence threshold: {} < {} for account {}",
                            result.confidence(), minConfidence, accountId);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("AI Gateway request failed for account {}: {}", accountId, e.getMessage());
            // Audit the failure
            ObjectNode auditError = mapper.createObjectNode();
            auditError.put("capability", capability.name());
            auditError.put("error", e.getMessage());
            audit.record(tenantId, actorId, "AI_REQUEST_FAILED", "INTEGRATION_REQUEST",
                    accountId, new AuditChange(null, auditError), Instant.now());
            // Fail-closed: return null (caller should use fallback)
            return null;
        }
    }

    /**
     * Build a minimal indicators payload for health scoring.
     */
    public JsonNode buildHealthIndicators(int daysSinceLastActivity, int openOpportunities,
                                           double totalPipeline, int meetingFreq30d,
                                           double responseTimeAvgHours, String lifecycleStatus) {
        ObjectNode node = mapper.createObjectNode();
        node.put("daysSinceLastActivity", daysSinceLastActivity);
        node.put("openOpportunities", openOpportunities);
        node.put("totalPipelineAmount", totalPipeline);
        node.put("meetingFrequency30d", meetingFreq30d);
        node.put("responseTimeAvgHours", responseTimeAvgHours);
        node.put("lifecycleStatus", lifecycleStatus);
        return node;
    }

    /**
     * Build indicators for CLV forecasting.
     */
    public JsonNode buildClvIndicators(double totalRevenue, int transactionCount,
                                        double avgDealSize, int customerSinceMonths, double growthRate) {
        ObjectNode node = mapper.createObjectNode();
        node.put("totalRevenue", totalRevenue);
        node.put("transactionCount", transactionCount);
        node.put("avgDealSize", avgDealSize);
        node.put("customerSinceMonths", customerSinceMonths);
        node.put("growthRate", growthRate);
        return node;
    }

    /**
     * Build indicators for churn prediction.
     */
    public JsonNode buildChurnIndicators(int daysSinceLastActivity, double engagementDeclinePct,
                                          int openIssuesUnresolved, int contractRenewalDays) {
        ObjectNode node = mapper.createObjectNode();
        node.put("daysSinceLastActivity", daysSinceLastActivity);
        node.put("engagementDeclinePct", engagementDeclinePct);
        node.put("openIssuesUnresolved", openIssuesUnresolved);
        node.put("contractRenewalDays", contractRenewalDays);
        return node;
    }
}
