package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowApprovalPolicyEngine;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
import com.sanad.platform.workflow.application.WorkflowAuthorizationReadPort;
import com.sanad.platform.workflow.domain.WorkflowApprovalPolicy;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequest;
import com.sanad.platform.workflow.domain.WorkflowApprovalRequestRepository;
import com.sanad.platform.workflow.domain.WorkflowDefinitionRepository;
import com.sanad.platform.workflow.domain.WorkflowInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowStepInstanceRepository;
import com.sanad.platform.workflow.domain.WorkflowTransitionAuditRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 1 / Task 9 — application-boundary authorization for exceptional self approval.
 *
 * <p>An ALLOW policy is necessary but not sufficient: the acting user must also
 * hold WORKFLOW.SELF_APPROVAL_OVERRIDE. A service instance without an explicit
 * authorization port must fail closed.</p>
 */
class WorkflowApprovalSelfApprovalAuthorizationTest {

    private static final String SELF_APPROVAL_OVERRIDE = "WORKFLOW.SELF_APPROVAL_OVERRIDE";

    @Test
    void explicitAllowPolicyStillFailsClosedWithoutOverrideCapabilityAtApplicationBoundary() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        var approvalRepo = mock(WorkflowApprovalRequestRepository.class);
        var instanceRepo = mock(WorkflowInstanceRepository.class);
        var stepInstanceRepo = mock(WorkflowStepInstanceRepository.class);
        var definitionRepo = mock(WorkflowDefinitionRepository.class);
        var auditRepo = mock(WorkflowTransitionAuditRepository.class);

        var request = createRequest(tenantId, instanceId, actorId, actorId);
        stubApprovalPersistence(approvalRepo, instanceRepo, tenantId, instanceId, request);

        var service = new WorkflowApprovalService(
                approvalRepo,
                instanceRepo,
                stepInstanceRepo,
                definitionRepo,
                auditRepo,
                new WorkflowApprovalPolicyEngine(),
                null);

        assertThatThrownBy(() -> service.approve(
                tenantId, request.id(), actorId, request.version(), "self approval"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void explicitAllowPolicyPermitsSelfApprovalWhenOverrideCapabilityIsCurrentlyGranted() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        var approvalRepo = mock(WorkflowApprovalRequestRepository.class);
        var instanceRepo = mock(WorkflowInstanceRepository.class);
        var stepInstanceRepo = mock(WorkflowStepInstanceRepository.class);
        var definitionRepo = mock(WorkflowDefinitionRepository.class);
        var auditRepo = mock(WorkflowTransitionAuditRepository.class);
        var authorizationReadPort = mock(WorkflowAuthorizationReadPort.class);

        var request = createRequest(tenantId, instanceId, actorId, actorId);
        stubApprovalPersistence(approvalRepo, instanceRepo, tenantId, instanceId, request);
        when(authorizationReadPort.findActiveUserIdsByCapability(tenantId, SELF_APPROVAL_OVERRIDE))
                .thenReturn(List.of(actorId));

        var service = new WorkflowApprovalService(
                approvalRepo,
                instanceRepo,
                stepInstanceRepo,
                definitionRepo,
                auditRepo,
                new WorkflowApprovalPolicyEngine(),
                null,
                authorizationReadPort);

        var approved = service.approve(
                tenantId, request.id(), actorId, request.version(), "authorized self approval");

        assertThat(approved.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(actorId);
        verify(authorizationReadPort)
                .findActiveUserIdsByCapability(tenantId, SELF_APPROVAL_OVERRIDE);
    }

    @Test
    void approvalByDifferentUserDoesNotRequireSelfApprovalOverrideCapability() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        var approvalRepo = mock(WorkflowApprovalRequestRepository.class);
        var instanceRepo = mock(WorkflowInstanceRepository.class);
        var stepInstanceRepo = mock(WorkflowStepInstanceRepository.class);
        var definitionRepo = mock(WorkflowDefinitionRepository.class);
        var auditRepo = mock(WorkflowTransitionAuditRepository.class);
        var authorizationReadPort = mock(WorkflowAuthorizationReadPort.class);

        var request = createRequest(tenantId, instanceId, requesterId, approverId);
        stubApprovalPersistence(approvalRepo, instanceRepo, tenantId, instanceId, request);

        var service = new WorkflowApprovalService(
                approvalRepo,
                instanceRepo,
                stepInstanceRepo,
                definitionRepo,
                auditRepo,
                new WorkflowApprovalPolicyEngine(),
                null,
                authorizationReadPort);

        var approved = service.approve(
                tenantId, request.id(), approverId, request.version(), "normal approval");

        assertThat(approved.status()).isEqualTo(WorkflowApprovalRequest.Status.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(approverId);
        verify(authorizationReadPort, never())
                .findActiveUserIdsByCapability(any(UUID.class), any(String.class));
    }

    private WorkflowApprovalRequest createRequest(
            UUID tenantId, UUID instanceId, UUID requestedByUserId, UUID requestedFromUserId) {
        return WorkflowApprovalRequest.create(
                tenantId,
                instanceId,
                null,
                requestedFromUserId,
                "MANAGER",
                Instant.now().plusSeconds(600),
                requestedByUserId,
                null,
                new WorkflowApprovalPolicy(
                        WorkflowApprovalPolicy.Aggregation.ANY_ONE,
                        WorkflowApprovalPolicy.SelfApproval.ALLOW));
    }

    private void stubApprovalPersistence(
            WorkflowApprovalRequestRepository approvalRepo,
            WorkflowInstanceRepository instanceRepo,
            UUID tenantId,
            UUID instanceId,
            WorkflowApprovalRequest request) {
        when(approvalRepo.findById(tenantId, request.id())).thenReturn(Optional.of(request));
        when(approvalRepo.save(any(WorkflowApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalRepo.findByInstance(tenantId, instanceId))
                .thenAnswer(invocation -> List.of());
        when(instanceRepo.findById(tenantId, instanceId)).thenReturn(Optional.empty());
    }
}
