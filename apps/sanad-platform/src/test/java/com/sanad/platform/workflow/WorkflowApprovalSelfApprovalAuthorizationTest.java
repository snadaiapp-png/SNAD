package com.sanad.platform.workflow;

import com.sanad.platform.workflow.application.WorkflowApprovalPolicyEngine;
import com.sanad.platform.workflow.application.WorkflowApprovalService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 1 / Task 9 — application-boundary authorization for exceptional self approval.
 *
 * <p>An ALLOW policy is necessary but not sufficient: the acting user must also
 * hold WORKFLOW.SELF_APPROVAL_OVERRIDE. A service instance without an explicit
 * authorization port must fail closed.</p>
 */
class WorkflowApprovalSelfApprovalAuthorizationTest {

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

        var request = WorkflowApprovalRequest.create(
                tenantId,
                instanceId,
                null,
                actorId,
                "MANAGER",
                Instant.now().plusSeconds(600),
                actorId,
                null,
                new WorkflowApprovalPolicy(
                        WorkflowApprovalPolicy.Aggregation.ANY_ONE,
                        WorkflowApprovalPolicy.SelfApproval.ALLOW));

        when(approvalRepo.findById(tenantId, request.id())).thenReturn(Optional.of(request));
        when(approvalRepo.save(any(WorkflowApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepo.findById(tenantId, instanceId)).thenReturn(Optional.empty());

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
}
