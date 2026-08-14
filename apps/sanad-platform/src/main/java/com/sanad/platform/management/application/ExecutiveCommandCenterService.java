package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Executive Command Center service — aggregates all management domains
 * into a single executive view.
 *
 * <p>Also computes the Executive Health Score deterministically from
 * real data and persists immutable snapshots.
 */
@Service
public class ExecutiveCommandCenterService {

    private final StrategicObjectiveRepository objectiveRepo;
    private final KeyResultRepository keyResultRepo;
    private final KpiDefinitionRepository kpiDefRepo;
    private final KpiMeasurementRepository kpiMeasurementRepo;
    private final StrategicInitiativeRepository initiativeRepo;
    private final ExecutiveDecisionRepository decisionRepo;
    private final RiskRepository riskRepo;
    private final IssueRepository issueRepo;
    private final EscalationRepository escalationRepo;
    private final ExecutiveAlertRepository alertRepo;
    private final ExecutiveHealthSnapshotRepository snapshotRepo;
    private final JdbcTemplate jdbc;

    public ExecutiveCommandCenterService(
            StrategicObjectiveRepository objectiveRepo,
            KeyResultRepository keyResultRepo,
            KpiDefinitionRepository kpiDefRepo,
            KpiMeasurementRepository kpiMeasurementRepo,
            StrategicInitiativeRepository initiativeRepo,
            ExecutiveDecisionRepository decisionRepo,
            RiskRepository riskRepo,
            IssueRepository issueRepo,
            EscalationRepository escalationRepo,
            ExecutiveAlertRepository alertRepo,
            ExecutiveHealthSnapshotRepository snapshotRepo,
            JdbcTemplate jdbc) {
        this.objectiveRepo = objectiveRepo;
        this.keyResultRepo = keyResultRepo;
        this.kpiDefRepo = kpiDefRepo;
        this.kpiMeasurementRepo = kpiMeasurementRepo;
        this.initiativeRepo = initiativeRepo;
        this.decisionRepo = decisionRepo;
        this.riskRepo = riskRepo;
        this.issueRepo = issueRepo;
        this.escalationRepo = escalationRepo;
        this.alertRepo = alertRepo;
        this.snapshotRepo = snapshotRepo;
        this.jdbc = jdbc;
    }

