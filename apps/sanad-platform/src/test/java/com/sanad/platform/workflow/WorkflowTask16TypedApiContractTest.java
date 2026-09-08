package com.sanad.platform.workflow;

import com.sanad.platform.workflow.api.WorkflowController;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Task 16 contract: Y2 endpoints must expose typed DTOs while the existing
 * /api/v1/workflows JSON contract remains protected by WorkflowApiContractTest.
 */
class WorkflowTask16TypedApiContractTest {

    @Test
    void typedDtoBoundaryExists() {
        assertThatCode(() -> Class.forName("com.sanad.platform.workflow.api.WorkflowDtos"))
                .doesNotThrowAnyException();
    }

    @Test
    void workItemEndpointsReturnTypedResponses() throws Exception {
        var mine = WorkflowController.class.getDeclaredMethod(
                "myWorkItems", Authentication.class, int.class);
        var pool = WorkflowController.class.getDeclaredMethod(
                "poolWorkItems", Authentication.class, int.class);

        assertThat(mine.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$WorkItemResponse");
        assertThat(pool.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$WorkItemResponse");
    }

    @Test
    void approvalDtoCarriesLegacyUserIdentityAndY2EmployeeIdentity() throws Exception {
        var dtoClass = Class.forName(
                "com.sanad.platform.workflow.api.WorkflowDtos$ApprovalResponse");
        var componentNames = java.util.Arrays.stream(dtoClass.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(componentNames)
                .contains("requestedFromUserId", "requestedFromEmployeeId");
    }

    @Test
    void approvalEndpointsUseTypedResponsesAndExpectedVersionCommands() throws Exception {
        var decisionRequest = Class.forName(
                "com.sanad.platform.workflow.api.WorkflowController$ApprovalDecisionRequest");
        var requestComponents = java.util.Arrays.stream(decisionRequest.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(requestComponents).containsExactly("expectedVersion", "comments");

        var list = WorkflowController.class.getDeclaredMethod(
                "listPendingApprovals", Authentication.class, int.class);
        var pending = WorkflowController.class.getDeclaredMethod(
                "listPendingApprovalsForUser", Authentication.class, int.class);
        assertThat(list.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$ApprovalResponse");
        assertThat(pending.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$ApprovalResponse");

        var approve = WorkflowController.class.getDeclaredMethod(
                "approveRequest", Authentication.class, java.util.UUID.class, decisionRequest);
        var reject = WorkflowController.class.getDeclaredMethod(
                "rejectRequest", Authentication.class, java.util.UUID.class, decisionRequest);
        assertThat(approve.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$ApprovalResponse");
        assertThat(reject.getGenericReturnType().getTypeName())
                .contains("WorkflowDtos$ApprovalResponse");
    }
}
