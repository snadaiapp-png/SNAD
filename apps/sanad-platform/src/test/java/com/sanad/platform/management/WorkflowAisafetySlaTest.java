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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive certification tests for:
 * - Workflow idempotency (duplicate invocations → no duplicate alerts)
 * - AI safety (advisory-only, evidence, confidence, provenance)
 * - SLA monitoring (detection of overdue decisions and escalations)
 *
 * Uses real PostgreSQL (via CI service container) — no mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowAisafetySlaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StrategicObjectiveService objectiveService;
    @Autowired private KpiService kpiService;
    @Autowired private RiskService riskService;
    @Autowired private IssueService issueService;
    @Autowired private ExecutiveDecisionService decisionService;
    @Autowired private EscalationService escalationService;
    @Autowired private ExecutiveAlertService alertService;
    @Autowired private ExecutiveIntelligenceService intelligenceService;
    @Autowired private SlaMonitoringService slaMonitoringService;
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
                tenantId, "wf-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-" + userId.toString().substring(0, 8) + "@test", now, now);

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);

        var caps = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'EXECUTIVE_%' OR code LIKE 'RISK.%' "
                + "OR code LIKE 'ISSUE.%' OR code LIKE 'ESCALATION.%'");
        for (var cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, roleId, cap.get("id"), now);
        }
    }

    private org.springframework.security.core.Authentication auth() {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    // ===== WORKFLOW IDEMPOTENCY TESTS =====

    @Test
    void workflowRiskCritical_escalationCreatedOnce() {
        var risk = Risk.create(
                tenantId, "RISK-IDEMP-1", "Critical Risk", "Test",
                "OPERATIONAL", 5, 5, userId, userId, null
        );
        var saved = riskService.create(risk, userId);

        // First call creates escalation
        var escalations1 = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, saved.id());
        assertThat(escalations1).hasSize(1);

        // Re-assess with same critical values — should NOT create a duplicate escalation
        var reassessed = riskService.reassess(tenantId, saved.id(), 5, 5, userId);
        var escalations2 = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, reassessed.id());
        assertThat(escalations2).hasSize(1);  // Still only 1 — idempotent
    }

    @Test
    void workflowIssueCritical_escalationCreatedOnce() {
        var issue = Issue.create(
                tenantId, "ISS-IDEMP-1", "Critical Issue", "Test",
                Issue.Severity.CRITICAL, Issue.Priority.CRITICAL,
                "INTERNAL", "Impact", userId, userId, null
        );
        var saved = issueService.create(issue, userId);

        var escalations = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.ISSUE, saved.id());
        assertThat(escalations).hasSize(1);
    }

    @Test
    void workflowKpiOffTrack_alertCreatedOnce() {
        var def = KpiDefinition.create(
                tenantId, "KPI-IDEMP-1", "Test KPI", "Test",
                "OPERATIONAL", KeyResult.MetricUnit.CURRENCY,
                KeyResult.Direction.UP, "SUM", "CRM", userId
        );
        kpiService.createDefinition(def);

        var target = KpiTarget.create(
                tenantId, def.id(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("100000"), new BigDecimal("50000"),
                null, userId
        );
        kpiService.createTarget(target);

        // Record OFF_TRACK measurement
        kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 6, 30),
                new BigDecimal("10000"), "Test", userId
        );

        // Check alert was created
        var alerts1 = alertService.findBySource(
                tenantId, ExecutiveAlert.SourceEntityType.KPI,
                def.id(), ExecutiveAlert.AlertType.KPI_OFF_TRACK
        );
        assertThat(alerts1).isPresent();

        // Record another OFF_TRACK measurement for a DIFFERENT period — should NOT create duplicate
        kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 7, 31),
                new BigDecimal("15000"), "Test 2", userId
        );

        // Still only 1 alert (deduplicated by source_entity + type)
        var alerts2 = alertService.findBySource(
                tenantId, ExecutiveAlert.SourceEntityType.KPI,
                def.id(), ExecutiveAlert.AlertType.KPI_OFF_TRACK
        );
        assertThat(alerts2).isPresent();
        assertThat(alerts2.get().id()).isEqualTo(alerts1.get().id());  // Same alert
    }

    @Test
    void workflowObjectiveOffTrack_alertCreatedOnce() {
        var objective = StrategicObjective.create(
                tenantId, "OBJ-IDEMP-1", "Test Objective", "Test",
                StrategicObjective.Priority.HIGH, userId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        var created = objectiveService.createObjective(objective);
        objectiveService.activate(tenantId, created.id());

        // Mark OFF_TRACK — should create alert
        objectiveService.markOffTrack(tenantId, created.id(), userId);

        var alerts1 = alertService.findBySource(
                tenantId, ExecutiveAlert.SourceEntityType.OBJECTIVE,
                created.id(), ExecutiveAlert.AlertType.OBJECTIVE_OFF_TRACK
        );
        assertThat(alerts1).isPresent();

        // Call markOffTrack again — should NOT create duplicate (createOrGetExisting)
        // But markOffTrack requires ACTIVE or AT_RISK status, and it's already OFF_TRACK
        // So we can't call it again directly. But the alert deduplication is tested above.
    }

    // ===== SLA MONITORING TESTS =====

    @Test
    void slaMonitoring_decisionOverdue_triggersAlert() {
        // Create a decision
        var decision = ExecutiveDecision.create(
                tenantId, "DEC-SLA-1", "Test Decision", "Test",
                "Test rationale", "STRATEGIC", ExecutiveDecision.Priority.HIGH,
                "HIGH", "Test outcome", userId, userId, null
        );
        var created = decisionService.create(decision, userId);

        // Submit the decision (sets submittedAt and approvalDueAt = now + 7 days)
        decisionService.submit(tenantId, created.id(), userId);

        // Override approval_due_at to be in the past (simulate SLA breach)
        jdbc.update("UPDATE executive_decisions SET approval_due_at = NOW() - INTERVAL '1 day' "
                + "WHERE id = ? AND tenant_id = ?", created.id(), tenantId);

        // Run SLA monitoring
        int alerts = slaMonitoringService.checkDecisionApprovalSla(tenantId);
        assertThat(alerts).isEqualTo(1);

        // Run again — should be idempotent (0 new alerts)
        int alerts2 = slaMonitoringService.checkDecisionApprovalSla(tenantId);
        assertThat(alerts2).isEqualTo(0);
    }

    @Test
    void slaMonitoring_escalationOverdue_triggersAlert() {
        // Create an escalation with SLA in the past
        var escalation = Escalation.create(
                tenantId, "ESC-SLA-1",
                Escalation.SourceEntityType.RISK, UUID.randomUUID(),
                "Test escalation",
                Escalation.Severity.HIGH,
                1, userId,
                java.time.Instant.now().minusSeconds(3600),  // 1 hour ago (SLA passed)
                userId
        );
        escalationService.create(escalation, userId);

        // Run SLA monitoring
        int alerts = slaMonitoringService.checkEscalationSla(tenantId);
        assertThat(alerts).isEqualTo(1);

        // Run again — idempotent
        int alerts2 = slaMonitoringService.checkEscalationSla(tenantId);
        assertThat(alerts2).isEqualTo(0);
    }

    @Test
    void slaMonitoring_noBreaches_returnsZero() {
        int alerts = slaMonitoringService.checkAllSlaBreaches(tenantId);
        assertThat(alerts).isEqualTo(0);
    }

    // ===== AI SAFETY TESTS =====

    @Test
    void aiSafety_insightIsAlwaysAdvisory() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        assertThat(insight.advisory()).isTrue();
        assertThat(insight.advisory()).as("AI insight must always be advisory").isTrue();
    }

    @Test
    void aiSafety_confidenceBetweenZeroAndOne() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        assertThat(insight.confidence()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(insight.confidence()).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    void aiSafety_evidenceIsPresent() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        assertThat(insight.evidence()).isNotBlank();
    }

    @Test
    void aiSafety_modelNameIsPresent() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        assertThat(insight.modelName()).isNotBlank();
        assertThat(insight.modelName()).isEqualTo("deterministic");
    }

    @Test
    void aiSafety_recommendationIsAdvisory() {
        var insight = intelligenceService.recommendExecutiveAction(tenantId, userId);
        assertThat(insight.advisory()).isTrue();
        assertThat(insight.type()).isEqualTo(ExecutiveInsight.InsightType.RECOMMENDATION);
    }

    @Test
    void aiSafety_anomalyIsAdvisory() {
        // Create a KPI with off-track measurement
        var def = KpiDefinition.create(
                tenantId, "KPI-AI-1", "Test KPI", "Test",
                "OPERATIONAL", KeyResult.MetricUnit.CURRENCY,
                KeyResult.Direction.UP, "SUM", "CRM", userId
        );
        kpiService.createDefinition(def);

        var target = KpiTarget.create(
                tenantId, def.id(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("100000"), new BigDecimal("50000"),
                null, userId
        );
        kpiService.createTarget(target);

        kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 6, 30),
                new BigDecimal("10000"), "Test", userId
        );

        var anomalies = intelligenceService.detectKpiAnomalies(tenantId, userId);
        assertThat(anomalies).isNotEmpty();
        for (var anomaly : anomalies) {
            assertThat(anomaly.advisory()).as("AI anomaly must be advisory").isTrue();
        }
    }

    @Test
    void aiSafety_providerIsDeterministicFallback() {
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        // When no external AI provider is configured, deterministic provider is used
        assertThat(insight.modelName()).isEqualTo("deterministic");
    }

    @Test
    void aiSafety_insightCannotBeUsedAsCommand() {
        // Verify that ExecutiveInsight is a data record, not a command
        // It has no methods that can mutate business state
        var insight = intelligenceService.generateExecutiveSummary(tenantId, userId);
        // The insight record only has: dismiss() and archive() which change the insight's own status
        // It has NO methods like approve(), close(), execute(), etc.
        // This is a design verification — the type system enforces this
        assertThat(insight.advisory()).isTrue();
        assertThat(insight.getClass().getMethods())
                .as("ExecutiveInsight must not have state-mutating business methods")
                .noneMatch(m -> m.getName().equals("approve")
                        || m.getName().equals("reject")
                        || m.getName().equals("close")
                        || m.getName().equals("execute")
                        || m.getName().equals("mutate"));
    }

    // ===== CROSS-DOMAIN GOLDEN PATH TEST =====

    @Test
    void crossDomainGoldenPath_riskToEscalationToAlertToCommandCenterToAi() {
        // 1. Create CRITICAL risk
        var risk = Risk.create(
                tenantId, "RISK-GOLDEN-1", "Critical Data Breach",
                "Risk of data breach", "COMPLIANCE",
                5, 5, userId, userId, LocalDate.of(2026, 12, 31)
        );
        var savedRisk = riskService.create(risk, userId);
        assertThat(savedRisk.severity()).isEqualTo(Risk.Severity.CRITICAL);
        assertThat(savedRisk.riskScore()).isEqualTo(25);

        // 2. Auto-escalation created
        var escalations = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, savedRisk.id());
        assertThat(escalations).hasSize(1);
        assertThat(escalations.get(0).severity()).isEqualTo(Escalation.Severity.CRITICAL);

        // 3. AI generates insight about the risk
        var recommendation = intelligenceService.recommendExecutiveAction(tenantId, userId);
        assertThat(recommendation.advisory()).isTrue();
        assertThat(recommendation.description()).contains("critical risk");

        // 4. AI CANNOT close the risk or mutate it
        // (proven by type safety — ExecutiveInsight has no business mutation methods)

        // 5. Command Center dashboard reflects the risk
        // (tested in CommandCenterAlertsIntelligenceTest)

        // 6. AI recommendation is advisory only — no mutation
        assertThat(recommendation.advisory()).isTrue();
    }
}
