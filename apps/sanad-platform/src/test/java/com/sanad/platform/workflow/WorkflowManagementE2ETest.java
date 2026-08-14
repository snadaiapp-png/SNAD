package com.sanad.platform.workflow;

import com.sanad.platform.management.application.DecisionWorkflowIntegrationService;
import com.sanad.platform.management.application.ExecutiveDecisionService;
import com.sanad.platform.management.domain.*;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-Domain End-to-End test covering the golden path:
 *
 * <ol>
 *   <li>Tenant setup (tenantA + tenantB)</li>
 *   <li>Users (requester, approver)</li>
 *   <li>Workflow definition (DECISION_APPROVAL) with steps</li>
 *   <li>Senior management decision (DRAFT)</li>
 *   <li>Decision submitted (state SUBMITTED, SLA starts)</li>
 *   <li>Workflow instance started automatically via integration service</li>
 *   <li>Approval request created (requested_from=owner, requested_by=submitter)</li>
 *   <li>Authorized approver approves via workflow engine</li>
 *   <li>Decision becomes APPROVED</li>
 *   <li>Audit records in both management_audit_trail and workflow_transition_audit</li>
 *   <li>Tenant isolation: tenantB cannot see tenantA's data</li>
 * </ol>
 *
 * <p>Negative golden path:
 * <ul>
 *   <li>Requester attempts self-approval → rejected by SOD</li>
 *   <li>Decision remains non-approved</li>
 *   <li>Audit/security event verified</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowManagementE2ETest {

    @Autowired private ExecutiveDecisionService decisionService;
    @Autowired private DecisionWorkflowIntegrationService integrationService;
    @Autowired private WorkflowDefinitionService defService;
    @Autowired private WorkflowExecutionService execService;
    @Autowired private WorkflowApprovalService approvalService;
    @Autowired private WorkflowMonitoringService monitoringService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID requesterA;     // creates and submits the decision
    private UUID approverA;      // approves the decision
    private UUID userB;          // tenantB user

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE management_audit_trail, escalations, executive_alerts, "
                + "executive_insights, executive_health_snapshots, "
                + "decision_actions, decision_participants, executive_decisions, "
                + "risks, issues, "
                + "strategic_initiatives, kpi_measurements, kpi_targets, "
                + "kpi_definitions, key_results, strategic_objectives RESTART IDENTITY CASCADE");

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        requesterA = UUID.randomUUID();
        approverA = UUID.randomUUID();
        userB = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        for (var tid : List.of(tenantA, tenantB)) {
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tid, "Tenant " + tid.toString().substring(0, 8),
                    "e2e-" + tid.toString().substring(0, 8), now, now);
        }
        for (var uid : List.of(requesterA, approverA)) {
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                    uid, tenantA, "e2e-" + uid.toString().substring(0, 8) + "@test", now, now);
        }
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'User B', 'ACTIVE', 'dummy', ?, ?)",
                userB, tenantB, "e2e-" + userB.toString().substring(0, 8) + "@test", now, now);

        // Grant ALL capabilities to both tenants
        for (var tid : List.of(tenantA, tenantB)) {
            var uid = tid == tenantA ? requesterA : userB;
            var roleId = UUID.randomUUID();
            jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                    + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                    roleId, tid, now, now);
            var caps = jdbc.queryForList(
                    "SELECT id FROM access_capabilities WHERE code LIKE 'WORKFLOW.%' "
                    + "OR code LIKE 'EXECUTIVE_%' OR code LIKE 'RISK.%' OR code LIKE 'ISSUE.%' "
                    + "OR code LIKE 'ESCALATION.%'");
            for (var cap : caps) {
                jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                        + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tid, roleId, cap.get("id"), now);
            }
        }
    }

    /** Build a DECISION_APPROVAL workflow definition with 2 steps (REVIEW + APPROVE). */
    private void buildDecisionApprovalWorkflow(UUID tenantId, UUID userId) {
        var def = WorkflowDefinition.create(
                tenantId, DecisionWorkflowIntegrationService.DECISION_APPROVAL_WORKFLOW_CODE,
                "Decision Approval Workflow", "Auto-created for decision approvals",
                "MANAGEMENT", WorkflowDefinition.TriggerType.MANUAL, userId);
        var savedDef = defService.create(def, userId);
        defService.activate(tenantId, savedDef.id(), userId);

        // Add 2 steps
        var step1Id = UUID.randomUUID();
        var now = java.sql.Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO workflow_steps "
                + "(id, tenant_id, workflow_definition_id, step_key, name, step_type, sequence_order, "
                + "sla_hours, required_capability, required_role, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'REVIEW', 'Review Decision', 'APPROVAL', 1, "
                + "168, 'EXECUTIVE_DECISIONS.APPROVE', 'DECISION_APPROVER', 0, ?, ?)",
                step1Id, tenantId, savedDef.id(), now, now);

        var step2Id = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_steps "
                + "(id, tenant_id, workflow_definition_id, step_key, name, step_type, sequence_order, "
                + "sla_hours, required_capability, required_role, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'APPROVE', 'Final Approval', 'APPROVAL', 2, "
                + "168, 'EXECUTIVE_DECISIONS.APPROVE', 'DECISION_APPROVER', 0, ?, ?)",
                step2Id, tenantId, savedDef.id(), now, now);
    }

    // ===== POSITIVE GOLDEN PATH =====

    @Test
    void goldenPath_decisionApprovalViaWorkflow() {
        // 1. Build the DECISION_APPROVAL workflow definition
        buildDecisionApprovalWorkflow(tenantA, requesterA);

        // 2. Create a decision
        var decision = ExecutiveDecision.create(
                tenantA, "DEC-E2E-1", "Test Decision", "Description",
                "Rationale", "STRATEGIC", ExecutiveDecision.Priority.HIGH,
                "High impact", "Expected outcome",
                approverA,  // ownerUserId (will be the approver)
                requesterA,  // createdBy (will be the requester)
                null
        );
        var created = decisionService.create(decision, requesterA);
        assertThat(created.status()).isEqualTo(ExecutiveDecision.Status.DRAFT);

        // 3. Submit the decision (state SUBMITTED, SLA starts)
        var submitted = decisionService.submit(tenantA, created.id(), requesterA);
        assertThat(submitted.status()).isEqualTo(ExecutiveDecision.Status.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(submitted.approvalDueAt()).isNotNull();

        // 4. Start workflow instance + approval request via integration service
        var workflowInstanceId = integrationService.startWorkflowForDecision(
                tenantA, submitted.id(), requesterA);
        assertThat(workflowInstanceId).isNotNull();

        // 5. Verify the workflow instance was created
        var instanceOpt = execService.findById(tenantA, workflowInstanceId);
        assertThat(instanceOpt).isPresent();
        assertThat(instanceOpt.get().businessEntityType()).isEqualTo("DECISION");
        assertThat(instanceOpt.get().businessEntityId()).isEqualTo(submitted.id());
        assertThat(instanceOpt.get().status()).isEqualTo(WorkflowInstance.Status.RUNNING);

        // 6. Verify the approval request was created with proper SOD fields
        var approvals = approvalService.findByInstance(tenantA, workflowInstanceId);
        assertThat(approvals).hasSize(1);
        var approval = approvals.get(0);
        assertThat(approval.status()).isEqualTo(WorkflowApprovalRequest.Status.PENDING);
        assertThat(approval.requestedFromUserId()).isEqualTo(approverA);  // owner
        assertThat(approval.requestedByUserId()).isEqualTo(requesterA);  // submitter (SOD enforced)

        // 7. Approver (different from requester) approves via workflow engine
        var approvedDecision = integrationService.approveDecisionViaWorkflow(
                tenantA, submitted.id(), approverA, "approved by approver");
        assertThat(approvedDecision.status()).isEqualTo(ExecutiveDecision.Status.APPROVED);
        assertThat(approvedDecision.decidedBy()).isEqualTo(approverA);

        // 8. Verify the workflow approval is APPROVED
        var finalApprovals = approvalService.findByInstance(tenantA, workflowInstanceId);
        assertThat(finalApprovals).hasSize(1);
        assertThat(finalApprovals.get(0).status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);
        assertThat(finalApprovals.get(0).actedBy()).isEqualTo(approverA);

        // 9. Verify audit records in both management_audit_trail and workflow_transition_audit
        var mgmtAuditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM management_audit_trail WHERE entity_id = ?",
                Integer.class, submitted.id());
        assertThat(mgmtAuditCount).isGreaterThanOrEqualTo(2);  // CREATE + APPROVE (at minimum)

        var wfAuditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ?",
                Integer.class, workflowInstanceId);
        assertThat(wfAuditCount).isGreaterThanOrEqualTo(2);  // START + APPROVE (at minimum)

        // 10. Tenant isolation: tenantB cannot see tenantA's data
        var tenantBDecision = decisionService.findById(tenantB, submitted.id());
        assertThat(tenantBDecision).isEmpty();

        var tenantBInstance = execService.findById(tenantB, workflowInstanceId);
        assertThat(tenantBInstance).isEmpty();
    }

    // ===== NEGATIVE GOLDEN PATH: SELF-APPROVAL REJECTED =====

    @Test
    void negativeGoldenPath_requesterSelfApprovalRejected() {
        // 1. Build the DECISION_APPROVAL workflow definition
        buildDecisionApprovalWorkflow(tenantA, requesterA);

        // 2. Create a decision — owner = requester (so submitter == owner, common case)
        var decision = ExecutiveDecision.create(
                tenantA, "DEC-E2E-NEG-1", "Test Decision", "Description",
                "Rationale", "STRATEGIC", ExecutiveDecision.Priority.HIGH,
                "High impact", "Expected outcome",
                requesterA,  // ownerUserId == createdBy (same person)
                requesterA,  // createdBy
                null
        );
        var created = decisionService.create(decision, requesterA);
        decisionService.submit(tenantA, created.id(), requesterA);

        // 3. Start workflow
        var workflowInstanceId = integrationService.startWorkflowForDecision(
                tenantA, created.id(), requesterA);
        assertThat(workflowInstanceId).isNotNull();

        // 4. Requester attempts self-approval via workflow — SOD should reject
        assertThatThrownBy(() ->
                integrationService.approveDecisionViaWorkflow(
                        tenantA, created.id(), requesterA, "self approve"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Segregation of duties");

        // 5. Verify decision remains non-approved (still SUBMITTED or UNDER_REVIEW)
        var decisionAfterAttempt = decisionService.findById(tenantA, created.id()).orElseThrow();
        assertThat(decisionAfterAttempt.status())
                .isIn(ExecutiveDecision.Status.SUBMITTED, ExecutiveDecision.Status.UNDER_REVIEW);

        // 6. Verify the workflow approval is still PENDING (rollback worked)
        var approvals = approvalService.findByInstance(tenantA, workflowInstanceId);
        assertThat(approvals).hasSize(1);
        assertThat(approvals.get(0).status()).isEqualTo(WorkflowApprovalRequest.Status.PENDING);

        // 7. Verify NO APPROVE audit record was created (transaction rolled back)
        var approveAuditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ? AND action = 'APPROVE'",
                Integer.class, workflowInstanceId);
        assertThat(approveAuditCount).isZero();

        // 8. A different authorized approver CAN still approve after the failed attempt
        var approved = integrationService.approveDecisionViaWorkflow(
                tenantA, created.id(), approverA, "approved by different approver");
        assertThat(approved.status()).isEqualTo(ExecutiveDecision.Status.APPROVED);
    }

    // ===== TENANT ISOLATION: CROSS-TENANT APPROVAL BLOCKED =====

    @Test
    void crossTenantApproval_blocked() {
        buildDecisionApprovalWorkflow(tenantA, requesterA);

        var decision = ExecutiveDecision.create(
                tenantA, "DEC-E2E-XT-1", "Tenant A Decision", "Description",
                "Rationale", "STRATEGIC", ExecutiveDecision.Priority.NORMAL,
                "Impact", "Outcome",
                approverA, requesterA, null
        );
        var created = decisionService.create(decision, requesterA);
        decisionService.submit(tenantA, created.id(), requesterA);
        var workflowInstanceId = integrationService.startWorkflowForDecision(
                tenantA, created.id(), requesterA);

        // tenantB user attempts to approve tenantA's decision
        assertThatThrownBy(() ->
                integrationService.approveDecisionViaWorkflow(
                        tenantB, created.id(), userB, "cross-tenant approve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ===== IDEMPOTENCY: RE-APPROVE AFTER ALREADY APPROVED =====

    @Test
    void idempotentApprove_afterAlreadyApproved_returnsApprovedDecision() {
        buildDecisionApprovalWorkflow(tenantA, requesterA);

        var decision = ExecutiveDecision.create(
                tenantA, "DEC-E2E-IDEM-1", "Test Decision", "Description",
                "Rationale", "STRATEGIC", ExecutiveDecision.Priority.NORMAL,
                "Impact", "Outcome",
                approverA, requesterA, null
        );
        var created = decisionService.create(decision, requesterA);
        decisionService.submit(tenantA, created.id(), requesterA);
        var workflowInstanceId = integrationService.startWorkflowForDecision(
                tenantA, created.id(), requesterA);

        // First approve — succeeds
        var first = integrationService.approveDecisionViaWorkflow(
                tenantA, created.id(), approverA, "first");
        assertThat(first.status()).isEqualTo(ExecutiveDecision.Status.APPROVED);

        // Second approve attempt on the same decision — workflow approval is no longer PENDING
        assertThatThrownBy(() ->
                integrationService.approveDecisionViaWorkflow(
                        tenantA, created.id(), approverA, "second"))
                .isInstanceOf(IllegalStateException.class);

        // Verify only 1 APPROVE audit record exists in workflow_transition_audit
        var approveAuditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ? AND action = 'APPROVE'",
                Integer.class, workflowInstanceId);
        assertThat(approveAuditCount).isEqualTo(1);
    }

    // ===== FALLBACK: NO WORKFLOW DEFINITION → DIRECT APPROVAL =====

    @Test
    void fallback_noWorkflowDefinition_fallsBackToDirectApproval() {
        // Do NOT create a DECISION_APPROVAL workflow definition
        var decision = ExecutiveDecision.create(
                tenantA, "DEC-E2E-FB-1", "Test Decision", "Description",
                "Rationale", "STRATEGIC", ExecutiveDecision.Priority.NORMAL,
                "Impact", "Outcome",
                approverA, requesterA, null
        );
        var created = decisionService.create(decision, requesterA);
        decisionService.submit(tenantA, created.id(), requesterA);

        // startWorkflowForDecision returns null (no workflow definition)
        var result = integrationService.startWorkflowForDecision(
                tenantA, created.id(), requesterA);
        assertThat(result).isNull();

        // approveDecisionViaWorkflow falls back to direct approval (still SOD-enforced by ExecutiveDecision.approve)
        var approved = integrationService.approveDecisionViaWorkflow(
                tenantA, created.id(), approverA, "direct approve");
        assertThat(approved.status()).isEqualTo(ExecutiveDecision.Status.APPROVED);
    }
}
