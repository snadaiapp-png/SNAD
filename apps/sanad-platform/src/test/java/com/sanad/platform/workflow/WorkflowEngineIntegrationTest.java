package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.*;
import com.sanad.platform.workflow.domain.*;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Comprehensive integration test for the Workflow Engine.
 *
 * Covers:
 * - Workflow definition lifecycle (create → activate → deactivate → archive)
 * - Workflow instance lifecycle (start → advance → complete)
 * - Approval lifecycle (create → approve with segregation of duties)
 * - Approval rejection path
 * - Cross-tenant isolation
 * - SLA monitoring
 * - API endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowEngineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowDefinitionService defService;
    @Autowired private WorkflowExecutionService execService;
    @Autowired private WorkflowApprovalService approvalService;
    @Autowired private WorkflowMonitoringService monitoringService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;
    private UUID approverId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        approverId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-" + tenantId.toString().substring(0, 8), now, now);
        for (var uid : List.of(userId, approverId)) {
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                    uid, tenantId, "wf-" + uid.toString().substring(0, 8) + "@test", now, now);
        }

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);

        var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'WORKFLOW.%'");
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

    // ===== DEFINITION LIFECYCLE =====

    @Test
    void definitionLifecycle_createActivateDeactivateArchive() {
        var def = WorkflowDefinition.create(
                tenantId, "WF-001", "Approval Workflow", "Test workflow",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        assertThat(created.status()).isEqualTo(WorkflowDefinition.Status.DRAFT);

        // Add a step
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "step1", "Review",
                        WorkflowStep.StepType.APPROVAL, 1, null, 48, "WORKFLOW.APPROVE", null));

        var activated = defService.activate(tenantId, created.id(), userId);
        assertThat(activated.status()).isEqualTo(WorkflowDefinition.Status.ACTIVE);

        var deactivated = defService.deactivate(tenantId, created.id(), userId);
        assertThat(deactivated.status()).isEqualTo(WorkflowDefinition.Status.INACTIVE);

        var archived = defService.archive(tenantId, created.id(), userId);
        assertThat(archived.status()).isEqualTo(WorkflowDefinition.Status.ARCHIVED);
    }

    // ===== INSTANCE LIFECYCLE =====

    @Test
    void instanceLifecycle_startAdvanceComplete() {
        // Create + activate a definition with 2 steps
        var def = WorkflowDefinition.create(
                tenantId, "WF-INST-1", "Two Step", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "step1", "First",
                        WorkflowStep.StepType.ACTION, 1, null, null, null, null));
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "step2", "Last",
                        WorkflowStep.StepType.END, 2, null, null, null, null));
        defService.activate(tenantId, created.id(), userId);

        // Start instance
        var instance = execService.startWorkflow(
                tenantId, created.id(), "TEST_ENTITY", UUID.randomUUID(), userId
        );
        assertThat(instance.status()).isEqualTo(WorkflowInstance.Status.RUNNING);
        assertThat(instance.currentStepKey()).isEqualTo("step1");

        // Advance to step2
        var advanced = execService.advanceToNextStep(tenantId, instance.id(), userId, "done");
        assertThat(advanced.currentStepKey()).isEqualTo("step2");

        // Complete (step2 is END type → workflow completes)
        var completed = execService.complete(tenantId, instance.id(), userId);
        assertThat(completed.status()).isEqualTo(WorkflowInstance.Status.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
    }

    // ===== APPROVAL LIFECYCLE =====

    @Test
    void approval_approveWithDifferentUser() {
        // Create definition with APPROVAL step
        var def = WorkflowDefinition.create(
                tenantId, "WF-APPR-1", "Approval WF", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "approve1", "Manager Approval",
                        WorkflowStep.StepType.APPROVAL, 1, null, 48, "WORKFLOW.APPROVE", null));
        defService.activate(tenantId, created.id(), userId);

        // Start instance
        var instance = execService.startWorkflow(
                tenantId, created.id(), "DECISION", UUID.randomUUID(), userId
        );

        // Create approval request (requested FROM approverId)
        var approval = approvalService.createApproval(
                tenantId, instance.id(), instance.currentStepKey(), approverId, "MANAGER",
                Instant.now().plus(48, ChronoUnit.HOURS), userId
        );
        assertThat(approval.status()).isEqualTo(WorkflowApprovalRequest.Status.PENDING);

        // Approve with approverId (different from userId who created it)
        var approved = approvalService.approve(tenantId, approval.id(), approverId, "Approved");
        assertThat(approved.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);
        assertThat(approved.actedBy()).isEqualTo(approverId);
    }

    // ===== SEGREGATION OF DUTIES =====

    @Test
    void approval_segregationOfDuties_requesterCannotApprove() {
        var def = WorkflowDefinition.create(
                tenantId, "WF-SOD-1", "SOD Test", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "approve1", "Approval",
                        WorkflowStep.StepType.APPROVAL, 1, null, 48, "WORKFLOW.APPROVE", null));
        defService.activate(tenantId, created.id(), userId);

        var instance = execService.startWorkflow(
                tenantId, created.id(), "TEST", UUID.randomUUID(), userId
        );

        // Create approval requested FROM userId (same as who will try to approve)
        var approval = approvalService.createApproval(
                tenantId, instance.id(), instance.currentStepKey(), userId, "MANAGER",
                Instant.now().plus(48, ChronoUnit.HOURS), userId
        );

        // Try to approve with the SAME user → should fail
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> approvalService.approve(tenantId, approval.id(), userId, "self-approve"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Segregation of duties");
    }

    // ===== REJECTION PATH =====

    @Test
    void approval_rejectionPath() {
        var def = WorkflowDefinition.create(
                tenantId, "WF-REJ-1", "Rejection WF", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "approve1", "Approval",
                        WorkflowStep.StepType.APPROVAL, 1, null, 48, "WORKFLOW.APPROVE", null));
        defService.activate(tenantId, created.id(), userId);

        var instance = execService.startWorkflow(
                tenantId, created.id(), "TEST", UUID.randomUUID(), userId
        );

        var approval = approvalService.createApproval(
                tenantId, instance.id(), instance.currentStepKey(), approverId, "MANAGER",
                Instant.now().plus(48, ChronoUnit.HOURS), userId
        );

        var rejected = approvalService.reject(tenantId, approval.id(), approverId, "Not good");
        assertThat(rejected.status()).isEqualTo(WorkflowApprovalRequest.Status.REJECTED);
    }

    // ===== CROSS-TENANT ISOLATION =====

    @Test
    void crossTenant_isolation() {
        var def = WorkflowDefinition.create(
                tenantId, "WF-ISO-1", "Isolation Test", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);

        // Try to find with different tenant
        var otherTenant = UUID.randomUUID();
        var found = defService.findById(otherTenant, created.id());
        assertThat(found).isEmpty();
    }

    // ===== API TESTS =====

    @Test
    void api_createDefinition_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/definitions")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {"code":"WF-API-1","name":"API Workflow","description":"Test",
                                 "module":"GENERAL","triggerType":"MANUAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WF-API-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void api_listDefinitions_returnsOk() throws Exception {
        defService.create(WorkflowDefinition.create(
                tenantId, "WF-LIST-1", "List Test", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId), userId);

        mockMvc.perform(get("/api/v1/workflows/definitions")
                        .with(authentication(auth())))
                .andExpect(status().isOk());
    }

    @Test
    void api_startInstance_returnsOk() throws Exception {
        var def = WorkflowDefinition.create(
                tenantId, "WF-START-1", "Start Test", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId
        );
        var created = defService.create(def, userId);
        defService.addStep(tenantId, created.id(),
                WorkflowStep.create(tenantId, created.id(), "s1", "Step 1",
                        WorkflowStep.StepType.END, 1, null, null, null, null));
        defService.activate(tenantId, created.id(), userId);

        mockMvc.perform(post("/api/v1/workflows/instances")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content(String.format("""
                                {"workflowDefinitionId":"%s","businessEntityType":"TEST","businessEntityId":"%s"}
                                """, created.id(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void api_monitoringHealth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/monitoring/health")
                        .with(authentication(auth())))
                .andExpect(status().isOk());
    }
}
