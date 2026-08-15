package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executive Command Center service — aggregates all management domains
 * into a single executive view.
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
    private final FinanceManagementIntegrationService financeIntegrationService;
    private final ModuleGovernanceService moduleGovernanceService;
    private final GovernedSystemsOverviewService governedSystemsOverviewService;
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
            FinanceManagementIntegrationService financeIntegrationService,
            ModuleGovernanceService moduleGovernanceService,
            GovernedSystemsOverviewService governedSystemsOverviewService,
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
        this.financeIntegrationService = financeIntegrationService;
        this.moduleGovernanceService = moduleGovernanceService;
        this.governedSystemsOverviewService = governedSystemsOverviewService;
        this.jdbc = jdbc;
    }

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

        int activeObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACTIVE).count();
        int atRiskObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.AT_RISK).count();
        int offTrackObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.OFF_TRACK).count();
        int achievedObj = (int) objectives.stream().filter(o -> o.status() == StrategicObjective.Status.ACHIEVED).count();

        int onTrackKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.ON_TRACK).count();
        int atRiskKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.AT_RISK).count();
        int offTrackKpis = (int) latestMeasurements.stream().filter(m -> m.status() == KpiMeasurement.Status.OFF_TRACK).count();
        int noDataKpis = kpiDefs.size() - latestMeasurements.size();

        int pendingDecisions = (int) decisions.stream()
                .filter(d -> d.status() == ExecutiveDecision.Status.SUBMITTED || d.status() == ExecutiveDecision.Status.UNDER_REVIEW).count();
        int overdueDecisions = (int) decisions.stream()
                .filter(d -> d.dueDate() != null && d.dueDate().isBefore(LocalDate.now())
                        && d.status() != ExecutiveDecision.Status.COMPLETED
                        && d.status() != ExecutiveDecision.Status.CANCELLED).count();

        int criticalRisks = (int) risks.stream().filter(r -> r.severity() == Risk.Severity.CRITICAL).count();
        int highRisks = (int) risks.stream().filter(r -> r.severity() == Risk.Severity.HIGH).count();

        int openIssues = (int) issues.stream()
                .filter(i -> i.status() == Issue.Status.OPEN || i.status() == Issue.Status.IN_PROGRESS || i.status() == Issue.Status.TRIAGED).count();
        int criticalIssues = (int) issues.stream()
                .filter(i -> i.severity() == Issue.Severity.CRITICAL && i.status() != Issue.Status.CLOSED).count();

        int activeEscalations = (int) escalations.stream().filter(e -> e.status() == Escalation.Status.ACTIVE).count();
        int overdueEscalations = (int) escalations.stream()
                .filter(e -> e.slaDeadline() != null && e.slaDeadline().isBefore(Instant.now()) && e.status() == Escalation.Status.ACTIVE).count();

        int strategyScore = ExecutiveHealthSnapshot.computeStrategyScore(objectives.size(), activeObj, atRiskObj, offTrackObj, achievedObj);
        int kpiScore = ExecutiveHealthSnapshot.computeKpiScore(kpiDefs.size(), onTrackKpis, atRiskKpis, offTrackKpis);
        int decisionScore = ExecutiveHealthSnapshot.computeDecisionScore(pendingDecisions, overdueDecisions);
        int riskScore = ExecutiveHealthSnapshot.computeRiskScore(criticalRisks, highRisks);
        int issueScore = ExecutiveHealthSnapshot.computeIssueScore(openIssues, criticalIssues);
        int escalationScore = ExecutiveHealthSnapshot.computeEscalationScore(activeEscalations, overdueEscalations);
        int healthScore = ExecutiveHealthSnapshot.computeHealthScore(strategyScore, kpiScore, decisionScore, riskScore, issueScore, escalationScore);

        Map<String, Object> financeOverview = safeOverview(() -> financeIntegrationService.getOverview(tenantId));
        List<Map<String, Object>> moduleGovernance = safeList(() -> moduleGovernanceService.getModuleStatuses());
        // Governed-systems overviews (CRM/Analytics/Workflow) are loaded via a
        // SEPARATE @Transactional(REQUIRES_NEW) bean so failures do not pollute
        // the outer dashboard transaction (PSQLException would otherwise abort
        // the entire @Transactional(readOnly=true) scope).
        var governedSystems = governedSystemsOverviewService.loadAll(tenantId);
        // GAP 19 (Revenue) + GAP 18 (Operations) are exposed via dedicated
        // endpoints (/api/v1/management/oversight/{revenue,operations}/overview)
        // rather than inlined in the dashboard to avoid transaction-abort
        // cascades from the CRM estimated_value column not existing in test fixtures.
        // The ExecutiveReportService.generateReport aggregates them all.
        Map<String, Object> revenueOverview = Map.of("_note", "use /api/v1/management/oversight/revenue/overview");
        Map<String, Object> operationalOverview = Map.of("_note", "use /api/v1/management/oversight/operations/overview");

        return new CommandCenterDashboard(
                healthScore, strategyScore, kpiScore, decisionScore, riskScore, issueScore, escalationScore,
                objectives.size(), activeObj, atRiskObj, offTrackObj, achievedObj,
                kpiDefs.size(), onTrackKpis, atRiskKpis, offTrackKpis, noDataKpis,
                pendingDecisions, overdueDecisions,
                criticalRisks, highRisks, risks.size(),
                openIssues, criticalIssues, issues.size(),
                activeEscalations, overdueEscalations, escalations.size(),
                alerts.size(),
                financeOverview, moduleGovernance,
                governedSystems.crm(), governedSystems.analytics(), governedSystems.workflow(),
                revenueOverview, operationalOverview,
                Instant.now()
        );
    }

    /** Defensive wrapper — if any integration service throws, return an empty map
     *  so the command center stays resilient (matches the WorkflowSystemHealthService pattern). */
    private Map<String, Object> safeOverview(java.util.function.Supplier<Map<String, Object>> supplier) {
        try {
            Map<String, Object> result = supplier.get();
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE");
        }
    }

    /** Same defensive wrapper for List-returning integrations. */
    private List<Map<String, Object>> safeList(java.util.function.Supplier<List<Map<String, Object>>> supplier) {
        try {
            List<Map<String, Object>> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            return List.of(Map.of("_error", e.getClass().getSimpleName(),
                    "_status", "UNAVAILABLE"));
        }
    }

    @Transactional
    public ExecutiveHealthSnapshot snapshotHealth(UUID tenantId) {
        var dashboard = getDashboard(tenantId);
        var snapshot = new ExecutiveHealthSnapshot(
                UUID.randomUUID(), tenantId,
                dashboard.healthScore(), dashboard.strategyScore(), dashboard.kpiScore(),
                dashboard.decisionScore(), dashboard.riskScore(), dashboard.issueScore(), dashboard.escalationScore(),
                dashboard.totalObjectives(), dashboard.activeObjectives(), dashboard.atRiskObjectives(), dashboard.offTrackObjectives(),
                dashboard.totalKpis(), dashboard.onTrackKpis(), dashboard.atRiskKpis(), dashboard.offTrackKpis(),
                dashboard.pendingDecisions(), dashboard.overdueDecisions(), dashboard.criticalRisks(), dashboard.highRisks(),
                dashboard.openIssues(), dashboard.criticalIssues(), dashboard.activeEscalations(), dashboard.overdueEscalations(),
                dashboard.activeAlerts(), Instant.now());
        return snapshotRepo.save(snapshot);
    }

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
            Map<String, Object> financeOverview,
            List<Map<String, Object>> moduleGovernance,
            Map<String, Object> crmOverview,
            Map<String, Object> analyticsOverview,
            Map<String, Object> workflowHealth,
            Map<String, Object> revenueOverview,
            Map<String, Object> operationalOverview,
            Instant generatedAt) {}
}
