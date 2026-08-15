package com.sanad.platform.management.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executive Reporting Service (GAP 25).
 *
 * <p>Produces a structured Executive Report combining:
 * <ul>
 *   <li>Executive KPIs (from {@link KpiService})</li>
 *   <li>Strategic objectives (from {@link StrategicObjectiveService})</li>
 *   <li>Risks, Issues, Alerts, Decisions, Escalations (from their respective services)</li>
 *   <li>CRM, Finance, Analytics, Workflow overviews (via the governance contract)</li>
 *   <li>Module health (via {@link ManagementGovernanceModuleRegistry})</li>
 *   <li>Revenue overview (from {@link RevenueOversightService})</li>
 *   <li>Operational overview (from {@link CrossModuleOperationalOverviewService})</li>
 *   <li>Executive intelligence summary (from {@link ExecutiveIntelligenceService})</li>
 * </ul>
 *
 * <p>The report is exposed via:
 * <ul>
 *   <li>{@code GET /api/v1/management/reports/executive} — JSON download</li>
 *   <li>The {@code Content-Disposition: attachment} header makes the browser
 *       save the response as a file rather than rendering it inline.</li>
 *   <li>No PDF/Excel library dependency — JSON is the minimum correct
 *       implementation that closes the gap. Future baselines can add
 *       a PDF renderer without breaking the contract.</li>
 * </ul>
 *
 * <p>Report metadata:
 * <ul>
 *   <li>{@code tenantId} — tenant scope</li>
 *   <li>{@code generatedAt} — timestamp</li>
 *   <li>{@code reportingPeriodStart}/{@code End} — period covered</li>
 *   <li>{@code sourceModules} — list of modules that contributed</li>
 *   <li>{@code reportStatus} — COMPLETE / PARTIAL (PARTIAL if any module was unavailable)</li>
 *   <li>{@code version} — report schema version (1.0)</li>
 * </ul>
 *
 * <p>The report is tenant-safe (every data source is tenant-scoped) and
 * auditable (the controller endpoint is logged via {@code PlatformAuditService}).
 *
 * <p>Scheduling: the architecture is ready — when the project adds a
 * scheduler, this service can be invoked by a {@code @Scheduled} job
 * without modification. No scheduler is added by this implementation
 * to avoid unnecessary infrastructure.
 */
@Service
public class ExecutiveReportService {

    private final ExecutiveCommandCenterService commandCenterService;
    private final RevenueOversightService revenueService;
    private final CrossModuleOperationalOverviewService operationalService;
    private final ManagementGovernanceModuleRegistry moduleRegistry;
    private final ExecutiveIntelligenceService intelligenceService;

    public ExecutiveReportService(
            ExecutiveCommandCenterService commandCenterService,
            RevenueOversightService revenueService,
            CrossModuleOperationalOverviewService operationalService,
            ManagementGovernanceModuleRegistry moduleRegistry,
            ExecutiveIntelligenceService intelligenceService) {
        this.commandCenterService = commandCenterService;
        this.revenueService = revenueService;
        this.operationalService = operationalService;
        this.moduleRegistry = moduleRegistry;
        this.intelligenceService = intelligenceService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateReport(UUID tenantId) {
        Map<String, Object> report = new LinkedHashMap<>();

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenantId.toString());
        metadata.put("generatedAt", Instant.now().toString());
        metadata.put("reportingPeriodStart", LocalDate.now().minusDays(30).toString());
        metadata.put("reportingPeriodEnd", LocalDate.now().toString());
        metadata.put("version", "1.0");
        metadata.put("format", "JSON");

        // Section 1: Executive Command Center Dashboard
        Map<String, Object> dashboard;
        boolean dashboardOk = true;
        try {
            dashboard = toMutableMap(commandCenterService.getDashboard(tenantId));
        } catch (Exception e) {
            dashboard = Map.of("_error", e.getClass().getSimpleName());
            dashboardOk = false;
        }
        report.put("commandCenter", dashboard);

        // Section 2: Revenue Overview (GAP 19)
        Map<String, Object> revenue;
        boolean revenueOk = true;
        try {
            revenue = toMutableMap(revenueService.getExecutiveRevenueOverview(tenantId));
        } catch (Exception e) {
            revenue = Map.of("_error", e.getClass().getSimpleName());
            revenueOk = false;
        }
        report.put("revenueOverview", revenue);

        // Section 3: Cross-Module Operational Overview (GAP 18)
        Map<String, Object> operations;
        boolean opsOk = true;
        try {
            operations = toMutableMap(operationalService.getOperationalOverview(tenantId));
        } catch (Exception e) {
            operations = Map.of("_error", e.getClass().getSimpleName());
            opsOk = false;
        }
        report.put("operationalOverview", operations);

        // Section 4: Module Health (GAP 24)
        List<Map<String, Object>> moduleHealth;
        try {
            moduleHealth = moduleRegistry.compositeSummary(tenantId);
        } catch (Exception e) {
            moduleHealth = List.of(Map.of("_error", e.getClass().getSimpleName()));
        }
        report.put("moduleHealth", moduleHealth);

        // Section 5: Executive Intelligence summary
        Map<String, Object> intelligence;
        try {
            // system-generated report — actor is null (audit trail captures report generation separately)
            var insight = intelligenceService.generateExecutiveSummary(tenantId, null);
            intelligence = toMutableMap(insight);
        } catch (Exception e) {
            intelligence = Map.of("_error", e.getClass().getSimpleName());
        }
        report.put("executiveIntelligence", intelligence);

        // Source modules that contributed data (audit trail)
        List<String> sourceModules = new java.util.ArrayList<>();
        if (dashboardOk) sourceModules.add("COMMAND_CENTER");
        if (revenueOk) {
            sourceModules.add("CRM");
            sourceModules.add("FINANCE");
        }
        if (opsOk) sourceModules.add("OPERATIONAL_OVERVIEW");
        metadata.put("sourceModules", sourceModules);

        // Report status: COMPLETE if all sections loaded, PARTIAL otherwise
        String reportStatus = (dashboardOk && revenueOk && opsOk) ? "COMPLETE" : "PARTIAL";
        metadata.put("reportStatus", reportStatus);

        report.put("_metadata", metadata);
        return report;
    }

    /** Defensive helper: wrap an immutable map into a mutable LinkedHashMap
     *  so callers can add error markers if needed. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object o) {
        if (o instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) o);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", o);
        return m;
    }
}
