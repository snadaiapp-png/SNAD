package com.sanad.platform.workflow;

import com.sanad.platform.workflow.api.WorkflowController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowY2BaselineContractTest {

    @Test
    void workflowControllerKeepsV1BasePathDuringY2Cutover() {
        var mapping = WorkflowController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/workflows");
    }
}
