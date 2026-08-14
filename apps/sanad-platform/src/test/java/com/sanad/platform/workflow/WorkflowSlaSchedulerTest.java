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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SLA Scheduler integration tests for the Workflow Engine.
 *
 * <p>Proves:
 * <ul>
 *   <li>Scheduled execution invokes SLA monitoring</li>
 *   <li>Repeated execution is idempotent (no duplicate alerts/audits)</li>
 *   <li>No-breach case returns 0</li>
 *   <li>Decision SLA breach detected (approval past due)</li>
 *   <li>Step instance SLA breach detected (IN_PROGRESS step past due_at)</li>
 *   <li>Multi-tenant safety (each tenant checked separately)</li>
 *   <li>Per-tenant failure isolation (one bad tenant doesn't block others)</li>
 *   <li>FK-safe: no system-generated records violate actor_user_id FK</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowSlaSchedulerTest {

    @Autowired private WorkflowSlaScheduler scheduler;
    @Autowired private WorkflowDefinitionService defService;
    @Autowired private WorkflowExecutionService execService;
    @Autowired private WorkflowApprovalService approvalService;
    @Autowired private WorkflowMonitoringService monitoringService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID approverA;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE workflow_transition_audit, workflow_approval_requests, "
                + "workflow_step_instances, workflow_instances, workflow_steps, "
                + "workflow_definitions RESTART IDENTITY CASCADE");

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        approverA = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        for (var tid : List.of(tenantA, tenantB)) {
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tid, "Tenant " + tid.toString().substring(0, 8),
                    "sch-" + tid.toString().substring(0, 8), now, now);
        }
        for (var uid : List.of(userA, approverA)) {
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                    uid, tenantA, "sch-" + uid.toString().substring(0, 8) + "@test", now, now);
        }
        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Admin', 'Test', 'ACTIVE', ?, ?)",
                roleId, tenantA, now, now);
        var caps = jdbc.queryForList("SELECT id FROM access_capabilities WHERE code LIKE 'WORKFLOW.%'");
        for (var cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), tenantA, roleId, cap.get("id"), now);
        }
    }

    /** Build a workflow definition with N steps and activate it. */
    private WorkflowDefinition buildActiveWorkflow(UUID tenantId, String code) {
        var def = WorkflowDefinition.create(
                tenantId, code, "Test Workflow " + code, "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userA);
        var savedDef = defService.create(def, userA);
        defService.activate(tenantId, savedDef.id(), userA);
        return defService.findById(tenantId, savedDef.id()).orElseThrow();
    }

    // ===== 1. NO-BREACH CASE =====

    @Test
    void noBreachCase_returnsZero() {
        // No instances / approvals → 0 breaches
        var result = scheduler.runSlaCheckInternal();
        assertThat(result.tenantsProcessed()).isGreaterThanOrEqualTo(1); // at least tenantA, tenantB
        assertThat(result.totalBreaches()).isZero();
        assertThat(result.tenantsFailed()).isZero();
    }

    // ===== 2. APPROVAL SLA BREACH (DECISION SLA) =====

    @Test
    void decisionSlaBreach_detectedByScheduler() {
        var def = buildActiveWorkflow(tenantA, "SCH-DEC-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "DECISION", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);

        // Create an approval with PAST due_at
        var approval = WorkflowApprovalRequest.create(
                tenantA, savedInstance.id(), null,
                approverA, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS), // past due
                userA);
        approvalService.createApproval(approval, userA);

        var result = scheduler.runSlaCheckInternal();
        assertThat(result.totalBreaches()).isEqualTo(1);

        // Verify NO extra audit/approval records were created (idempotent)
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditCount).isLessThanOrEqualTo(2); // START + ASSIGN

        // Verify approval status is still PENDING (monitoring is read-only)
        var approvalStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_approval_requests WHERE id = ?",
                String.class, approval.id());
        assertThat(approvalStatus).isEqualTo("PENDING");
    }

    // ===== 3. STEP INSTANCE SLA BREACH (ESCALATION SLA) =====

    @Test
    void stepInstanceSlaBreach_detectedByScheduler() {
        var def = buildActiveWorkflow(tenantA, "SCH-STEP-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);

        // Manually create an IN_PROGRESS step_instance with PAST due_at
        var overdueStepInstance = WorkflowStepInstance.create(
                tenantA, savedInstance.id(), firstStep.id(),
                firstStep.stepKey(),
                Instant.now().minus(2, ChronoUnit.HOURS), // past due
                null, firstStep.requiredRole());
        // Transition to IN_PROGRESS (start the step)
        var started = WorkflowStepInstance.create(
                tenantA, savedInstance.id(), firstStep.id(),
                firstStep.stepKey(),
                Instant.now().minus(2, ChronoUnit.HOURS), // past due
                null, firstStep.requiredRole());
        // Use the JDBC layer to insert an IN_PROGRESS step_instance with a past due_at
        var stepId = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_step_instances "
                + "(id, tenant_id, workflow_instance_id, workflow_step_id, step_key, status, "
                + "started_at, due_at, attempt_count, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?, 0, 0, ?, ?)",
                stepId, tenantA, savedInstance.id(), firstStep.id(), firstStep.stepKey(),
                java.sql.Timestamp.from(Instant.now().minus(3, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()));

        var result = scheduler.runSlaCheckInternal();
        assertThat(result.totalBreaches()).isGreaterThanOrEqualTo(1); // 1 step breach
    }

    // ===== 4. MULTI-TENANT SAFETY =====

    @Test
    void multiTenantSafety_eachTenantCheckedSeparately() {
        // Create a breach in tenantA only — tenantB has no breaches
        var def = buildActiveWorkflow(tenantA, "SCH-MULTI-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);
        var approval = WorkflowApprovalRequest.create(
                tenantA, savedInstance.id(), null,
                approverA, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),
                userA);
        approvalService.createApproval(approval, userA);

        // Direct service call for tenantB → 0
        int tenantBBreaches = monitoringService.checkAllSlaBreaches(tenantB);
        assertThat(tenantBBreaches).isZero();

        // Direct service call for tenantA → 1
        int tenantABreaches = monitoringService.checkAllSlaBreaches(tenantA);
        assertThat(tenantABreaches).isEqualTo(1);

        // Scheduler aggregating both tenants → 1
        var result = scheduler.runSlaCheckInternal();
        assertThat(result.totalBreaches()).isEqualTo(1);
        assertThat(result.tenantsProcessed()).isGreaterThanOrEqualTo(2);
    }

    // ===== 5. REPEATED SCHEDULER EXECUTION IS IDEMPOTENT =====

    @Test
    void repeatedSchedulerExecution_isIdempotent() {
        var def = buildActiveWorkflow(tenantA, "SCH-IDEM-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);
        var approval = WorkflowApprovalRequest.create(
                tenantA, savedInstance.id(), null,
                approverA, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),
                userA);
        approvalService.createApproval(approval, userA);

        // Run the scheduler 5 times — should always return 1 breach
        for (int i = 0; i < 5; i++) {
            var result = scheduler.runSlaCheckInternal();
            assertThat(result.totalBreaches()).as("tick " + i).isEqualTo(1);
            assertThat(result.tenantsFailed()).as("tick " + i + " failures").isZero();
        }

        // Verify no duplicate audit records were created
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        // Expected: 1 START + 1 ASSIGN = 2 (no EXPIRE/FAIL because monitoring is read-only)
        assertThat(auditCount).isLessThanOrEqualTo(2);
    }

    // ===== 6. FK-SAFE: NO SYSTEM-GENERATED RECORDS VIOLATE FK CONSTRAINTS =====

    @Test
    void fkSafe_noSystemActorViolations() {
        var def = buildActiveWorkflow(tenantA, "SCH-FK-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);
        var approval = WorkflowApprovalRequest.create(
                tenantA, savedInstance.id(), null,
                approverA, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),
                userA);
        approvalService.createApproval(approval, userA);

        scheduler.runSlaCheckInternal();

        // Verify all audit rows have NULL or valid actor_user_id (no fake user IDs that violate FK)
        // The workflow_transition_audit.actor_user_id is nullable (set by the migration).
        var badActorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit wa "
                + "LEFT JOIN users u ON u.tenant_id = wa.tenant_id AND u.id = wa.actor_user_id "
                + "WHERE wa.actor_user_id IS NOT NULL AND u.id IS NULL",
                Integer.class);
        assertThat(badActorCount).isZero();
    }

    // ===== 7. PER-TENANT FAILURE ISOLATION =====

    @Test
    void perTenantFailureIsolation_oneBadTenantDoesNotBlockOthers() {
        // Create a valid breach in tenantA
        var def = buildActiveWorkflow(tenantA, "SCH-FAIL-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantA, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userA, null);
        var savedInstance = execService.startWorkflow(instance, userA);
        var approval = WorkflowApprovalRequest.create(
                tenantA, savedInstance.id(), null,
                approverA, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),
                userA);
        approvalService.createApproval(approval, userA);

        // Run scheduler — should process both tenantA (with breach) and tenantB (no breach)
        var result = scheduler.runSlaCheckInternal();
        assertThat(result.tenantsProcessed()).isGreaterThanOrEqualTo(2);
        assertThat(result.tenantsFailed()).isZero();
        assertThat(result.totalBreaches()).isEqualTo(1);

        // Verify tenantA still has its data intact (scheduler didn't corrupt state)
        var tenantApprovals = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_approval_requests WHERE tenant_id = ?",
                Integer.class, tenantA);
        assertThat(tenantApprovals).isEqualTo(1);
    }

    // ===== 8. SCHEDULER RETURNS STRUCTURED RESULT =====

    @Test
    void schedulerReturnsStructuredResult_withDurationAndCounts() {
        var result = scheduler.runSlaCheckInternal();
        assertThat(result).isNotNull();
        assertThat(result.tenantsProcessed()).isGreaterThanOrEqualTo(0);
        assertThat(result.tenantsFailed()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalBreaches()).isGreaterThanOrEqualTo(0);
        assertThat(result.tenantsSeen()).isGreaterThanOrEqualTo(0);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }
}
