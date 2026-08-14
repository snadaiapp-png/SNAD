package com.sanad.platform.management;

import com.sanad.platform.management.application.*;
import com.sanad.platform.management.domain.*;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for Phase D-G: Command Center, Alerts, AI Intelligence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class CommandCenterAlertsIntelligenceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ExecutiveCommandCenterService commandCenterService;
    @Autowired private ExecutiveAlertService alertService;
    @Autowired private ExecutiveIntelligenceService intelligenceService;
    @Autowired private StrategicObjectiveService objectiveService;
    @Autowired private RiskService riskService;
    @Autowired private IssueService issueService;
    @Autowired private ExecutiveDecisionService decisionService;
    @Autowired private KpiService kpiService;
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
                + "kpi_definitions, key_results, strategic_objectives RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "cc-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "cc-" + userId.toString().substring(0, 8) + "@test", now, now);

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);

        // Grant ALL management capabilities
        var caps = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'EXECUTIVE_%' OR code LIKE 'RISK.%' "
                + "OR code LIKE 'ISSUE.%' OR code LIKE 'ESCALATION.%'");
        for (var cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
        }
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    @Test
    void commandCenter_emptyTenant_returnsNoDataHealthScore() {
        var dashboard = commandCenterService.getDashboard(tenantId);
        // With no data, all scores should be 100 (no risk = healthy)
        assertThat(dashboard.healthScore()).isEqualTo(100);
        assertThat(dashboard.totalObjectives()).isEqualTo(0);
        assertThat(dashboard.totalKpis()).isEqualTo(0);
    }

    @Test
    void commandCenter_withCriticalRisk_lowersHealthScore() {
        // Create a CRITICAL risk
        var risk = Risk.create(
                tenantId, "RISK-CC-1", "Critical Risk", "Test",
                "OPERATIONAL", 5, 5, userId, userId, null
        );
        riskService.create(risk, userId);

        var dashboard = commandCenterService.getDashboard(tenantId);
        assertThat(dashboard.criticalRisks()).isEqualTo(1);
        assertThat(dashboard.riskScore()).isLessThan(100);
        assertThat(dashboard.healthScore()).isLessThan(100);
    }

    @Test
    void commandCenter_healthSnapshot_isImmutable() {
        var snap1 = commandCenterService.snapshotHealth(tenantId);
        assertThat(snap1.healthScore()).isEqualTo(100);

        // Create a critical risk to change health
        var risk = Risk.create(
                tenantId, "RISK-CC-2", "New Critical", "Test",
                "OPERATIONAL", 5, 5, userId, userId, null
        );
        riskService.create(risk, userId);

        var snap2 = commandCenterService.snapshotHealth(tenantId);
        assertThat(snap2.healthScore()).isLessThan(100);
        // First snapshot is immutable — should still be 100
        assertThat(snap1.healthScore()).isEqualTo(100);
    }

    @Test
    void alert_deduplication_sameSourceReturnsSameAlert() {
        // Create a critical risk (triggers auto-escalation)
        var risk = Risk.create(
                tenantId, "RISK-DEDUP-1", "Dedup Test", "Test",
                "OPERATIONAL", 5, 5, userId, userId, null
        );
        var saved = riskService.create(risk, userId);

        // Manually create an alert for the same risk
        var alert1 = alertService.createOrGetExisting(
                tenantId, ExecutiveAlert.AlertType.CRITICAL_RISK,
                ExecutiveAlert.Severity.CRITICAL,
                ExecutiveAlert.SourceEntityType.RISK, saved.id(),
                "Critical Risk Alert", "Risk reached CRITICAL", userId
        );
        assertThat(alert1.status()).isEqualTo(ExecutiveAlert.Status.OPEN);

        // Try to create again — should return the SAME alert (no duplicate)
        var alert2 = alertService.createOrGetExisting(
                tenantId, ExecutiveAlert.AlertType.CRITICAL_RISK,
                ExecutiveAlert.Severity.CRITICAL,
                ExecutiveAlert.SourceEntityType.RISK, saved.id(),
                "Critical Risk Alert", "Risk reached CRITICAL", userId
        );
        assertThat(alert2.id()).isEqualTo(alert1.id());
    }

    @Test
    void alert_lifecycle_acknowledgeAndResolve() {
        var alert = alertService.createOrGetExisting(
                tenantId, ExecutiveAlert.AlertType.KPI_OFF_TRACK,
                ExecutiveAlert.Severity.HIGH,
                ExecutiveAlert.SourceEntityType.KPI, UUID.randomUUID(),
                "KPI Off Track", "KPI is off track", userId
        );
        assertThat(alert.status()).isEqualTo(ExecutiveAlert.Status.OPEN);

        var acknowledged = alertService.acknowledge(tenantId, alert.id(), userId);
        assertThat(acknowledged.status()).isEqualTo(ExecutiveAlert.Status.ACKNOWLEDGED);

        var resolved = alertService.resolve(tenantId, alert.id(), "KPI recovered", userId);
        assertThat(resolved.status()).isEqualTo(ExecutiveAlert.Status.RESOLVED);
        assertThat(resolved.resolution()).isEqualTo("KPI recovered");
    }

    @Test
    void intelligence_executiveSummary_isAdvisoryAndTraceable() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        assertThat(insight.type()).isEqualTo(ExecutiveInsight.InsightType.SUMMARY);
        assertThat(insight.advisory()).isTrue();  // ALWAYS advisory
        assertThat(insight.modelName()).isEqualTo("deterministic");
        assertThat(insight.confidence()).isEqualByComparingTo(java.math.BigDecimal.ONE);
        assertThat(insight.evidence()).isNotBlank();  // has provenance
    }

    @Test
    void intelligence_anomalyDetection_offTrackKpi() {
        // Create a KPI + target + off-track measurement
        var def = KpiDefinition.create(
                tenantId, "KPI-ANOM-1", "Revenue",
                "Monthly revenue", "FINANCIAL",
                KeyResult.MetricUnit.CURRENCY, KeyResult.Direction.UP,
                "SUM", "BILLING", userId
        );
        kpiService.createDefinition(def);

        var target = KpiTarget.create(
                tenantId, def.id(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new java.math.BigDecimal("100000"),
                new java.math.BigDecimal("50000"),
                null, userId
        );
        kpiService.createTarget(target);

        // Record a very low measurement → OFF_TRACK
        kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 6, 30),
                new java.math.BigDecimal("10000"), "Test", userId
        );

        var anomalies = intelligenceService.detectKpiAnomalies(tenantId, userId);
        assertThat(anomalies).isNotEmpty();
        assertThat(anomalies.get(0).type()).isEqualTo(ExecutiveInsight.InsightType.ANOMALY);
        assertThat(anomalies.get(0).advisory()).isTrue();
    }

    @Test
    void intelligence_recommendAction_includesCriticalRisks() {
        // Create a critical risk
        var risk = Risk.create(
                tenantId, "RISK-REC-1", "Critical Risk",
                "Test critical", "OPERATIONAL",
                5, 5, userId, userId, null
        );
        riskService.create(risk, userId);

        var insight = intelligenceService.recommendExecutiveAction(tenantId, userId);
        assertThat(insight.type()).isEqualTo(ExecutiveInsight.InsightType.RECOMMENDATION);
        assertThat(insight.description()).contains("critical risk");
        assertThat(insight.advisory()).isTrue();
        assertThat(insight.evidence()).contains("critical_risks");
    }

    @Test
    void commandCenterApi_returnsHealthScore() throws Exception {
        mockMvc.perform(get("/api/v1/management/command-center")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthScore").exists())
                .andExpect(jsonPath("$.strategyScore").exists())
                .andExpect(jsonPath("$.kpiScore").exists())
                .andExpect(jsonPath("$.riskScore").exists());
    }

    @Test
    void alertsApi_listOpenAlerts() throws Exception {
        // Create an alert
        alertService.createOrGetExisting(
                tenantId, ExecutiveAlert.AlertType.KPI_OFF_TRACK,
                ExecutiveAlert.Severity.HIGH,
                ExecutiveAlert.SourceEntityType.KPI, UUID.randomUUID(),
                "Test Alert", "Test", userId
        );

        mockMvc.perform(get("/api/v1/management/alerts/open")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void intelligenceApi_generateSummary() throws Exception {
        mockMvc.perform(post("/api/v1/management/intelligence/summary")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SUMMARY"))
                .andExpect(jsonPath("$.advisory").value(true))
                .andExpect(jsonPath("$.modelName").value("deterministic"));
    }

    @Test
    void intelligenceApi_recommendAction() throws Exception {
        mockMvc.perform(post("/api/v1/management/intelligence/recommend")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RECOMMENDATION"))
                .andExpect(jsonPath("$.advisory").value(true));
    }
}
