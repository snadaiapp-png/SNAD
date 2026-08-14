package com.sanad.platform.management.infrastructure;

import com.sanad.platform.management.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic (rule-based) Executive Intelligence Provider.
 *
 * <p>This is the default provider, always available without external configuration.
 * Uses deterministic rules to generate advisory insights.
 *
 * <p>AI safety: ALL output is advisory-only. This provider NEVER mutates
 * business state — it only creates {@link ExecutiveInsight} records with
 * advisory=true.
 */
@Component
public class DeterministicExecutiveIntelligenceProvider implements ExecutiveIntelligenceProvider {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Override
    public String providerName() {
        return "deterministic";
    }

    @Override
    public ExecutiveInsight generateSummary(
            UUID tenantId, UUID requestedBy,
            List<StrategicObjective> objectives,
            List<KpiMeasurement> latestMeasurements,
            List<Risk> risks, List<Issue> issues,
            List<ExecutiveDecision> decisions) {

        var offTrackKpis = latestMeasurements.stream()
                .filter(m -> m.status() == KpiMeasurement.Status.OFF_TRACK).count();
        var atRiskObjectives = objectives.stream()
                .filter(o -> o.status() == StrategicObjective.Status.AT_RISK
                        || o.status() == StrategicObjective.Status.OFF_TRACK).count();
        var criticalRisks = risks.stream()
                .filter(r -> r.severity() == Risk.Severity.CRITICAL).count();
        var pendingDecisions = decisions.stream()
                .filter(d -> d.status() == ExecutiveDecision.Status.SUBMITTED
                        || d.status() == ExecutiveDecision.Status.UNDER_REVIEW).count();

        var summary = new StringBuilder();
        summary.append("Executive Summary:\n");
        summary.append(String.format("- %d active objectives (%d at risk/off-track)\n",
                objectives.size(), atRiskObjectives));
        summary.append(String.format("- %d KPIs tracked (%d off-track)\n",
                latestMeasurements.size(), offTrackKpis));
        summary.append(String.format("- %d risks (%d critical)\n", risks.size(), criticalRisks));
        summary.append(String.format("- %d issues, %d pending decisions\n",
                issues.size(), pendingDecisions));

        var evidence = String.format(
                "{\"objectives\":%d,\"kpis\":%d,\"risks\":%d,\"issues\":%d,\"decisions\":%d}",
                objectives.size(), latestMeasurements.size(), risks.size(), issues.size(), decisions.size()
        );

        return ExecutiveInsight.create(
                tenantId, ExecutiveInsight.InsightType.SUMMARY,
                "Executive Summary", summary.toString(),
                BigDecimal.ONE, evidence,
                providerName(), "1.0",
                requestedBy != null ? requestedBy : SYSTEM_USER_ID
        );
    }

    @Override
    public List<ExecutiveInsight> detectAnomalies(
            UUID tenantId, UUID requestedBy,
            List<KpiDefinition> kpiDefs,
            List<KpiMeasurement> latestMeasurements) {

        var anomalies = new ArrayList<ExecutiveInsight>();
        for (var m : latestMeasurements) {
            if (m.status() == KpiMeasurement.Status.OFF_TRACK
                    || m.status() == KpiMeasurement.Status.AT_RISK) {
                var def = kpiDefs.stream()
                        .filter(d -> d.id().equals(m.kpiDefinitionId()))
                        .findFirst().orElse(null);
                if (def != null) {
                    var title = "KPI Anomaly: " + def.name();
                    var desc = String.format(
                            "KPI '%s' is %s. Measured value: %s, variance: %s%%",
                            def.name(), m.status().name(),
                            m.measuredValue(), m.variancePct()
                    );
                    var evidence = String.format(
                            "{\"kpi_definition_id\":\"%s\",\"measurement_id\":\"%s\",\"status\":\"%s\"}",
                            m.kpiDefinitionId(), m.id(), m.status().name()
                    );
                    var confidence = m.status() == KpiMeasurement.Status.OFF_TRACK
                            ? new BigDecimal("0.95")
                            : new BigDecimal("0.80");
                    anomalies.add(ExecutiveInsight.create(
                            tenantId, ExecutiveInsight.InsightType.ANOMALY,
                            title, desc, confidence, evidence,
                            providerName(), "1.0",
                            requestedBy != null ? requestedBy : SYSTEM_USER_ID
                    ));
                }
            }
        }
        return anomalies;
    }

    @Override
    public ExecutiveInsight recommendAction(
            UUID tenantId, UUID requestedBy,
            List<Risk> risks, List<Issue> issues,
            List<Escalation> escalations) {

        var recommendation = new StringBuilder();
        var evidence = new StringBuilder("[");

        if (!risks.isEmpty()) {
            var critical = risks.stream().filter(r -> r.severity() == Risk.Severity.CRITICAL).count();
            if (critical > 0) {
                recommendation.append(String.format(
                        "Address %d critical risk(s) immediately.\n", critical));
                evidence.append("{\"type\":\"critical_risks\",\"count\":").append(critical).append("}");
            }
        }
        if (!escalations.isEmpty()) {
            if (!recommendation.isEmpty()) recommendation.append("\n");
            recommendation.append(String.format(
                    "Review %d active escalation(s).\n", escalations.size()));
            if (evidence.length() > 1) evidence.append(",");
            evidence.append("{\"type\":\"active_escalations\",\"count\":").append(escalations.size()).append("}");
        }
        if (issues.stream().anyMatch(i -> i.severity() == Issue.Severity.CRITICAL
                && i.status() != Issue.Status.CLOSED)) {
            if (!recommendation.isEmpty()) recommendation.append("\n");
            recommendation.append("Triage critical issues.\n");
            if (evidence.length() > 1) evidence.append(",");
            evidence.append("{\"type\":\"critical_issues\"}");
        }

        if (recommendation.isEmpty()) {
            recommendation.append("No critical executive actions required at this time.");
        }
        evidence.append("]");

        return ExecutiveInsight.create(
                tenantId, ExecutiveInsight.InsightType.RECOMMENDATION,
                "Recommended Executive Action", recommendation.toString(),
                new BigDecimal("0.85"), evidence.toString(),
                providerName(), "1.0",
                requestedBy != null ? requestedBy : SYSTEM_USER_ID
        );
    }
}
