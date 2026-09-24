package com.sanad.platform.management.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * AI Executive Intelligence Provider — abstraction for AI-powered
 * executive analysis.
 *
 * <p>Implementations may use:
 * <ul>
 *   <li>Deterministic rules (default, always available)</li>
 *   <li>External AI providers (when configured)</li>
 * </ul>
 *
 * <p>ALL providers MUST produce advisory-only output. No provider
 * implementation may mutate business state.
 */
public interface ExecutiveIntelligenceProvider {

    /** Provider name (e.g., 'deterministic', 'openai', 'anthropic'). */
    String providerName();

    /**
     * Generate an executive summary of the current organizational state.
     *
     * @return an advisory insight with evidence and confidence
     */
    ExecutiveInsight generateSummary(
            UUID tenantId, UUID requestedBy,
            List<StrategicObjective> objectives,
            List<KpiMeasurement> latestMeasurements,
            List<Risk> risks, List<Issue> issues,
            List<ExecutiveDecision> decisions);

    /**
     * Detect KPI anomalies from measurements.
     *
     * @return list of anomaly insights (advisory only)
     */
    List<ExecutiveInsight> detectAnomalies(
            UUID tenantId, UUID requestedBy,
            List<KpiDefinition> kpiDefs,
            List<KpiMeasurement> latestMeasurements);

    /**
     * Recommend an executive action based on current state.
     *
     * @return an advisory recommendation with evidence
     */
    ExecutiveInsight recommendAction(
            UUID tenantId, UUID requestedBy,
            List<Risk> risks, List<Issue> issues,
            List<Escalation> escalations);
}
