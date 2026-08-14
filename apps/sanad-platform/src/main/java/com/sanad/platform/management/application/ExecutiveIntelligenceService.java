package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Executive Intelligence service — generates advisory insights using
 * deterministic rule-based analysis (fallback when no external AI provider is configured).
 *
 * <p>AI output is ALWAYS advisory. It NEVER mutates business state.
 * Every insight references its evidence (source data) and carries a confidence score.
 *
 * <p>When no external AI provider is configured (model_name = 'deterministic'),
 * the system uses rule-based heuristics to generate insights.
 */
@Service
public class ExecutiveIntelligenceService {

    private final ExecutiveInsightRepository insightRepo;
    private final StrategicObjectiveRepository objectiveRepo;
    private final KpiDefinitionRepository kpiDefRepo;
    private final KpiMeasurementRepository kpiMeasurementRepo;
    private final RiskRepository riskRepo;
    private final IssueRepository issueRepo;
    private final ExecutiveDecisionRepository decisionRepo;
    private final EscalationRepository escalationRepo;

    // System UUID for deterministic (non-AI) generated insights
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    public ExecutiveIntelligenceService(
            ExecutiveInsightRepository insightRepo,
            StrategicObjectiveRepository objectiveRepo,
            KpiDefinitionRepository kpiDefRepo,
            KpiMeasurementRepository kpiMeasurementRepo,
            RiskRepository riskRepo,
            IssueRepository issueRepo,
            ExecutiveDecisionRepository decisionRepo,
            EscalationRepository escalationRepo) {
        this.insightRepo = insightRepo;
        this.objectiveRepo = objectiveRepo;
        this.kpiDefRepo = kpiDefRepo;
        this.kpiMeasurementRepo = kpiMeasurementRepo;
        this.riskRepo = riskRepo;
        this.issueRepo = issueRepo;
        this.decisionRepo = decisionRepo;
        this.escalationRepo = escalationRepo;
    }

    /**
     * Generate an executive summary — a high-level overview of
     * strategic health, KPI status, risks, and decisions.
     */
    @Transactional
    public ExecutiveInsight generateExecutiveSummary(UUID tenantId, UUID requestedBy) {
        var objectives = objectiveRepo.findActiveObjectivesForPeriod(tenantId, LocalDate.now());
        var kpiDefs = kpiDefRepo.findByTenantAndStatus(tenantId, KpiDefinition.Status.ACTIVE, 50);
        var kpiDefIds = kpiDefs.stream().map(KpiDefinition::id).toList();
        var measurements = kpiMeasurementRepo.findLatestForDefinitions(kpiDefIds);
        var risks = riskRepo.findByTenant(tenantId, 50);
        var issues = issueRepo.findByTenant(tenantId, 50);
        var decisions = decisionRepo.findByTenant(tenantId, 50);

        var offTrackKpis = measurements.stream()
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
                kpiDefs.size(), offTrackKpis));
        summary.append(String.format("- %d risks (%d critical)\n", risks.size(), criticalRisks));
        summary.append(String.format("- %d issues, %d pending decisions\n",
                issues.size(), pendingDecisions));

        var evidence = String.format(
                "{\"objectives\":%d,\"kpis\":%d,\"risks\":%d,\"issues\":%d,\"decisions\":%d}",
                objectives.size(), kpiDefs.size(), risks.size(), issues.size(), decisions.size()
        );

        // Confidence is 1.0 for deterministic summaries (they're factual aggregations)
        var insight = ExecutiveInsight.create(
                tenantId,
                ExecutiveInsight.InsightType.SUMMARY,
                "Executive Summary",
                summary.toString(),
                BigDecimal.ONE,
                evidence,
                "deterministic", "1.0",
                requestedBy != null ? requestedBy : SYSTEM_USER_ID
        );
        return insightRepo.save(insight);
    }

    /**
     * Detect KPI anomalies — KPIs with significant negative variance
     * or sudden status changes.
     */
    @Transactional
    public List<ExecutiveInsight> detectKpiAnomalies(UUID tenantId, UUID requestedBy) {
        var kpiDefs = kpiDefRepo.findByTenantAndStatus(tenantId, KpiDefinition.Status.ACTIVE, 50);
        var kpiDefIds = kpiDefs.stream().map(KpiDefinition::id).toList();
        var measurements = kpiMeasurementRepo.findLatestForDefinitions(kpiDefIds);

        var anomalies = new ArrayList<ExecutiveInsight>();
        for (var m : measurements) {
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
                    var insight = ExecutiveInsight.create(
                            tenantId,
                            ExecutiveInsight.InsightType.ANOMALY,
                            title, desc, confidence, evidence,
                            "deterministic", "1.0",
                            requestedBy != null ? requestedBy : SYSTEM_USER_ID
                    );
                    anomalies.add(insightRepo.save(insight));
                }
            }
        }
        return anomalies;
    }

    /**
     * Generate a recommended executive action based on the current state.
     */
    @Transactional
    public ExecutiveInsight recommendExecutiveAction(UUID tenantId, UUID requestedBy) {
        var risks = riskRepo.findByTenant(tenantId, 50);
        var issues = issueRepo.findByTenant(tenantId, 50);
        var escalations = escalationRepo.findByTenantAndStatus(
                tenantId, Escalation.Status.ACTIVE, 50);

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

        var insight = ExecutiveInsight.create(
                tenantId,
                ExecutiveInsight.InsightType.RECOMMENDATION,
                "Recommended Executive Action",
                recommendation.toString(),
                new BigDecimal("0.85"),
                evidence.toString(),
                "deterministic", "1.0",
                requestedBy != null ? requestedBy : SYSTEM_USER_ID
        );
        return insightRepo.save(insight);
    }

    @Transactional(readOnly = true)
    public List<ExecutiveInsight> findActiveInsights(UUID tenantId, int limit) {
        return insightRepo.findByTenantAndStatus(tenantId, ExecutiveInsight.InsightStatus.ACTIVE, limit);
    }

    @Transactional(readOnly = true)
    public Optional<ExecutiveInsight> findById(UUID tenantId, UUID id) {
        return insightRepo.findById(tenantId, id);
    }

    @Transactional
    public ExecutiveInsight dismissInsight(UUID tenantId, UUID id) {
        var insight = insightRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found: " + id));
        return insightRepo.save(insight.dismiss());
    }
}
