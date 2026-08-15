package com.sanad.platform.management;

import com.sanad.platform.management.application.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Senior Management Final Closure Test (Phase 4 item 6).
 *
 * Verifies that the Executive Command Center now exposes ALL management
 * domains in one unified dashboard:
 *  - CRM, Finance, Analytics, Workflow, Module Registry
 *  - Revenue Overview (GAP 19)
 *  - Operational Overview (GAP 18)
 *  - KPIs/OKRs, Risks, Issues, Alerts, Decisions, Escalations, SLA
 *  - Governance Configuration (GAP 26) is wired and operational
 *  - Executive Report (GAP 25) aggregates all sections
 *
 * Closes the v20260815.8 final closure gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class SeniorManagementFinalClosureTest {

    @Autowired private ExecutiveCommandCenterService commandCenterService;
    @Autowired private ExecutiveReportService reportService;
    @Autowired private GovernanceConfigurationService governanceConfigService;
    @Autowired private RevenueOversightService revenueService;
    @Autowired private CrossModuleOperationalOverviewService operationalService;
    @Autowired private ManagementGovernanceModuleRegistry moduleRegistry;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE management_audit_trail, escalations, executive_alerts, "
                + "executive_insights, executive_health_snapshots, "
                + "decision_actions, decision_participants, executive_decisions, "
                + "risks, issues, "
                + "strategic_initiatives, kpi_measurements, kpi_targets, "
                + "kpi_definitions, key_results, strategic_objectives, "
                + "governance_configurations RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "fc-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "fc-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void commandCenter_dashboard_exposesAllManagementDomains() {
        var dashboard = commandCenterService.getDashboard(tenantId);

        // Original 6 management domains
        assertThat(dashboard.totalObjectives()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.totalKpis()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.totalRisks()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.totalIssues()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.totalEscalations()).isGreaterThanOrEqualTo(0);
        assertThat(dashboard.activeAlerts()).isGreaterThanOrEqualTo(0);

        // Governed-systems overviews (v20260815.7)
        assertThat(dashboard.financeOverview()).isNotNull();
        assertThat(dashboard.moduleGovernance()).isNotNull();
        assertThat(dashboard.crmOverview()).isNotNull();
        assertThat(dashboard.analyticsOverview()).isNotNull();
        assertThat(dashboard.workflowHealth()).isNotNull();

        // GAP 19 + GAP 18: Revenue + Operational overviews (v20260815.8)
        assertThat(dashboard.revenueOverview()).isNotNull();
        assertThat(dashboard.operationalOverview()).isNotNull();
    }

    @Test
    void commandCenter_dashboard_revenueOverviewContainsRealData() {
        // v20260815.9 — Revenue Overview is REAL data, not a placeholder.
        // It must contain the actual revenue fields (crmWonRevenue, etc.).
        var dashboard = commandCenterService.getDashboard(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> revenue = (Map<String, Object>) dashboard.revenueOverview();
        assertThat(revenue).containsKeys(
                "crmWonRevenue", "crmPipelineValue", "invoiceTotalValue",
                "collectedRevenue", "outstandingAmount", "paymentStatusSummary",
                "revenueVariance", "sourceModules", "generatedAt");
        // Must NOT be a placeholder
        assertThat(revenue.containsKey("_note")).isFalse();
        assertThat(revenue.containsKey("_status")).isFalse();
    }

    @Test
    void commandCenter_dashboard_operationalOverviewContainsRealData() {
        var dashboard = commandCenterService.getDashboard(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> ops = (Map<String, Object>) dashboard.operationalOverview();
        assertThat(ops).containsKeys(
                "overallHealthStatus", "moduleCount", "modules",
                "totalOpenAlerts", "totalOpenRisks", "totalOpenIssues",
                "slaOverallState", "generatedAt");
        // Must NOT be a placeholder
        assertThat(ops.containsKey("_note")).isFalse();
    }

    @Test
    void executiveReport_aggregatesAllFiveGapImplementations() {
        Map<String, Object> report = reportService.generateReport(tenantId);
        // Section 1: command center (with revenue + operations integrated)
        assertThat(report).containsKey("commandCenter");
        // Section 2: revenue overview (GAP 19)
        assertThat(report).containsKey("revenueOverview");
        // Section 3: operational overview (GAP 18)
        assertThat(report).containsKey("operationalOverview");
        // Section 4: module health (GAP 24)
        assertThat(report).containsKey("moduleHealth");
        // Section 5: executive intelligence (GAP 25)
        assertThat(report).containsKey("executiveIntelligence");
        // Metadata
        assertThat(report).containsKey("_metadata");
    }

    @Test
    void governanceConfiguration_isWiredAndFunctional() {
        // Verify the governance configuration service is wired and operational
        var configs = governanceConfigService.list(tenantId);
        assertThat(configs).isNotNull();  // empty list is OK

        // Verify default resolution works
        var resolved = governanceConfigService.resolveInteger(
                tenantId, "alert.dedup.window.seconds", 999);
        assertThat(resolved.value()).isEqualTo(300);
    }

    @Test
    void moduleRegistry_discoversAllFiveAdapters() {
        var modules = moduleRegistry.allModules();
        // CRM, Finance, Analytics, Workflow, Module Registry = 5 adapters
        assertThat(modules.size()).isGreaterThanOrEqualTo(5);
        var codes = modules.stream().map(ManagementGovernanceModuleContract::moduleCode).toList();
        assertThat(codes).contains("CRM", "FINANCE", "ANALYTICS", "WORKFLOW");
    }

    @Test
    void endToEndManagementOperatingModel_isComplete() {
        // Verify the end-to-end operating model:
        // 1. Command Center works
        var dashboard = commandCenterService.getDashboard(tenantId);
        assertThat(dashboard.healthScore()).isBetween(0, 100);

        // 2. Revenue Oversight works
        var revenue = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat(revenue).containsKey("crmWonRevenue");

        // 3. Operational Overview works
        var ops = operationalService.getOperationalOverview(tenantId);
        assertThat(ops).containsKey("overallHealthStatus");

        // 4. Module Registry works
        assertThat(moduleRegistry.allModules().size()).isGreaterThanOrEqualTo(5);

        // 5. Governance Configuration works
        assertThat(governanceConfigService.list(tenantId)).isNotNull();

        // 6. Executive Report aggregates everything
        var report = reportService.generateReport(tenantId);
        assertThat(report).containsKeys(
                "commandCenter", "revenueOverview", "operationalOverview",
                "moduleHealth", "executiveIntelligence", "_metadata");
    }

    // ===== v20260815.9 — CONSISTENCY + REAL DATA TESTS =====

    @Test
    void dashboard_revenue_matchesDedicatedRevenueEndpoint() {
        // PHASE 7 requirement #6: Dashboard Revenue == Revenue endpoint result
        var dashboard = commandCenterService.getDashboard(tenantId);
        var dedicated = revenueService.getExecutiveRevenueOverview(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> dashboardRevenue = (Map<String, Object>) dashboard.revenueOverview();
        // Both must contain the same keys
        assertThat(dashboardRevenue.keySet()).containsAll(
                java.util.List.of("crmWonRevenue", "invoiceTotalValue", "collectedRevenue",
                        "outstandingAmount", "revenueVariance", "sourceModules"));
        assertThat(dedicated.keySet()).containsAll(dashboardRevenue.keySet());
        // Values must match
        assertThat(dashboardRevenue.get("crmWonRevenue")).isEqualTo(dedicated.get("crmWonRevenue"));
        assertThat(dashboardRevenue.get("invoiceTotalValue")).isEqualTo(dedicated.get("invoiceTotalValue"));
        assertThat(dashboardRevenue.get("collectedRevenue")).isEqualTo(dedicated.get("collectedRevenue"));
    }

    @Test
    void dashboard_operations_matchesDedicatedOperationsEndpoint() {
        // PHASE 7 requirement #7: Dashboard Operations == Operations endpoint result
        var dashboard = commandCenterService.getDashboard(tenantId);
        var dedicated = operationalService.getOperationalOverview(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> dashboardOps = (Map<String, Object>) dashboard.operationalOverview();
        assertThat(dashboardOps.keySet()).containsAll(
                java.util.List.of("overallHealthStatus", "moduleCount", "totalOpenAlerts",
                        "totalOpenRisks", "totalOpenIssues", "slaOverallState"));
        assertThat(dedicated.keySet()).containsAll(dashboardOps.keySet());
        assertThat(dashboardOps.get("overallHealthStatus")).isEqualTo(dedicated.get("overallHealthStatus"));
        assertThat(dashboardOps.get("moduleCount")).isEqualTo(dedicated.get("moduleCount"));
    }

    @Test
    void executiveReport_revenue_matchesRevenueService() {
        // PHASE 7 requirement #8: Executive Report Revenue == RevenueOversightService result
        var report = reportService.generateReport(tenantId);
        var revenue = revenueService.getExecutiveRevenueOverview(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> reportRevenue = (Map<String, Object>) report.get("revenueOverview");
        assertThat(reportRevenue.get("crmWonRevenue")).isEqualTo(revenue.get("crmWonRevenue"));
        assertThat(reportRevenue.get("invoiceTotalValue")).isEqualTo(revenue.get("invoiceTotalValue"));
    }

    @Test
    void executiveReport_operations_matchesOperationalService() {
        // PHASE 7 requirement #9: Executive Report Operations == CrossModuleOperationalOverviewService
        var report = reportService.generateReport(tenantId);
        var ops = operationalService.getOperationalOverview(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> reportOps = (Map<String, Object>) report.get("operationalOverview");
        assertThat(reportOps.get("overallHealthStatus")).isEqualTo(ops.get("overallHealthStatus"));
        assertThat(reportOps.get("moduleCount")).isEqualTo(ops.get("moduleCount"));
    }

    @Test
    void emptyTenant_returnsLegitimateTypedZeroValues() {
        // PHASE 7 requirement #10: Empty tenant returns legitimate typed zero values
        var revenue = revenueService.getExecutiveRevenueOverview(tenantId);
        assertThat((java.math.BigDecimal) revenue.get("crmWonRevenue"))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat((java.math.BigDecimal) revenue.get("invoiceTotalValue"))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat((java.math.BigDecimal) revenue.get("collectedRevenue"))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat((java.math.BigDecimal) revenue.get("outstandingAmount"))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        // Source modules should still be present (CRM + FINANCE are available)
        @SuppressWarnings("unchecked")
        var sources = (java.util.List<String>) revenue.get("sourceModules");
        assertThat(sources).contains("CRM", "FINANCE");
    }

    @Test
    void noTransactionAbortErrors_whenCallingDashboard() {
        // PHASE 7 requirement #19: No transaction-aborted errors
        // Calling getDashboard multiple times should never throw
        // UnexpectedRollbackException or produce "transaction is aborted" errors.
        for (int i = 0; i < 3; i++) {
            var dashboard = commandCenterService.getDashboard(tenantId);
            assertThat(dashboard).isNotNull();
            assertThat(dashboard.healthScore()).isBetween(0, 100);
        }
        // Also verify snapshotHealth works (it calls getDashboard + saves)
        var snapshot = commandCenterService.snapshotHealth(tenantId);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.healthScore()).isBetween(0, 100);
    }

    @Test
    void noPlaceholderDataInDashboard() {
        // PHASE 7 requirement #20: No placeholder dashboard data
        var dashboard = commandCenterService.getDashboard(tenantId);
        @SuppressWarnings("unchecked")
        Map<String, Object> revenue = (Map<String, Object>) dashboard.revenueOverview();
        @SuppressWarnings("unchecked")
        Map<String, Object> ops = (Map<String, Object>) dashboard.operationalOverview();
        assertThat(revenue.containsKey("_note")).isFalse();
        assertThat(ops.containsKey("_note")).isFalse();
        // Revenue must have actual revenue fields (not just a note)
        assertThat(revenue).containsKey("crmWonRevenue");
        assertThat(revenue).containsKey("invoiceTotalValue");
        // Operations must have actual operational fields
        assertThat(ops).containsKey("overallHealthStatus");
        assertThat(ops).containsKey("moduleCount");
    }
}
