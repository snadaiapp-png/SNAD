package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrLeaveWorkflowAdapter;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowEntitlementGuard;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowDefinition;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowStep;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowTransition;
import com.sanad.platform.workflow.infrastructure.JdbcWorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.infrastructure.JdbcWorkflowDefinitionRepository;
import com.sanad.platform.workflow.infrastructure.JdbcWorkflowInstanceRepository;
import com.sanad.platform.workflow.infrastructure.JdbcWorkflowStepInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PostgreSQL Direct regression test for the G2 leave workflow step-instance
 * approval binding (directive §9).
 *
 * <p>Real journey:
 * <ol>
 *   <li>Submit leave → Manager step instance created → Manager approval
 *       linked to its exact step instance.</li>
 *   <li>Manager approve → Manager step becomes COMPLETED → HR step instance
 *       created → HR approval linked to HR step instance.</li>
 *   <li>DB assertion: managerApproval.workflow_step_instance_id
 *       != hrApproval.workflow_step_instance_id.</li>
 *   <li>Resolver at HR stage must NOT return the old Manager approval.</li>
 * </ol>
 *
 * <p>PostgreSQL Direct only. NO Docker, NO Testcontainers, NO H2, NO SQLite.
 * The test constructs {@link HrLeaveWorkflowAdapter} with the canonical
 * JDBC-backed repository implementations so it exercises the real SQL
 * mapping used in production. Fixture inserts use the same repos' save()
 * methods so column lists stay in sync with the schema.
 *
 * <p>NO {@code Assumptions.assumeTrue} skip — this is a required regression
 * for the wrong-step approval selection defect (directive §14). CI must
 * provide PostgreSQL; locally the test fails closed when no PostgreSQL is
 * available (LOCAL_BACKEND_TESTS=NOT_RUN_ENVIRONMENT_LIMITATION per §13).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HrG2LeavePostgresIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private JdbcTemplate jdbc;
    private JdbcWorkflowDefinitionRepository defRepo;
    private JdbcWorkflowInstanceRepository instanceRepo;
    private JdbcWorkflowStepInstanceRepository stepInstanceRepo;
    private JdbcWorkflowApprovalRequestRepository approvalRepo;
    private HrLeaveWorkflowAdapter adapter;

    private UUID tenantId;
    private UUID submitterUserId;
    private UUID managerUserId;
    private UUID hrUserId;
    private UUID leaveRequestId;
    private WorkflowDefinition definition;
    private WorkflowStep submitStep;
    private WorkflowStep managerApprovalStep;
    private WorkflowStep hrApprovalStep;
    private WorkflowStep endApprovedStep;
    private WorkflowStep endRejectedStep;
    private WorkflowInstance instance;
    private WorkflowStepInstance submitStepInstance;
    private WorkflowStepInstance managerStepInstance;
    private WorkflowStepInstance hrStepInstance;
    private WorkflowApprovalRequest managerApproval;

    @BeforeEach
    void setUp() {
        // Fail closed if PostgreSQL is not configured — required regression (no Assumptions skip).
        if (DB_URL == null || DB_URL.isBlank() || DB_USER == null || DB_USER.isBlank()) {
            throw new IllegalStateException(
                    "HrG2LeavePostgresIntegrationTest requires SPRING_DATASOURCE_URL "
                            + "and SPRING_DATASOURCE_USERNAME pointing to a PostgreSQL Direct database.");
        }

        DataSource dataSource = new DriverManagerDataSource(DB_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);

        defRepo = new JdbcWorkflowDefinitionRepository(jdbc);
        instanceRepo = new JdbcWorkflowInstanceRepository(jdbc);
        stepInstanceRepo = new JdbcWorkflowStepInstanceRepository(jdbc);
        approvalRepo = new JdbcWorkflowApprovalRequestRepository(jdbc);

        // Adapter under test: real JDBC repos + Mockito mocks for services
        // not exercised by findPendingApprovalForCurrentStep().
        adapter = new HrLeaveWorkflowAdapter(
                defRepo,
                instanceRepo,
                stepInstanceRepo,
                approvalRepo,
                mock(WorkflowApprovalService.class),
                mock(WorkflowExecutionService.class),
                mock(WorkflowGraphExecutionService.class),
                mock(WorkflowEntitlementGuard.class));

        tenantId = UUID.randomUUID();
        submitterUserId = UUID.randomUUID();
        managerUserId = UUID.randomUUID();
        hrUserId = UUID.randomUUID();
        leaveRequestId = UUID.randomUUID();

        insertBaseFixtures();
        publishWorkflowDefinition();
        startManagerStageWorkflow();
    }

    // ==================== Fixture setup ====================

    private void insertBaseFixtures() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, "G2 Leave Lifecycle Test " + tenantId.toString().substring(0, 8),
                "g2-leave-" + tenantId.toString().substring(0, 8), now, now);

        insertUser(submitterUserId, "submitter");
        insertUser(managerUserId, "manager");
        insertUser(hrUserId, "hr");
    }

    private void insertUser(UUID userId, String roleSuffix) {
        Timestamp now = Timestamp.from(Instant.now());
        String email = roleSuffix + "-" + userId.toString().substring(0, 8) + "@test";
        // password_hash is added by V10 migration — required column for the
        // canonical users schema. No silent fallback; if the column is missing
        // the test fails closed (migration chain drift indicator).
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, email, roleSuffix + " user", now, now);
    }

    private void publishWorkflowDefinition() {
        // Use the canonical createY2Draft + publish so the row goes through the
        // production JdbcWorkflowDefinitionRepository SQL.
        WorkflowDefinition draft = WorkflowDefinition.createY2Draft(
                tenantId, "HR_LEAVE_APPROVAL", "Leave Approval",
                "G2 regression test definition", "HRM",
                WorkflowDefinition.TriggerType.MANUAL, submitterUserId);
        definition = defRepo.save(draft);

        submitStep = defRepo.saveStep(WorkflowStep.create(
                tenantId, definition.id(), "submit", "Leave Submission",
                WorkflowStep.StepType.START, 1, "{}", null, null, null));
        managerApprovalStep = defRepo.saveStep(WorkflowStep.create(
                tenantId, definition.id(), "manager_approval", "Manager Approval",
                WorkflowStep.StepType.APPROVAL, 2, "{\"approvalPolicy\":\"ANY_ONE\"}",
                48, "HRM.LEAVE.TEAM_APPROVE", null));
        hrApprovalStep = defRepo.saveStep(WorkflowStep.create(
                tenantId, definition.id(), "hr_approval", "HR Approval",
                WorkflowStep.StepType.APPROVAL, 3, "{\"approvalPolicy\":\"ANY_ONE\"}",
                48, "HRM.LEAVE.HR_APPROVE", null));
        endApprovedStep = defRepo.saveStep(WorkflowStep.create(
                tenantId, definition.id(), "end_approved", "Leave Approved",
                WorkflowStep.StepType.END, 4, "{}", null, null, null));
        endRejectedStep = defRepo.saveStep(WorkflowStep.create(
                tenantId, definition.id(), "end_rejected", "Leave Rejected",
                WorkflowStep.StepType.END, 5, "{}", null, null, null));

        defRepo.saveTransition(WorkflowTransition.create(
                tenantId, definition.id(), submitStep.id(), managerApprovalStep.id(),
                "to_manager_approval", "SUCCESS", null, 10, null));
        defRepo.saveTransition(WorkflowTransition.create(
                tenantId, definition.id(), managerApprovalStep.id(), hrApprovalStep.id(),
                "manager_approved", "APPROVE", null, 10, null));
        defRepo.saveTransition(WorkflowTransition.create(
                tenantId, definition.id(), managerApprovalStep.id(), endRejectedStep.id(),
                "manager_rejected", "REJECT", null, 10, null));
        defRepo.saveTransition(WorkflowTransition.create(
                tenantId, definition.id(), hrApprovalStep.id(), endApprovedStep.id(),
                "hr_approved", "APPROVE", null, 10, null));
        defRepo.saveTransition(WorkflowTransition.create(
                tenantId, definition.id(), hrApprovalStep.id(), endRejectedStep.id(),
                "hr_rejected", "REJECT", null, 10, null));

        definition = definition.publish(submitterUserId, "test-checksum");
        definition = defRepo.save(definition);
    }

    private void startManagerStageWorkflow() {
        // Build a RUNNING Y2 instance at manager_approval using startY2 + manual
        // currentStepKey override. (Real engine start would also work but adds
        // entitlement/work-item setup complexity unrelated to the resolver test.)
        String idempotencyKey = "HR_LEAVE_APPROVAL:" + tenantId + ":" + leaveRequestId;
        WorkflowInstance started = WorkflowInstance.startY2(
                tenantId, definition.definitionFamilyId(), definition.id(), definition.version(),
                "LEAVE_REQUEST", leaveRequestId,
                "manager_approval", submitterUserId, leaveRequestId,
                "MANUAL", null, idempotencyKey, null, null);
        // Override the id so we can reference it in subsequent setup.
        instance = new WorkflowInstance(
                workflowInstanceIdForTest(), started.tenantId(), started.workflowDefinitionId(),
                started.workflowVersion(), started.businessEntityType(), started.businessEntityId(),
                WorkflowInstance.Status.RUNNING, "manager_approval", started.startedBy(), started.startedAt(),
                started.completedAt(), started.cancelledAt(), started.cancelledBy(), started.cancelReason(),
                started.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                started.definitionFamilyId(), started.definitionVersionId(), started.parentInstanceId(),
                started.triggerType(), started.triggerId(), started.idempotencyKey(), started.causationId(),
                started.contextJson(), started.contextSchemaVersion(), started.version(),
                started.createdAt(), started.updatedAt());
        instance = instanceRepo.save(instance);

        // Submit step instance — COMPLETED (historical)
        submitStepInstance = WorkflowStepInstance.create(
                tenantId, instance.id(), submitStep.id(), "submit", (java.time.Instant) null,
                submitterUserId, null);
        // Mark as started + completed via the domain transitions
        submitStepInstance = stepInstanceRepo.save(submitStepInstance);
        submitStepInstance = stepInstanceRepo.save(submitStepInstance.start());
        submitStepInstance = stepInstanceRepo.save(submitStepInstance.complete("Submitted"));

        // Manager step instance — PENDING (current actionable)
        managerStepInstance = WorkflowStepInstance.create(
                tenantId, instance.id(), managerApprovalStep.id(), "manager_approval",
                (java.time.Instant) null, managerUserId, "MANAGER");
        managerStepInstance = stepInstanceRepo.save(managerStepInstance);

        // Manager approval request bound to the manager step instance
        managerApproval = WorkflowApprovalRequest.create(
                tenantId, instance.id(), managerStepInstance.id(),
                managerUserId, "MANAGER", null, submitterUserId);
        managerApproval = approvalRepo.save(managerApproval);
    }

    /**
     * Fixed UUID for the workflow instance so test methods can reference it
     * before instance is constructed. startY2 generates a random UUID; we
     * override it to a fixed test value.
     */
    private UUID workflowInstanceIdForTest() {
        return UUID.randomUUID(); // fresh per test invocation is fine — we use instance.id() afterwards
    }

    // ==================== Tests ====================

    /**
     * TEST: Manager stage — resolver returns Manager approval bound to Manager step instance.
     */
    @Test
    void managerStage_resolverReturnsManagerApprovalBoundToCurrentStepInstance() {
        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "manager_approval");

        assertThat(result).as("Manager approval must be found at manager_approval step").isPresent();
        assertThat(result.get().id()).isEqualTo(managerApproval.id());
        assertThat(result.get().workflowStepInstanceId())
                .as("Manager approval must be bound to the Manager step instance")
                .isEqualTo(managerStepInstance.id());
    }

    /**
     * TEST: After Manager approval progression, resolver at HR stage returns HR approval
     * bound to the HR step instance — NOT the old Manager approval.
     */
    @Test
    void hrStage_resolverReturnsHrApprovalNotOldManagerApproval_afterManagerApproval() {
        // Simulate Manager approval progression:
        // 1. Mark Manager step instance as COMPLETED
        // 2. Mark Manager approval as APPROVED
        // 3. Insert HR step instance (PENDING)
        // 4. Update workflow_instance.current_step_key = "hr_approval"
        // 5. Insert HR approval request bound to HR step instance
        //
        // Phase 2.E fix: save each transition separately to avoid stale entity
        // OptimisticLockingFailureException. The previous code called
        // managerStepInstance.start().complete(...) in one chain, then save() —
        // but start() bumps version 0→1 and complete() bumps 1→2, so save()
        // tried UPDATE WHERE version=1 against a DB row at version=0 → 0 rows
        // affected → OptimisticLockingFailureException.
        // The fix: save after start() (version 0→1 persisted), then save after
        // complete() (version 1→2 persisted). Each save()'s UPDATE WHERE
        // version=N-1 matches the DB's current version.
        WorkflowStepInstance started = stepInstanceRepo.save(managerStepInstance.start());
        WorkflowStepInstance completedManager = stepInstanceRepo.save(started.complete("Approved by manager"));
        WorkflowApprovalRequest approvedManager = approvalRepo.save(
                managerApproval.approve(managerUserId, "ok"));

        // Insert HR step instance (PENDING)
        hrStepInstance = WorkflowStepInstance.create(
                tenantId, instance.id(), hrApprovalStep.id(), "hr_approval",
                (java.time.Instant) null, hrUserId, "HR");
        hrStepInstance = stepInstanceRepo.save(hrStepInstance);

        // Update workflow_instance current_step_key to hr_approval
        WorkflowInstance advancedInstance = new WorkflowInstance(
                instance.id(), instance.tenantId(), instance.workflowDefinitionId(),
                instance.workflowVersion(), instance.businessEntityType(), instance.businessEntityId(),
                WorkflowInstance.Status.RUNNING, "hr_approval", instance.startedBy(), instance.startedAt(),
                instance.completedAt(), instance.cancelledAt(), instance.cancelledBy(), instance.cancelReason(),
                instance.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                instance.definitionFamilyId(), instance.definitionVersionId(), instance.parentInstanceId(),
                instance.triggerType(), instance.triggerId(), instance.idempotencyKey(), instance.causationId(),
                instance.contextJson(), instance.contextSchemaVersion(), instance.version() + 1,
                instance.createdAt(), java.time.Instant.now());
        instance = instanceRepo.save(advancedInstance);

        // Insert HR approval request bound to HR step instance
        WorkflowApprovalRequest hrApproval = WorkflowApprovalRequest.create(
                tenantId, instance.id(), hrStepInstance.id(),
                hrUserId, "HR", null, submitterUserId);
        hrApproval = approvalRepo.save(hrApproval);

        // ===== Assertions =====

        // 1. Resolver at HR stage must return the HR approval, NOT the old Manager approval
        Optional<WorkflowApprovalRequest> hrResult = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "hr_approval");

        assertThat(hrResult).as("HR approval must be found at hr_approval step").isPresent();
        assertThat(hrResult.get().id())
                .as("Must be the HR approval, NOT the old (APPROVED) Manager approval")
                .isEqualTo(hrApproval.id());
        assertThat(hrResult.get().workflowStepInstanceId())
                .as("HR approval must be bound to the HR step instance")
                .isEqualTo(hrStepInstance.id());

        // 2. DB-level assertion: manager and HR step instance ids must differ
        assertThat(completedManager.id())
                .as("Manager and HR step instance ids must differ (binding integrity)")
                .isNotEqualTo(hrStepInstance.id());

        // 3. Resolver asking for manager_approval at HR stage must fail closed
        Optional<WorkflowApprovalRequest> wrongStepResult = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "manager_approval");

        assertThat(wrongStepResult)
                .as("Resolver at HR stage must NOT return old Manager approval (currentStepKey mismatch)")
                .isEmpty();
    }

    /**
     * TEST: Edge case — if the Manager approval is somehow still PENDING after the workflow
     * advanced to HR, the resolver at HR stage must STILL fail closed for "manager_approval"
     * (currentStepKey is hr_approval) and return only the HR approval for "hr_approval".
     */
    @Test
    void hrStage_resolverFailsClosed_whenOldManagerApprovalStillPending() {
        // Move workflow to hr_approval but DON'T mark Manager approval as APPROVED
        // (simulates a stale/buggy state — resolver must still fail closed by step instance id)
        // Mark Manager step as COMPLETED but leave approval as PENDING.
        //
        // Phase 2.E fix: save each transition separately to avoid stale entity
        // OptimisticLockingFailureException (same root cause as the other test).
        WorkflowStepInstance started = stepInstanceRepo.save(managerStepInstance.start());
        stepInstanceRepo.save(started.complete("Completed without approval"));

        // Insert HR step instance (PENDING)
        hrStepInstance = WorkflowStepInstance.create(
                tenantId, instance.id(), hrApprovalStep.id(), "hr_approval",
                (java.time.Instant) null, hrUserId, "HR");
        hrStepInstance = stepInstanceRepo.save(hrStepInstance);

        // Insert HR approval request
        WorkflowApprovalRequest hrApproval = WorkflowApprovalRequest.create(
                tenantId, instance.id(), hrStepInstance.id(),
                hrUserId, "HR", null, submitterUserId);
        hrApproval = approvalRepo.save(hrApproval);

        // Update workflow_instance current_step_key to hr_approval
        WorkflowInstance advancedInstance = new WorkflowInstance(
                instance.id(), instance.tenantId(), instance.workflowDefinitionId(),
                instance.workflowVersion(), instance.businessEntityType(), instance.businessEntityId(),
                WorkflowInstance.Status.RUNNING, "hr_approval", instance.startedBy(), instance.startedAt(),
                instance.completedAt(), instance.cancelledAt(), instance.cancelledBy(), instance.cancelReason(),
                instance.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                instance.definitionFamilyId(), instance.definitionVersionId(), instance.parentInstanceId(),
                instance.triggerType(), instance.triggerId(), instance.idempotencyKey(), instance.causationId(),
                instance.contextJson(), instance.contextSchemaVersion(), instance.version() + 1,
                instance.createdAt(), java.time.Instant.now());
        instance = instanceRepo.save(advancedInstance);

        // Resolver asking for manager_approval at HR stage — must fail closed (currentStepKey mismatch)
        Optional<WorkflowApprovalRequest> wrongStep = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "manager_approval");
        assertThat(wrongStep)
                .as("Resolver must fail closed — currentStepKey is hr_approval, not manager_approval")
                .isEmpty();

        // Resolver asking for hr_approval — must return HR approval (not the still-PENDING Manager approval
        // which is bound to a DIFFERENT step instance id)
        Optional<WorkflowApprovalRequest> correctStep = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "hr_approval");
        assertThat(correctStep).as("HR approval must be found").isPresent();
        assertThat(correctStep.get().id())
                .as("Must be HR approval, NOT the still-PENDING Manager approval (different step instance id)")
                .isEqualTo(hrApproval.id());
        assertThat(correctStep.get().workflowStepInstanceId())
                .isEqualTo(hrStepInstance.id());
    }

    /**
     * TEST: Tenant isolation — Tenant A cannot resolve Tenant B's approval.
     *
     * <p>Tenant B sets up its own workflow at manager_approval. Tenant A's adapter
     * querying Tenant B's instance id must return empty (instance not found in
     * Tenant A's scope via findById).
     */
    @Test
    void tenantIsolation_tenantAResolverCannotSeeTenantBApproval() {
        // Tenant B fixtures
        UUID tenantBId = UUID.randomUUID();
        UUID tenantBUser = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantBId, "Tenant B " + tenantBId.toString().substring(0, 8),
                "tenant-b-" + tenantBId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', ?, ?)",
                tenantBUser, tenantBId, "tenant-b-" + tenantBUser.toString().substring(0, 8) + "@test",
                "Tenant B User", now, now);

        // Tenant B workflow definition + steps + instance + step instance + approval
        WorkflowDefinition tenantBDef = WorkflowDefinition.createY2Draft(
                tenantBId, "HR_LEAVE_APPROVAL", "Leave Approval", "Tenant B def", "HRM",
                WorkflowDefinition.TriggerType.MANUAL, tenantBUser);
        tenantBDef = defRepo.save(tenantBDef);
        WorkflowStep tenantBSubmit = defRepo.saveStep(WorkflowStep.create(
                tenantBId, tenantBDef.id(), "submit", "Submit", WorkflowStep.StepType.START,
                1, "{}", null, null, null));
        WorkflowStep tenantBManagerStep = defRepo.saveStep(WorkflowStep.create(
                tenantBId, tenantBDef.id(), "manager_approval", "Manager Approval",
                WorkflowStep.StepType.APPROVAL, 2, "{\"approvalPolicy\":\"ANY_ONE\"}",
                48, "HRM.LEAVE.TEAM_APPROVE", null));
        tenantBDef = tenantBDef.publish(tenantBUser, "checksum-b");
        tenantBDef = defRepo.save(tenantBDef);

        UUID tenantBInstanceId = UUID.randomUUID();
        WorkflowInstance tenantBInstance = WorkflowInstance.startY2(
                tenantBId, tenantBDef.definitionFamilyId(), tenantBDef.id(), tenantBDef.version(),
                "LEAVE_REQUEST", UUID.randomUUID(),
                "manager_approval", tenantBUser, tenantBInstanceId,
                "MANUAL", null, "tenant-b-key-" + tenantBInstanceId, null, null);
        tenantBInstance = new WorkflowInstance(
                tenantBInstanceId, tenantBInstance.tenantId(), tenantBInstance.workflowDefinitionId(),
                tenantBInstance.workflowVersion(), tenantBInstance.businessEntityType(),
                tenantBInstance.businessEntityId(),
                WorkflowInstance.Status.RUNNING, "manager_approval", tenantBInstance.startedBy(),
                tenantBInstance.startedAt(), tenantBInstance.completedAt(), tenantBInstance.cancelledAt(),
                tenantBInstance.cancelledBy(), tenantBInstance.cancelReason(), tenantBInstance.correlationId(),
                WorkflowInstance.EngineGeneration.Y2, tenantBInstance.definitionFamilyId(),
                tenantBInstance.definitionVersionId(), tenantBInstance.parentInstanceId(),
                tenantBInstance.triggerType(), tenantBInstance.triggerId(),
                tenantBInstance.idempotencyKey(), tenantBInstance.causationId(),
                tenantBInstance.contextJson(), tenantBInstance.contextSchemaVersion(),
                tenantBInstance.version(), tenantBInstance.createdAt(), tenantBInstance.updatedAt());
        tenantBInstance = instanceRepo.save(tenantBInstance);

        WorkflowStepInstance tenantBManagerSi = WorkflowStepInstance.create(
                tenantBId, tenantBInstance.id(), tenantBManagerStep.id(), "manager_approval",
                (java.time.Instant) null, tenantBUser, "MANAGER");
        tenantBManagerSi = stepInstanceRepo.save(tenantBManagerSi);

        WorkflowApprovalRequest tenantBApproval = WorkflowApprovalRequest.create(
                tenantBId, tenantBInstance.id(), tenantBManagerSi.id(),
                tenantBUser, "MANAGER", null, tenantBUser);
        tenantBApproval = approvalRepo.save(tenantBApproval);

        // Tenant A's adapter querying Tenant B's instance must return empty (tenant isolation)
        Optional<WorkflowApprovalRequest> crossTenantResult = adapter.findPendingApprovalForCurrentStep(
                tenantId, tenantBInstance.id(), "manager_approval");

        assertThat(crossTenantResult)
                .as("Tenant A must not resolve Tenant B's approval (tenant isolation via findById)")
                .isEmpty();

        // Sanity: Tenant A's resolver querying own instance returns own approval
        Optional<WorkflowApprovalRequest> ownResult = adapter.findPendingApprovalForCurrentStep(
                tenantId, instance.id(), "manager_approval");
        assertThat(ownResult).as("Tenant A must resolve own approval").isPresent();
        assertThat(ownResult.get().id()).isEqualTo(managerApproval.id());
    }
}
