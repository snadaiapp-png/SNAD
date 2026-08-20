package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Reference-integrity unit tests for {@link WorkflowApprovalService#createApproval}.
 *
 * <p>These tests prove the service-layer guards against foreign-UUID injection
 * by validating every cross-reference an API caller can supply:
 * <ul>
 *   <li>Tenant A instance + Tenant B step instance → controlled 400</li>
 *   <li>Tenant A instance + step instance from another A workflow → controlled 400</li>
 *   <li>Tenant A approval assigned to Tenant B user (zero-UUID sentinel) → controlled 400</li>
 *   <li>Invalid / nonexistent step instance → controlled 400</li>
 *   <li>Step belongs to a different definition than the instance → controlled 400</li>
 *   <li>Blank requestedFromRole → controlled 400</li>
 *   <li>Happy path with all references valid → approval persisted</li>
 * </ul>
 *
 * <p>The tests mock the repositories so they exercise only the
 * {@code validateReferences} logic — no Spring context or DB required,
 * keeping them hermetic and fast.
 */
class WorkflowApprovalReferenceIntegrityTest {

    private WorkflowApprovalRequestRepository approvalRepo;
    private WorkflowInstanceRepository instanceRepo;
    private WorkflowStepInstanceRepository stepInstanceRepo;
    private WorkflowDefinitionRepository defRepo;
    private WorkflowTransitionAuditRepository auditRepo;
    private WorkflowApprovalService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID instanceA1;
    private UUID instanceA2;
    private UUID stepInstanceA1Valid;
    private UUID stepInstanceA2OtherWorkflow;
    private UUID stepInstanceBForeign;
    private UUID defA1;
    private UUID defA2;
    private UUID stepIdA1;
    private UUID stepIdA2;

    @BeforeEach
    void setUp() {
        approvalRepo = mock(WorkflowApprovalRequestRepository.class);
        instanceRepo = mock(WorkflowInstanceRepository.class);
        stepInstanceRepo = mock(WorkflowStepInstanceRepository.class);
        defRepo = mock(WorkflowDefinitionRepository.class);
        auditRepo = mock(WorkflowTransitionAuditRepository.class);
        service = new WorkflowApprovalService(approvalRepo, instanceRepo,
                stepInstanceRepo, defRepo, auditRepo);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        defA1 = UUID.randomUUID();
        defA2 = UUID.randomUUID();
        instanceA1 = UUID.randomUUID();
        instanceA2 = UUID.randomUUID();
        stepInstanceA1Valid = UUID.randomUUID();
        stepInstanceA2OtherWorkflow = UUID.randomUUID();
        stepInstanceBForeign = UUID.randomUUID();
        stepIdA1 = UUID.randomUUID();
        stepIdA2 = UUID.randomUUID();

        // Default: audit save returns the same audit (no-op)
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ===== Negative tests =====

    @Test
    void createApproval_tenantAInstance_tenantBStepInstance_rejected400() {
        // Instance A1 belongs to tenantA
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        // Step instance lookup scoped by tenantA returns EMPTY (the step
        // instance belongs to tenantB). This is what findById returns when
        // the step instance doesn't belong to the queried tenant.
        when(stepInstanceRepo.findById(tenantA, stepInstanceBForeign))
                .thenReturn(Optional.empty());

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, stepInstanceBForeign,
                null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void createApproval_stepInstanceBelongsToDifferentInstance_rejected400() {
        // Both instance and step instance belong to tenantA, but the step
        // instance is for a DIFFERENT workflow instance within the same tenant.
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        when(stepInstanceRepo.findById(tenantA, stepInstanceA2OtherWorkflow))
                .thenReturn(Optional.of(mockStepInstance(tenantA, instanceA2, stepIdA2, stepInstanceA2OtherWorkflow)));

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, stepInstanceA2OtherWorkflow,
                null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    // Must mention the cross-instance mismatch (but never leak internal UUIDs)
                    assertThat(rse.getMessage()).contains("Step instance does not belong to this workflow instance");
                });
    }

    @Test
    void createApproval_stepBelongsToDifferentWorkflowDefinition_rejected400() {
        // Instance A1 belongs to definition A1
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        // Step instance belongs to A1 instance but references a step from defA2
        when(stepInstanceRepo.findById(tenantA, stepInstanceA1Valid))
                .thenReturn(Optional.of(mockStepInstance(tenantA, instanceA1, stepIdA2, stepInstanceA1Valid)));
        // The defRepo.findSteps(defA1) returns steps that DON'T include stepIdA2
        when(defRepo.findSteps(defA1)).thenReturn(List.of(
                mockStep(stepIdA1, defA1, "step_a1")));

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, stepInstanceA1Valid,
                null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getMessage()).contains("Step does not belong to the workflow definition");
                });
    }

    @Test
    void createApproval_nonexistentStepInstance_rejected400() {
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        when(stepInstanceRepo.findById(tenantA, stepInstanceA1Valid))
                .thenReturn(Optional.empty());

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, stepInstanceA1Valid,
                null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getMessage()).contains("StepInstance not found");
                });
    }

    @Test
    void createApproval_nonexistentInstance_rejected400() {
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.empty());

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, null,
                null, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void createApproval_zeroUuidRequestedFromUserId_rejected400() {
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        UUID zeroUuid = new UUID(0L, 0L);

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, null,
                zeroUuid, null, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getMessage()).contains("requestedFromUserId is invalid");
                });
    }

    @Test
    void createApproval_blankRequestedFromRole_rejected400() {
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));

        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, null,
                null, "   ", null, UUID.randomUUID());

        assertThatThrownBy(() -> service.createApproval(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> {
                    var rse = (ResponseStatusException) t;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getMessage()).contains("requestedFromRole cannot be blank");
                });
    }

    // ===== Positive test =====

    @Test
    void createApproval_allReferencesValid_persistsAndAudits() {
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));
        when(stepInstanceRepo.findById(tenantA, stepInstanceA1Valid))
                .thenReturn(Optional.of(mockStepInstance(tenantA, instanceA1, stepIdA1, stepInstanceA1Valid)));
        when(defRepo.findSteps(defA1)).thenReturn(List.of(
                mockStep(stepIdA1, defA1, "step_a1")));
        when(approvalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instanceRepo.findById(tenantA, instanceA1)).thenReturn(Optional.of(
                mockInstance(tenantA, instanceA1, defA1)));

        var requesterId = UUID.randomUUID();
        var req = WorkflowApprovalRequest.create(
                tenantA, instanceA1, stepInstanceA1Valid,
                UUID.randomUUID(), "MANAGER", Instant.now().plusSeconds(3600),
                requesterId);

        var saved = service.createApproval(req, requesterId);

        assertThat(saved).isNotNull();
        assertThat(saved.status()).isEqualTo(WorkflowApprovalRequest.Status.PENDING);
        verify(approvalRepo, times(1)).save(any());
        verify(auditRepo, times(1)).save(any());
    }

    // ===== Helpers =====
    // WorkflowInstance / WorkflowStepInstance / WorkflowStep are Java records,
    // so we can't mock them with vanilla Mockito. We construct real instances
    // using their factory methods (only the fields needed by validateReferences
    // are populated; the rest are left as defaults / nulls).

    private WorkflowInstance mockInstance(UUID tenantId, UUID instanceId, UUID defId) {
        // WorkflowInstance.start(tenantId, defId, version, businessEntityType, businessEntityId, currentStepKey, startedBy, correlationId)
        return WorkflowInstance.start(
                tenantId, defId, 1,
                "TEST", UUID.randomUUID(),
                "step_a1", UUID.randomUUID(), UUID.randomUUID());
    }

    private WorkflowStepInstance mockStepInstance(UUID tenantId, UUID instanceId,
                                                    UUID stepId, UUID stepInstanceId) {
        // Use WorkflowStepInstance.create() then it returns a record with id().
        var created = WorkflowStepInstance.create(
                tenantId, instanceId, stepId, "step_" + stepInstanceId.toString().substring(0, 4),
                null, null, null);
        // Replace the auto-generated id with the requested one by constructing a new record
        return new WorkflowStepInstance(
                stepInstanceId, created.tenantId(), created.workflowInstanceId(),
                created.workflowStepId(), created.stepKey(), created.status(),
                created.assignedUserId(), created.assignedRole(),
                created.startedAt(), created.completedAt(), created.dueAt(),
                created.attemptCount(), created.result(),
                created.version(), created.createdAt(), created.updatedAt());
    }

    private WorkflowStep mockStep(UUID stepId, UUID defId, String stepKey) {
        WorkflowStep.StepType stepType = WorkflowStep.StepType.ACTION;
        return new WorkflowStep(
                stepId, tenantA, defId, stepKey, "Step " + stepKey, stepType,
                1, null, null, null, null,
                0, java.time.Instant.now(), java.time.Instant.now());
    }
}
