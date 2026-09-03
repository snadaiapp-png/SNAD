package com.sanad.platform.workflow;

import com.sanad.platform.workflow.domain.WorkflowWorkItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDirectWorkItemLifecycleTest {

    @Test
    void directItemStartsClaimedByItsExplicitAssignee() {
        UUID tenantId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID stepInstanceId = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();

        WorkflowWorkItem item = WorkflowWorkItem.create(
                tenantId, instanceId, stepInstanceId,
                WorkflowWorkItem.Type.HUMAN_TASK,
                WorkflowWorkItem.AssignmentMode.DIRECT,
                assignee,
                "WORKFLOW", "INSTANCE", instanceId,
                "Direct task", null, 0, null, null);

        assertThat(item.status()).isEqualTo(WorkflowWorkItem.Status.CLAIMED);
        assertThat(item.assigneeEmployeeId()).isEqualTo(assignee);
        assertThat(item.claimedByEmployeeId()).isEqualTo(assignee);
        assertThat(item.claimedAt()).isNotNull();
    }
}
