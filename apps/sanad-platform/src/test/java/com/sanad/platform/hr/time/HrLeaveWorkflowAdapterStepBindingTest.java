package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrLeaveWorkflowAdapter;
import com.sanad.platform.workflow.application.WorkflowApprovalCommandPort;
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
 */
class HrLeaveWorkflowAdapterStepBindingTest {

    private WorkflowDefinitionRepository definitionRepository;
    private WorkflowInstanceRepository instanceRepository;
    private WorkflowStepInstanceRepository stepInstanceRepository;
    private WorkflowApprovalRequestRepository approvalRepository;
    private WorkflowApprovalCommandPort approvalCommands;
    private WorkflowExecutionService executionService;
    private WorkflowGraphExecutionService graphExecutionService;
    private WorkflowEntitlementGuard entitlementGuard;

    private HrLeaveWorkflowAdapter adapter;

    private UUID tenantId;
    private UUID workflowInstanceId;
    private UUID managerStepInstanceId;
    private UUID hrStepInstanceId;
    private UUID otherWorkflowStepInstanceId;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(WorkflowDefinitionRepository.class);
        instanceRepository = mock(WorkflowInstanceRepository.class);
        stepInstanceRepository = mock(WorkflowStepInstanceRepository.class);
        approvalRepository = mock(WorkflowApprovalRequestRepository.class);
        approvalCommands = mock(WorkflowApprovalCommandPort.class);
        executionService = mock(WorkflowExecutionService.class);
        graphExecutionService = mock(WorkflowGraphExecutionService.class);
        entitlementGuard = mock(WorkflowEntitlementGuard.class);

        adapter = new HrLeaveWorkflowAdapter(
                definitionRepository, instanceRepository, stepInstanceRepository, approvalRepository,
                approvalCommands, executionService, graphExecutionService, entitlementGuard);

        tenantId = UUID.randomUUID();
        workflowInstanceId = UUID.randomUUID();
        managerStepInstanceId = UUID.randomUUID();
        hrStepInstanceId = UUID.randomUUID();
        otherWorkflowStepInstanceId = UUID.randomUUID();
    }

    private WorkflowInstance runningInstance(String currentStepKey) {
        UUID defFamilyId = UUID.randomUUID();
        UUID defVersionId = UUID.randomUUID();
        WorkflowInstance started = WorkflowInstance.startY2(
                tenantId, defFamilyId, defVersionId, 1,
                "LEAVE_REQUEST", UUID.randomUUID(),
                currentStepKey, UUID.randomUUID(), workflowInstanceId,
                "MANUAL", null, "test-key", null, null);
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

    @Test
    void test1_atManagerStep_returnsOnlyCurrentManagerApproval_whenHistoricalFixtureExistsForOtherStep() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(UUID.randomUUID(), "submit", WorkflowStepInstance.Status.COMPLETED),
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));
        UUID validManagerApprovalId = UUID.randomUUID();
        UUID historicalSubmitFixtureId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(validManagerApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING),
                approval(historicalSubmitFixtureId, otherWorkflowStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(validManagerApprovalId);
        assertThat(result.get().workflowStepInstanceId()).isEqualTo(managerStepInstanceId);
    }

    @Test
    void test2_atHrStep_returnsOnlyHrApproval_whenOldManagerApprovalExists() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("hr_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED),
                stepInstance(hrStepInstanceId, "hr_approval", WorkflowStepInstance.Status.PENDING)
        ));
        UUID oldManagerApprovalId = UUID.randomUUID();
        UUID validHrApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(oldManagerApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.APPROVED),
                approval(validHrApprovalId, hrStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(validHrApprovalId);
        assertThat(result.get().workflowStepInstanceId()).isEqualTo(hrStepInstanceId);
    }

    @Test
    void test3_atHrStep_failsClosed_whenOnlyManagerApprovalExists() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("hr_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval")).isEmpty();
    }

    @Test
    void test4_mustNotSelectApproval_whenWorkflowStepInstanceIdIsNull() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));
        UUID validApprovalId = UUID.randomUUID();
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), null, WorkflowApprovalRequest.Status.PENDING),
                approval(validApprovalId, managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        Optional<WorkflowApprovalRequest> result = adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval");
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(validApprovalId);
    }

    @Test
    void test5_mustNotSelectApproval_whenApprovalBelongsToAnotherStepInstance() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), otherWorkflowStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
    }

    @Test
    void test6_failsClosed_whenCurrentStepKeyMismatch() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(hrStepInstanceId, "hr_approval", WorkflowStepInstance.Status.PENDING)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), hrStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "hr_approval")).isEmpty();
    }

    @Test
    void test7_noActionableApproval_whenWorkflowCompleted() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(completedInstance("end_approved")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
        ));
        when(approvalRepository.findByInstance(tenantId, workflowInstanceId)).thenReturn(List.of(
                approval(UUID.randomUUID(), managerStepInstanceId, WorkflowApprovalRequest.Status.PENDING)
        ));

        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
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

        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
    }

    @Test
    void defensive_nullArgs_returnEmpty() {
        assertThat(adapter.findPendingApprovalForCurrentStep(null, workflowInstanceId, "manager_approval")).isEmpty();
        assertThat(adapter.findPendingApprovalForCurrentStep(tenantId, null, "manager_approval")).isEmpty();
        assertThat(adapter.findPendingApprovalForCurrentStep(tenantId, workflowInstanceId, null)).isEmpty();
    }

    @Test
    void defensive_instanceNotFound_returnsEmpty() {
        when(instanceRepository.findById(tenantId, workflowInstanceId)).thenReturn(Optional.empty());
        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
    }

    @Test
    void defensive_zeroCurrentStepInstances_returnsEmpty() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.COMPLETED)
        ));
        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
    }

    @Test
    void defensive_ambiguousMultipleCurrentStepInstances_returnsEmpty() {
        when(instanceRepository.findById(tenantId, workflowInstanceId))
                .thenReturn(Optional.of(runningInstance("manager_approval")));
        when(stepInstanceRepository.findByInstance(workflowInstanceId)).thenReturn(List.of(
                stepInstance(managerStepInstanceId, "manager_approval", WorkflowStepInstance.Status.PENDING),
                stepInstance(UUID.randomUUID(), "manager_approval", WorkflowStepInstance.Status.PENDING)
        ));
        assertThat(adapter.findPendingApprovalForCurrentStep(
                tenantId, workflowInstanceId, "manager_approval")).isEmpty();
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
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(managerStepInstanceId);
        assertThat(result.get().stepKey()).isEqualTo("manager_approval");
    }
}
