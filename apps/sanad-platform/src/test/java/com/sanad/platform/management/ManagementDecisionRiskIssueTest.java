package com.sanad.platform.management;

import com.sanad.platform.management.application.EscalationService;
import com.sanad.platform.management.application.ExecutiveDecisionService;
import com.sanad.platform.management.application.IssueService;
import com.sanad.platform.management.application.RiskService;
import com.sanad.platform.management.domain.Escalation;
import com.sanad.platform.management.domain.ExecutiveDecision;
import com.sanad.platform.management.domain.Issue;
import com.sanad.platform.management.domain.Risk;
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
 * Integration test for the Senior Management Phase B+C:
 * Decision Engine, Risk Engine, Issue Engine, Escalation Engine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class ManagementDecisionRiskIssueTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ExecutiveDecisionService decisionService;
    @Autowired private RiskService riskService;
    @Autowired private IssueService issueService;
    @Autowired private EscalationService escalationService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID approverId;

    @BeforeEach
    void setUp() {
        // Truncate all management tables
        jdbc.execute("TRUNCATE TABLE management_audit_trail, escalations, decision_actions, "
                + "decision_participants, executive_decisions, risks, issues, "
                + "strategic_initiatives, kpi_measurements, kpi_targets, "
                + "kpi_definitions, key_results, strategic_objectives RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        approverId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        // Seed tenant + users + ADMIN role + all management capabilities
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "dec-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "user-" + userId.toString().substring(0, 8) + "@test", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'Approver', 'ACTIVE', 'dummy', ?, ?)",
                approverId, tenantId, "appr-" + approverId.toString().substring(0, 8) + "@test", now, now);

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);

        // Grant ALL management capabilities
        var caps = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code LIKE 'EXECUTIVE_DECISIONS.%' "
                + "OR code LIKE 'RISK.%' OR code LIKE 'ISSUE.%' OR code LIKE 'ESCALATION.%' "
                + "OR code LIKE 'EXECUTIVE_MANAGEMENT.%'");
        for (var cap : caps) {
            var capId = (UUID) cap.get("id");
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, roleId, capId, now);
        }
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    @Test
    void decisionLifecycle_createSubmitReviewApproveExecuteComplete() {
        var decision = ExecutiveDecision.create(
                tenantId, "DEC-001", "Hire 10 Engineers",
                "Scale the engineering team", "Need more capacity for Q4",
                "PERSONNEL", ExecutiveDecision.Priority.HIGH, "MEDIUM",
                "10 engineers hired and onboarded", userId, userId, LocalDate.of(2026, 12, 31)
        );
        var created = decisionService.create(decision, userId);
        assertThat(created.status()).isEqualTo(ExecutiveDecision.Status.DRAFT);

        var submitted = decisionService.submit(tenantId, created.id(), userId);
        assertThat(submitted.status()).isEqualTo(ExecutiveDecision.Status.SUBMITTED);

        var reviewing = decisionService.startReview(tenantId, created.id(), userId);
        assertThat(reviewing.status()).isEqualTo(ExecutiveDecision.Status.UNDER_REVIEW);

        // Approve — must use a DIFFERENT user (segregation of duties)
        var approved = decisionService.approve(tenantId, created.id(), approverId);
        assertThat(approved.status()).isEqualTo(ExecutiveDecision.Status.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo(approverId);

        var executing = decisionService.startExecuting(tenantId, created.id(), userId);
        assertThat(executing.status()).isEqualTo(ExecutiveDecision.Status.EXECUTING);
        assertThat(executing.executedAt()).isNotNull();

        var completed = decisionService.complete(tenantId, created.id(), "10 engineers hired", userId);
        assertThat(completed.status()).isEqualTo(ExecutiveDecision.Status.COMPLETED);
        assertThat(completed.actualOutcome()).isEqualTo("10 engineers hired");
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void decision_segregationOfDuties_creatorCannotApprove() {
        var decision = ExecutiveDecision.create(
                tenantId, "DEC-002", "Test Decision", "Test", "Test",
                "OTHER", ExecutiveDecision.Priority.NORMAL, "LOW",
                "Test outcome", userId, userId, null
        );
        var created = decisionService.create(decision, userId);
        decisionService.submit(tenantId, created.id(), userId);
        decisionService.startReview(tenantId, created.id(), userId);

        // Try to approve with the SAME user who created it — should fail
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> decisionService.approve(tenantId, created.id(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Segregation of duties");
    }

    @Test
    void risk_scoringAndAutoEscalation() {
        // Create a CRITICAL risk (probability=5, impact=5, score=25)
        var risk = Risk.create(
                tenantId, "RISK-001", "Data Breach",
                "Risk of customer data breach", "COMPLIANCE",
                5, 5, userId, userId, LocalDate.of(2026, 12, 31)
        );
        var saved = riskService.create(risk, userId);

        assertThat(saved.riskScore()).isEqualTo(25);
        assertThat(saved.severity()).isEqualTo(Risk.Severity.CRITICAL);

        // Auto-escalation should have been created
        var escalations = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, saved.id());
        assertThat(escalations).hasSize(1);
        assertThat(escalations.get(0).severity()).isEqualTo(Escalation.Severity.CRITICAL);
        assertThat(escalations.get(0).sourceEntityType()).isEqualTo(Escalation.SourceEntityType.RISK);
    }

    @Test
    void risk_reassessChangesSeverityAndTriggersEscalation() {
        // Create a LOW risk (probability=1, impact=2, score=2)
        var risk = Risk.create(
                tenantId, "RISK-002", "Minor Delay",
                "Minor delivery delay", "OPERATIONAL",
                1, 2, userId, userId, null
        );
        var saved = riskService.create(risk, userId);
        assertThat(saved.severity()).isEqualTo(Risk.Severity.LOW);

        // No escalation for LOW risk
        var escalationsBefore = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, saved.id());
        assertThat(escalationsBefore).isEmpty();

        // Reassess to CRITICAL (probability=5, impact=5)
        var reassessed = riskService.reassess(tenantId, saved.id(), 5, 5, userId);
        assertThat(reassessed.severity()).isEqualTo(Risk.Severity.CRITICAL);

        // Now escalation should exist
        var escalationsAfter = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.RISK, saved.id());
        assertThat(escalationsAfter).hasSize(1);
    }

    @Test
    void issueLifecycle_createTriageProgressResolveClose() {
        var issue = Issue.create(
                tenantId, "ISS-001", "Production Outage",
                "CRM was down for 30 minutes",
                Issue.Severity.HIGH, Issue.Priority.HIGH,
                "MONITORING", "200 users affected",
                userId, userId, LocalDate.of(2026, 8, 20)
        );
        var created = issueService.create(issue, userId);
        assertThat(created.status()).isEqualTo(Issue.Status.OPEN);

        var triaged = issueService.triage(tenantId, created.id(), userId);
        assertThat(triaged.status()).isEqualTo(Issue.Status.TRIAGED);

        var inProgress = issueService.startProgress(tenantId, created.id(), userId);
        assertThat(inProgress.status()).isEqualTo(Issue.Status.IN_PROGRESS);

        var resolved = issueService.resolve(tenantId, created.id(), "Restarted server", userId);
        assertThat(resolved.status()).isEqualTo(Issue.Status.RESOLVED);
        assertThat(resolved.resolution()).isEqualTo("Restarted server");

        var closed = issueService.close(tenantId, created.id(), userId);
        assertThat(closed.status()).isEqualTo(Issue.Status.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void issue_criticalSeverityTriggersAutoEscalation() {
        var issue = Issue.create(
                tenantId, "ISS-002", "Critical Data Loss",
                "Customer data lost in production",
                Issue.Severity.CRITICAL, Issue.Priority.CRITICAL,
                "INTERNAL", "All customer data at risk",
                userId, userId, null
        );
        var saved = issueService.create(issue, userId);

        // Auto-escalation should exist
        var escalations = escalationService.findBySourceEntity(
                tenantId, Escalation.SourceEntityType.ISSUE, saved.id());
        assertThat(escalations).hasSize(1);
        assertThat(escalations.get(0).sourceEntityType()).isEqualTo(Escalation.SourceEntityType.ISSUE);
    }

    @Test
    void escalationLifecycle_acknowledgeAndResolve() {
        var escalation = Escalation.create(
                tenantId, "ESC-001",
                Escalation.SourceEntityType.DECISION, UUID.randomUUID(),
                "Manual escalation for testing",
                Escalation.Severity.HIGH,
                2, userId, null, userId
        );
        var created = escalationService.create(escalation, userId);
        assertThat(created.status()).isEqualTo(Escalation.Status.ACTIVE);

        var acknowledged = escalationService.acknowledge(tenantId, created.id(), userId);
        assertThat(acknowledged.status()).isEqualTo(Escalation.Status.ACKNOWLEDGED);

        var resolved = escalationService.resolve(tenantId, created.id(), "Issue resolved", userId);
        assertThat(resolved.status()).isEqualTo(Escalation.Status.RESOLVED);
        assertThat(resolved.resolution()).isEqualTo("Issue resolved");
        assertThat(resolved.resolvedAt()).isNotNull();
    }

    @Test
    void decisionApi_createViaRest() throws Exception {
        mockMvc.perform(post("/api/v1/management/decisions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {
                                    "decisionNumber": "DEC-API-1",
                                    "title": "API Decision",
                                    "description": "Created via API",
                                    "rationale": "Test",
                                    "category": "STRATEGIC",
                                    "priority": "HIGH",
                                    "impact": "MEDIUM",
                                    "expectedOutcome": "Test outcome",
                                    "dueDate": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionNumber").value("DEC-API-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void riskApi_createViaRest() throws Exception {
        mockMvc.perform(post("/api/v1/management/risks")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {
                                    "code": "RISK-API-1",
                                    "title": "API Risk",
                                    "description": "Test risk",
                                    "category": "FINANCIAL",
                                    "probability": 3,
                                    "impact": 4,
                                    "dueDate": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RISK-API-1"))
                .andExpect(jsonPath("$.riskScore").value(12))
                .andExpect(jsonPath("$.severity").value("HIGH"));
    }

    @Test
    void issueApi_createViaRest() throws Exception {
        mockMvc.perform(post("/api/v1/management/issues")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {
                                    "code": "ISS-API-1",
                                    "title": "API Issue",
                                    "description": "Test issue",
                                    "severity": "HIGH",
                                    "priority": "NORMAL",
                                    "source": "INTERNAL",
                                    "impact": "Some impact",
                                    "dueDate": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ISS-API-1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

}