    /**
     * Generate the Executive Command Center dashboard — a comprehensive
     * aggregated view of the organization's executive health.
     */
    @Transactional(readOnly = true)
    public CommandCenterDashboard getDashboard(UUID tenantId) {
        var objectives = objectiveRepo.findActiveObjectivesForPeriod(tenantId, LocalDate.now());
        var kpiDefs = kpiDefRepo.findByTenantAndStatus(tenantId, KpiDefinition.Status.ACTIVE, 50);
        var kpiDefIds = kpiDefs.stream().map(KpiDefinition::id).toList();
        var latestMeasurements = kpiMeasurementRepo.findLatestForDefinitions(kpiDefIds);
        var decisions = decisionRepo.findByTenant(tenantId, 50);
        var risks = riskRepo.findByTenant(tenantId, 50);
        var issues = issueRepo.findByTenant(tenantId, 50);
        var escalations = escalationRepo.findByTenant(tenantId, 50);
        var alerts = alertRepo.findByTenantAndStatus(tenantId, ExecutiveAlert.Status.OPEN, 50);

        // Count by status
        int activeObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACTIVE).count();
        int atRiskObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.AT_RISK).count();
        int offTrackObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.OFF_TRACK).count();
        int achievedObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACHIEVED).count();

        int onTrackKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.ON_TRACK).count();
        int atRiskKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.AT_RISK).count();
        int offTrackKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.OFF_TRACK).count();
        int noDataKpis = kpiDefs.size() - latestMeasurements.size();

        int pendingDecisions = (int) decisions.stream()
                .filter(d -> d.status() == ExecutiveDecision.Status.SUBMITTED
                        || d.status() == ExecutiveDecision.Status.UNDER_REVIEW).count();
        int overdueDecisions = (int) decisions.stream()
                .filter(d -> d.dueDate() != null && d.dueDate().isBefore(LocalDate.now())
                        && d.status() != ExecutiveDecision.Status.COMPLETED
                        && d.status() != ExecutiveDecision.Status.CANCELLED).count();

        int criticalRisks = (int) risks.stream().filter(r -> r.severity() == Risk.Severity.CRITICAL).count();
        int highRisks = (int) risks.stream().filter(r -> r.severity() == Risk.Severity.HIGH).count();

        int openIssues = (int) issues.stream()
                .filter(i -> i.status() == Issue.Status.OPEN
                        || i.status() == Issue.Status.IN_PROGRESS
                        || i.status() == Issue.Status.TRIAGED).count();
        int criticalIssues = (int) issues.stream()
                .filter(i -> i.severity() == Issue.Severity.CRITICAL && i.status() != Issue.Status.CLOSED).count();

        int activeEscalations = (int) escalations.stream()
                .filter(e -> e.status() == Escalation.Status.ACTIVE).count();
        int overdueEscalations = (int) escalations.stream()
                .filter(e -> e.slaDeadline() != null && e.slaDeadline().isBefore(Instant.now())
                        && e.status() == Escalation.Status.ACTIVE).count();

        // Compute health scores
        int strategyScore = ExecutiveHealthSnapshot.computeStrategyScore(
                objectives.size(), activeObj, atRiskObj, offTrackObj, achievedObj);
        int kpiScore = ExecutiveHealthSnapshot.computeKpiScore(
                kpiDefs.size(), onTrackKpis, atRiskKpis, offTrackKpis);
        int decisionScore = ExecutiveHealthSnapshot.computeDecisionScore(pendingDecisions, overdueDecisions);
        int riskScore = ExecutiveHealthSnapshot.computeRiskScore(criticalRisks, highRisks);
        int issueScore = ExecutiveHealthSnapshot.computeIssueScore(openIssues, criticalIssues);
        int escalationScore = ExecutiveHealthSnapshot.computeEscalationScore(activeEscalations, overdueEscalations);
        int healthScore = ExecutiveHealthSnapshot.computeHealthScore(
                strategyScore, kpiScore, decisionScore, riskScore, issueScore, escalationScore);

        return new CommandCenterDashboard(
                healthScore, strategyScore, kpiScore, decisionScore, riskScore, issueScore, escalationScore,
                objectives.size(), activeObj, atRiskObj, offTrackObj, achievedObj,
                kpiDefs.size(), onTrackKpis, atRiskKpis, offTrackKpis, noDataKpis,
                pendingDecisions, overdueDecisions,
                criticalRisks, highRisks, risks.size(),
                openIssues, criticalIssues, issues.size(),
                activeEscalations, overdueEscalations, escalations.size(),
                alerts.size(),
                Instant.now()
        );
    }

    /**
     * Snapshot the current executive health and persist it as an immutable record.
     */
    @Transactional
    public ExecutiveHealthSnapshot snapshotHealth(UUID tenantId) {
        var dashboard = getDashboard(tenantId);
        var snapshot = new ExecutiveHealthSnapshot(
                UUID.randomUUID(), tenantId,
                dashboard.healthScore(), dashboard.strategyScore(), dashboard.kpiScore(),
                dashboard.decisionScore(), dashboard.riskScore(), dashboard.issueScore(),
                dashboard.escalationScore(),
                dashboard.totalObjectives(), dashboard.activeObjectives(),
                dashboard.atRiskObjectives(), dashboard.offTrackObjectives(),
                dashboard.totalKpis(), dashboard.onTrackKpis(), dashboard.atRiskKpis(),
                dashboard.offTrackKpis(),
                dashboard.pendingDecisions(), dashboard.overdueDecisions(),
                dashboard.criticalRisks(), dashboard.highRisks(),
                dashboard.openIssues(), dashboard.criticalIssues(),
                dashboard.activeEscalations(), dashboard.overdueEscalations(),
                dashboard.activeAlerts(),
                Instant.now()
        );
        return snapshotRepo.save(snapshot);
    }

    /**
     * Command Center Dashboard DTO — the complete executive view.
     */
    public record CommandCenterDashboard(
            int healthScore, int strategyScore, int kpiScore, int decisionScore,
            int riskScore, int issueScore, int escalationScore,
            int totalObjectives, int activeObjectives, int atRiskObjectives,
            int offTrackObjectives, int achievedObjectives,
            int totalKpis, int onTrackKpis, int atRiskKpis, int offTrackKpis, int noDataKpis,
            int pendingDecisions, int overdueDecisions,
            int criticalRisks, int highRisks, int totalRisks,
            int openIssues, int criticalIssues, int totalIssues,
            int activeEscalations, int overdueEscalations, int totalEscalations,
            int activeAlerts,
            Instant generatedAt
    ) {}
}
