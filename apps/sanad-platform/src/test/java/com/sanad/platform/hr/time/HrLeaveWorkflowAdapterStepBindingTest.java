package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrLeaveWorkflowAdapter;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowEntitlementGuard;
import com.sanad.platform.workflow.application.WorkflowExecutionService;
import com.sanad.platform.workflow.application.WorkflowGraphExecutionService;
import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstance;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStepInstance;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused regression unit tests for {@link HrLeaveWorkflowAdapter#findPendingApprovalForCurrentStep}.
 *
 * <p>These tests prove the historical defect — selecting any PENDING approval
 * across the whole workflow instance via {@code findFirst()} — cannot recur.
 * Each test pins a specific failure mode from directive §8:
 * <ol>
 *   <li>TEST 1: At {@code manager_approval}, with a historical/non-current PENDING-like
 *       fixture for another step + a valid current-step Manager approval → resolver
 *       returns ONLY the current Manager approval.</li>
 *   <li>TEST 2: At {@code hr_approval}, with an old Manager approval record + a valid
 *       HR approval → resolver returns ONLY the HR approval.</li>
 *   <li>TEST 3: At {@code hr_approval} but only Manager approval exists → resolver
 *       returns empty / fails closed.</li>
 *   <li>TEST 4: Approval exists with {@code workflowStepInstanceId = null} → must
 *       NOT be selected.</li>
 *   <li>TEST 5: Approval belongs to another WorkflowStepInstance → must NOT be selected.</li>
 *   <li>TEST 6: WorkflowInstance.currentStepKey mismatch → fail closed.</li>
 *   <li>TEST 7: Workflow is COMPLETED/CANCELLED → no actionable approval.</li>
 * </ol>
 *
 * <p>The tests use Mockito stubs for the canonical Workflow Engine repositories
 * so they exercise only the resolver logic — no Spring context or DB required,
 * keeping them hermetic and fast.
 */
class HrLeaveWorkflowAdapterStepBindingTest {

    private WorkflowDefinitionRepository definitionRepository;
    private WorkflowInstanceRepository instanceRepository;
    private WorkflowStepInstanceRepository stepInstanceRepository;
    private WorkflowApprovalRequestRepository approvalRepository;
    private WorkflowApprovalService approvalService;
    private WorkflowExecutionService executionService;
    private WorkflowGraphExecutionService graphExecutionService;
    private WorkflowEntitlementGuard entitlementGuard;

    private HrLeaveWorkflowAdapter adapter;

    private UUID tenantId;
    private UUID workflowInstanceId;
    private UUID managerStepInstanceId;
    private UUID hrStepInstanceId;
    private UUID otherWorkflowStepInstanceId; // for cross-step fixtures

    @BeforeEach
    void setUp() {
        definitionRepository = mock(WorkflowDefinitionRepository.class);
        instanceRepository = mock(WorkflowInstanceRepository.class);
        stepInstanceRepository = mock(WorkflowStepInstanceRepository.class);
        approvalRepository = mock(WorkflowApprovalRequestRepository.class);
        approvalService = mock(WorkflowApprovalService.class);
        executionService = mock(WorkflowExecutionService.class);
        graphExecutionService = mock(WorkflowGraphExecutionService.class);
        entitlementGuard = mock(WorkflowEntitlementGuard.class);

        adapter = new HrLeaveWorkflowAdapter(
                definitionRepository, instanceRepository, stepInstanceRepository, approvalRepository,
                approvalService, executionService, graphExecutionService, entitlementGuard);

        tenantId = UUID.randomUUID();
        workflowInstanceId = UUID.randomUUID();
        managerStepInstanceId = UUID.randomUUID();
        hrStepInstanceId = UUID.randomUUID();
        otherWorkflowStepInstanceId = UUID.randomUUID();
    }

    // ==================== Helpers ====================

    /**
     * Build a RUNNING instance via the canonical startY2 factory, then if needed
     * flip the currentStepKey (startY2 pins it to firstStepKey; for tests that
     * need a workflow "currently at" a non-start step we mutate it post-hoc
     * using the canonical advanceToStep).
     */
    private WorkflowInstance runningInstance(String currentStepKey) {
        UUID defFamilyId = UUID.randomUUID();
        UUID defVersionId = UUID.randomUUID();
        WorkflowInstance started = WorkflowInstance.startY2(
                tenantId, defFamilyId, defVersionId, 1,
                "LEAVE_REQUEST", UUID.randomUUID(),
                currentStepKey, UUID.randomUUID(), workflowInstanceId,
                "MANUAL", null, "test-key", null, null);
        // Override the id to the fixed test value (startY2 uses randomUUID).
        return new WorkflowInstance(
                workflowInstanceId, started.tenantId(), started.workflowDefinitionId(),
                started.workflowVersion(), started.businessEntityType(), started.businessEntityId(),
                WorkflowInstance.Status.RUNNING, currentStepKey, started.startedBy(), started.startedAt(),
                started.completedAt(), started.cancelledAt(), started.cancelledBy(), started.cancelReason(),
                started.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                started.definitionFamilyId(), started.definitionVersionId(), started.parentInstanceId(),
                started.triggerType(), started.triggerId(), started.idempotencyKey(), started.causationId(),
                started.contextJson(), started.contextSchemaVersion(), started.version(),
                started.createdAt(), started.updatedAt());
    }

    /**
     * Build a COMPLETED instance at the specified terminal step key. Real
     * domain {@code complete()} nulls out currentStepKey, but for the resolver
     * test we want a synthetic COMPLETED instance with a known terminal
     * currentStepKey — the resolver must fail closed regardless because
     * {@code status != RUNNING}.
     */
    private WorkflowInstance completedInstance(String terminalStepKey) {
        WorkflowInstance started = runningInstance(terminalStepKey);
        return new WorkflowInstance(
                started.id(), started.tenantId(), started.workflowDefinitionId(),
                started.workflowVersion(), started.businessEntityType(), started.businessEntityId(),
                WorkflowInstance.Status.COMPLETED, terminalStepKey, started.startedBy(), started.startedAt(),
                Instant.now(), started.cancelledAt(), started.cancelledBy(), started.cancelReason(),
                started.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                started.definitionFamilyId(), started.definitionVersionId(), started.parentInstanceId(),
                started.triggerType(), started.triggerId(), started.idempotencyKey(), started.causationId(),
                started.contextJson(), started.contextSchemaVersion(), started.version() + 1,
                started.createdAt(), Instant.now());
    }

    private WorkflowInstance cancelledInstance(String stepKeyAtCancellation) {
        WorkflowInstance started = runningInstance(stepKeyAtCancellation);
        return new WorkflowInstance(
                started.id(), started.tenantId(), started.workflowDefinitionId(),
                started.workflowVersion(), started.businessEntityType(), started.businessEntityId(),
                WorkflowInstance.Status.CANCELLED, stepKeyAtCancellation, started.startedBy(), started.startedAt(),
                started.completedAt(), Instant.now(), UUID.randomUUID(), "test cancel",
                started.correlationId(), WorkflowInstance.EngineGeneration.Y2,
                started.definitionFamilyId(), started.definitionVersionId(), started.parentInstanceId(),
                started.triggerType(), started.triggerId(), started.idempotencyKey(), started.causationId(),
                started.contextJson(), started.contextSchemaVersion(), started.version() + 1,
                started.createdAt(), Instant.now());
    }

    private WorkflowStepInstance stepInstance(UUID id, String stepKey, WorkflowStepInstance.Status status) {
        return new WorkflowStepInstance(
                id, tenantId, workflowInstanceId, UUID.randomUUID(), stepKey, status,
                UUID.randomUUID(), "MANAGER", Instant.now(), null, null, null, null, null,
                0, null, 0, Instant.now(), Instant.now());
    }

    private WorkflowApprovalRequest approval(UUID id, UUID stepInstanceId, WorkflowApprovalRequest.Status status) {
        return new WorkflowApprovalRequest(
                id, tenantId, workflowInstanceId, stepInstanceId,
                UUID.randomUUID(), null, UUID.randomUUID(), null,
                WorkflowApprovalPolicy.Aggregation.ANY_ONE, WorkflowApprovalPolicy.SelfApproval.DENY,
                "{}", status, Instant.now(), null, null, null, null, null,
                0, Instant.now(), Instant.now());
    }

    // ==================== TEST 1: Manager step — historical fixture for another step + valid Manager approval ====================

    @Test
    void test1_atManagerStep_returnsOnlyCurrentManagerApproval_whenHistoricalFixtureExistsForOtherStep() {
        // Workflow at manager_approval step
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        // Step instances: one PENDING for manager_approval (current), one COMPLETED for submit (historical)
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(UUID.randomUUID(), "submit", WorkflowStepInstance.Status.COMPLETED),
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));

        // Approvals: one for the manager step (valid), one PENDING-like fixture for "submit" (historical, different step instance)
        UUID validManagerApprovalId = UUID.randomUUID();
        UUID historicalSubmitFixtureId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(validManagerApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING),
                approval(historicalSubmitFixtureId, otherWorkflowStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must return the single Manager approval bound to current step instance").isPresent();
        assertThat(result.get().id()).isEqualTo(validManagerApprovalId);
        assertThat(result.get().workflowStepInstanceId()).isEqualTo(managerStepInstanceId);
    }

    // ==================== TEST 2: HR step — old Manager approval + valid HR approval ====================

    @Test
    void test2_atHrStep_returnsOnlyHrApproval_whenOldManagerApprovalExists() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("hr_approval")));

        // Step instances: completed manager_approval, PENDING hr_approval (current)
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED),
                stepInstance(hrStepInstanceId, "hr_approval", WorkflowStepInstance.Status.PENDING)
        ));

        // Approvals: old Manager approval (still PENDING by mistake? or APPROVED), valid HR approval
        // Realistic: old Manager approval is APPROVED, not PENDING. But the resolver must reject it regardless.
        UUID oldManagerApprovalId = UUID.randomUUID();
        UUID validHrApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(oldManagerApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.APPROVED),
                approval(validHrApprovalId, hrStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");

        assertThat(result).as("Must return the single HR approval bound to current step instance").isPresent();
        assertThat(result.get().id()).isEqualTo(validHrApprovalId);
        assertThat(result.get().workflowStepInstanceId()).isEqualTo(hrStepInstanceId);
    }

    // ==================== TEST 3: At hr_approval but only Manager approval exists → fail closed ====================

    @Test
    void test3_atHrStep_failsClosed_whenOnlyManagerApprovalExists() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("hr_approval")));

        // Step instances: completed manager_approval, no hr_approval step instance yet
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
                // No PENDING hr_approval step instance — fail closed at step D
        ));

        UUID oldManagerApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(oldManagerApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");

        assertThat(result).as("Must fail closed — no actionable step instance for hr_approval").isEmpty();
    }

    // ==================== TEST 4: Approval with workflowStepInstanceId = null → must NOT be selected ====================

    @Test
    void test4_mustNotSelectApproval_whenWorkflowStepInstanceIdIsNull() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));

        // One approval with null workflowStepInstanceId, one valid for the step instance
        UUID nullStepApprovalId = UUID.randomUUID();
        UUID validApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(nullStepApprovalId, null, WorkflowApprovalRequest.Status.PENDING),
                approval(validApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must select only the approval bound to the step instance").isPresent();
        assertThat(result.get().id()).isEqualTo(validApprovalId);
        assertThat(result.get().workflowStepInstanceId()).isEqualTo(managerStepInstanceId);
    }

    // ==================== TEST 5: Approval belongs to another WorkflowStepInstance → must NOT be selected ====================

    @Test
    void test5_mustNotSelectApproval_whenApprovalBelongsToAnotherStepInstance() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));

        // Approval bound to a DIFFERENT step instance (e.g. from a previous iteration)
        UUID foreignApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(foreignApprovalId, otherWorkflowStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must fail closed — no approval bound to the current step instance").isEmpty();
    }

    // ==================== TEST 6: WorkflowInstance.currentStepKey mismatch → fail closed ====================

    @Test
    void test6_failsClosed_whenCurrentStepKeyMismatch() {
        // Instance is at manager_approval but caller asks for hr_approval
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        // Even if a valid HR approval + step instance exist, the currentStepKey mismatch must reject
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(hrStepInstanceId, "hr_approval", WorkflowStepInstance.Status.PENDING)
        ));

        UUID validHrApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(validHrApprovalId, hrStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");

        assertThat(result).as("Must fail closed — currentStepKey mismatch").isEmpty();
    }

    // ==================== TEST 7: Workflow COMPLETED → no actionable approval ====================

    @Test
    void test7_noActionableApproval_whenWorkflowCompleted() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(completedInstance("end_approved")));

        // Even with a step instance + approval in DB, the COMPLETED instance must reject
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must return empty — COMPLETED workflow has no actionable approval").isEmpty();
    }

    @Test
    void test7b_noActionableApproval_whenWorkflowCancelled() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(cancelledInstance("manager_approval")));

        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must return empty — CANCELLED workflow has no actionable approval").isEmpty();
    }

    // ==================== Additional defensive tests ====================

    @Test
    void defensive_nullArgs_returnEmpty() {
        assertThat(adapter.findPendingApprovalForCurrentStep(null, workflowInstanceId, "manager_approval")).isEmpty();
        assertThat(adapter.findPendingApprovalForCurrentStep(tenantId, null, "manager_approval")).isEmpty();
        assertThat(adapter.findPendingApprovalForCurrentStep(tenantId, workflowInstanceId, null)).isEmpty();
    }

    @Test
    void defensive_instanceNotFound_returnsEmpty() {
        when(instanceRepository.findById(tenantId, workflowInstanceId)).thenReturn(Optional.empty());

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must fail closed when instance not found (tenant isolation)").isEmpty();
    }

    @Test
    void defensive_zeroCurrentStepInstances_returnsEmpty() {
        // Step instance exists but is COMPLETED — no actionable current step instance
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must fail closed — no PENDING/IN_PROGRESS step instance").isEmpty();
    }

    @Test
    void defensive_ambiguousMultipleCurrentStepInstances_returnsEmpty() {
        // Two PENDING step instances for the same key — defensive fail closed
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));

        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING),
                stepInstance(UUID.randomUUID(), "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("Must fail closed — ambiguous step instances").isEmpty();
    }

    @Test
    void getCurrentStepInstance_returnsTheCurrentStepInstance() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(UUID.randomUUID(), "submit", WorkflowStepInstance.Status.COMPLETED),
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));

        Optional<WorkflowStepInstance> result = adapter.getCurrentStepInstance(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).as("getCurrentStepInstance must return the current PENDING step instance").isPresent();
        assertThat(result.get().id()).isEqualTo(managerStepInstanceId);
        assertThat(result.get().stepKey()).isEqualTo("manager_approval");
    }
}
