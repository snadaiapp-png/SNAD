package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowDefinitionService;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowMonitoringService;
import com.sanad.platform.workflow.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotency tests for the Workflow Engine.
 *
 * <p>Proves that duplicate or retried operations result in deterministic final
 * state with no duplicate records or duplicate side effects.
 *
 * <p>Tests cover:
 * <ol>
 *   <li>Duplicate workflow definition creation — second call fails (unique constraint)</li>
 *   <li>Duplicate workflow instance start (same correlation_id is allowed because business entity ID differs;
 *       but starting the SAME instance twice is rejected by the service layer)</li>
 *   <li>Duplicate approval request creation — multiple approvals can coexist but the same approval ID cannot be
 *       persisted twice (PK constraint)</li>
 *   <li>Duplicate approval (approve-then-approve-again) — second throws IllegalStateException (status check)</li>
 *   <li>Duplicate rejection (reject-then-reject-again) — second throws IllegalStateException</li>
 *   <li>Duplicate transition (advance-then-advance-again-to-same-step) — second succeeds because the domain
 *       model has no idempotency key for transitions; verified via audit count</li>
 *   <li>Retry after transaction boundary — failed transaction leaves no side effects</li>
 *   <li>Repeated SLA breach detection — calling monitoring N times returns same count (idempotent reads)</li>
 *   <li>Repeated scheduler execution — idempotent</li>
 *   <li>Concurrent duplicate invocation — exactly one wins</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowIdempotencyTest {

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
                tenantId, "wf-idem-" + tenantId.toString().substring(0, 8), now, now);
        for (var uid : List.of(userId, approverId)) {
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                    + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                    uid, tenantId, "wf-idem-" + uid.toString().substring(0, 8) + "@test", now, now);
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

    /** Build a workflow definition with N steps and activate it. */
    private WorkflowDefinition buildActiveWorkflow(String code) {
        var def = WorkflowDefinition.create(
                tenantId, code, "Test Workflow " + code, "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId);
        var savedDef = defService.create(def, userId);
        defService.activate(tenantId, savedDef.id(), userId);
        return defService.findById(tenantId, savedDef.id()).orElseThrow();
    }

    // ===== 1. DUPLICATE DEFINITION CREATION =====

    @Test
    void duplicateDefinitionCreation_secondCallFails() {
        var def1 = WorkflowDefinition.create(
                tenantId, "DUP-DEF-1", "First", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId);
        defService.create(def1, userId);

        // Same code+version triggers UNIQUE(tenant_id, code, version) constraint
        var def2 = WorkflowDefinition.create(
                tenantId, "DUP-DEF-1", "Second", "Test",
                "GENERAL", WorkflowDefinition.TriggerType.MANUAL, userId);
        assertThatThrownBy(() -> defService.create(def2, userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify only 1 row exists
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE code = 'DUP-DEF-1'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ===== 2. DUPLICATE INSTANCE START =====

    @Test
    void duplicateInstanceStart_sameInstanceRejectedByService() {
        var def = buildActiveWorkflow("DUP-INST-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();

        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var saved = execService.startWorkflow(instance, userId);

        // Calling startWorkflow AGAIN with the SAME instance (same ID) — the save() should fail
        // because the instance ID already exists (PK constraint on workflow_instances.id).
        // To make this test deterministic, we re-create the instance with the SAME UUID.
        var duplicate = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        // Force the same UUID as the saved instance
        var withSameId = new WorkflowInstance(
                saved.id(), tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), WorkflowInstance.Status.RUNNING,
                firstStep.stepKey(), userId, saved.startedAt(), null, null, null, null, null,
                0, saved.createdAt(), saved.updatedAt());
        assertThatThrownBy(() -> execService.startWorkflow(withSameId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify only 1 instance exists with this ID
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE id = ?",
                Integer.class, saved.id());
        assertThat(count).isEqualTo(1);
    }

    // ===== 3. DUPLICATE APPROVAL CREATION =====

    @Test
    void duplicateApprovalCreation_sameIdRejected() {
        var def = buildActiveWorkflow("DUP-APP-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().plus(1, ChronoUnit.DAYS),
                userId);
        var savedApproval = approvalService.createApproval(approval, userId);

        // Try to save the SAME approval again (same UUID)
        var duplicate = new WorkflowApprovalRequest(
                savedApproval.id(), tenantId, savedInstance.id(), null,
                approverId, "APPROVER", userId,
                WorkflowApprovalRequest.Status.PENDING,
                savedApproval.requestedAt(), savedApproval.dueAt(),
                null, null, null, null,
                0, savedApproval.createdAt(), savedApproval.updatedAt());
        assertThatThrownBy(() -> approvalService.createApproval(duplicate, userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify only 1 approval row exists
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_approval_requests WHERE id = ?",
                Integer.class, savedApproval.id());
        assertThat(count).isEqualTo(1);
    }

    // ===== 4. DUPLICATE APPROVAL APPROVAL =====

    @Test
    void duplicateApproval_secondApproveThrows() {
        var def = buildActiveWorkflow("DUP-APP-APPROVE-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().plus(1, ChronoUnit.DAYS),
                userId);
        var savedApproval = approvalService.createApproval(approval, userId);

        // First approve — succeeds
        var first = approvalService.approve(tenantId, savedApproval.id(), approverId, "first");
        assertThat(first.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);

        // Second approve — fails (status is now APPROVED, not PENDING)
        assertThatThrownBy(() ->
                approvalService.approve(tenantId, savedApproval.id(), approverId, "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve from APPROVED");

        // Verify only 1 audit APPROVE record exists for this approval
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'APPROVE' AND workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditCount).isEqualTo(1);
    }

    // ===== 5. DUPLICATE APPROVAL REJECTION =====

    @Test
    void duplicateRejection_secondRejectThrows() {
        var def = buildActiveWorkflow("DUP-REJ-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().plus(1, ChronoUnit.DAYS),
                userId);
        var savedApproval = approvalService.createApproval(approval, userId);

        // First reject — succeeds
        var first = approvalService.reject(tenantId, savedApproval.id(), approverId, "first");
        assertThat(first.status()).isEqualTo(WorkflowApprovalRequest.Status.REJECTED);

        // Second reject — fails (status is now REJECTED, not PENDING)
        assertThatThrownBy(() ->
                approvalService.reject(tenantId, savedApproval.id(), approverId, "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve from REJECTED");

        // Verify only 1 REJECT audit record exists
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'REJECT' AND workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditCount).isEqualTo(1);
    }

    // ===== 6. DUPLICATE WORKFLOW TRANSITION =====

    @Test
    void duplicateTransition_advanceToSameStepTwice_createsTwoAuditRecords() {
        var def = buildActiveWorkflow("DUP-TRANS-1");
        var steps = defService.findSteps(def.id());
        assertThat(steps).hasSizeGreaterThanOrEqualTo(2);
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var secondStep = steps.stream()
                .filter(s -> !s.stepKey().equals(firstStep.stepKey()))
                .min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder))
                .orElseThrow();

        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        // First advance to step2 — succeeds
        var advanced1 = execService.advanceToNextStep(tenantId, savedInstance.id(), secondStep.stepKey(), userId);
        assertThat(advanced1.currentStepKey()).isEqualTo(secondStep.stepKey());

        // The domain allows advancing to the SAME step again (no idempotency key) — this is
        // a known design choice. The test verifies that the audit trail reflects both
        // transitions, and the instance state is consistent.
        var advanced2 = execService.advanceToNextStep(tenantId, savedInstance.id(), secondStep.stepKey(), userId);
        assertThat(advanced2.currentStepKey()).isEqualTo(secondStep.stepKey());

        // Two ADVANCE audit records should exist
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'ADVANCE' AND workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditCount).isEqualTo(2);

        // But the instance row count remains 1
        var instanceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE id = ?",
                Integer.class, savedInstance.id());
        assertThat(instanceCount).isEqualTo(1);
    }

    // ===== 7. RETRY AFTER TRANSACTION BOUNDARY =====

    @Test
    void retryAfterTransactionBoundary_failedTransactionHasNoSideEffects() {
        var def = buildActiveWorkflow("RETRY-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().plus(1, ChronoUnit.DAYS),
                userId);
        var savedApproval = approvalService.createApproval(approval, userId);

        // Attempt 1: try to approve with the requester (SOD violation) — this throws and rolls back
        assertThatThrownBy(() ->
                approvalService.approve(tenantId, savedApproval.id(), userId, "self approve"))
                .isInstanceOf(IllegalStateException.class);

        // Verify NO approval audit record was written (transaction was rolled back)
        var auditAfterFailure = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'APPROVE' AND workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditAfterFailure).isZero();

        // Verify the approval status is still PENDING (rollback worked)
        var approvalStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_approval_requests WHERE id = ?",
                String.class, savedApproval.id());
        assertThat(approvalStatus).isEqualTo("PENDING");

        // Attempt 2: retry with proper approver — succeeds
        var retried = approvalService.approve(tenantId, savedApproval.id(), approverId, "retry");
        assertThat(retried.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);

        // Exactly 1 APPROVE audit record exists
        var auditAfterSuccess = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE action = 'APPROVE' AND workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        assertThat(auditAfterSuccess).isEqualTo(1);
    }

    // ===== 8. REPEATED SLA BREACH DETECTION =====

    @Test
    void repeatedSlaBreachDetection_returnsSameCount() {
        var def = buildActiveWorkflow("SLA-IDEM-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        // Create a PENDING approval with PAST due_at
        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),  // past due
                userId);
        approvalService.createApproval(approval, userId);

        // First call — should detect 1 overdue approval
        int firstCall = monitoringService.checkOverdueApprovals(tenantId);
        assertThat(firstCall).isEqualTo(1);

        // Second call — should return the SAME count (idempotent reads)
        int secondCall = monitoringService.checkOverdueApprovals(tenantId);
        assertThat(secondCall).isEqualTo(1);

        // Third call — still the same
        int thirdCall = monitoringService.checkOverdueApprovals(tenantId);
        assertThat(thirdCall).isEqualTo(1);

        // Verify no side effects (no extra audit rows, no extra alerts)
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        // Only the START audit (no EXPIRE or FAIL because monitoring is read-only)
        // Plus the ASSIGN audit from createApproval.
        assertThat(auditCount).isLessThanOrEqualTo(2);
    }

    // ===== 9. REPEATED SCHEDULER EXECUTION =====

    @Test
    void repeatedSchedulerExecution_isIdempotent() {
        var def = buildActiveWorkflow("SCHED-IDEM-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().minus(1, ChronoUnit.HOURS),  // past due
                userId);
        approvalService.createApproval(approval, userId);

        // Simulate 5 scheduler ticks
        for (int i = 0; i < 5; i++) {
            int breaches = monitoringService.checkAllSlaBreaches(tenantId);
            assertThat(breaches).isEqualTo(1);  // always 1 (idempotent)
        }

        // Verify no duplicate alerts/audits were created
        var auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_audit WHERE workflow_instance_id = ?",
                Integer.class, savedInstance.id());
        // Should remain stable: 1 START + 1 ASSIGN = 2 (no EXPIRE/FAIL added by monitoring)
        assertThat(auditCount).isLessThanOrEqualTo(2);

        // Verify the approval status is still PENDING (monitoring does not mutate state)
        var approvalStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_approval_requests WHERE id = ?",
                String.class, approval.id());
        assertThat(approvalStatus).isEqualTo("PENDING");
    }

    // ===== 10. CONCURRENT DUPLICATE INVOCATION =====

    @Test
    void concurrentDuplicateInvocation_exactlyOneWins() throws Exception {
        var def = buildActiveWorkflow("CONC-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();

        // Use a fixed instance ID — both threads try to start the SAME instance
        UUID fixedInstanceId = UUID.randomUUID();
        var instance = new WorkflowInstance(
                fixedInstanceId, tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), WorkflowInstance.Status.RUNNING,
                firstStep.stepKey(), userId, Instant.now(), null, null, null, null, null,
                0, Instant.now(), Instant.now());

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    execService.startWorkflow(instance, userId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(done).as("all threads completed").isTrue();
        // Exactly 1 thread should have succeeded (the others hit PK violation)
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threads - 1);

        // Verify only 1 instance row exists with the fixed ID
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_instances WHERE id = ?",
                Integer.class, fixedInstanceId);
        assertThat(count).isEqualTo(1);
    }

    // ===== BONUS: APPROVE-THEN-REJECT IS A NO-OP =====

    @Test
    void approveThenReject_secondThrows() {
        var def = buildActiveWorkflow("APPROVE-REJECT-1");
        var steps = defService.findSteps(def.id());
        var firstStep = steps.stream().min(java.util.Comparator.comparingInt(WorkflowStep::sequenceOrder)).orElseThrow();
        var instance = WorkflowInstance.start(
                tenantId, def.id(), def.version(),
                "ENTITY", UUID.randomUUID(), firstStep.stepKey(), userId, null);
        var savedInstance = execService.startWorkflow(instance, userId);

        var approval = WorkflowApprovalRequest.create(
                tenantId, savedInstance.id(), null,
                approverId, "APPROVER",
                Instant.now().plus(1, ChronoUnit.DAYS),
                userId);
        var savedApproval = approvalService.createApproval(approval, userId);

        // Approve — succeeds
        approvalService.approve(tenantId, savedApproval.id(), approverId, "approved");

        // Reject (after approve) — fails (status is APPROVED, not PENDING)
        assertThatThrownBy(() ->
                approvalService.reject(tenantId, savedApproval.id(), approverId, "rejected"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve from APPROVED");

        // Verify final status is APPROVED (not REJECTED)
        var finalStatus = jdbc.queryForObject(
                "SELECT status FROM workflow_approval_requests WHERE id = ?",
                String.class, savedApproval.id());
        assertThat(finalStatus).isEqualTo("APPROVED");
    }
}
