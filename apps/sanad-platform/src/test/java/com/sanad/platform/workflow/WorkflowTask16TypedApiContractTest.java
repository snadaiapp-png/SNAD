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
}
