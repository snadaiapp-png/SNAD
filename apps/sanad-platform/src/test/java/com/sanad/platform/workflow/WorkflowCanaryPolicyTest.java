package com.sanad.platform.workflow;

import com.sanad.platform.workflow.domain.WorkflowCanaryPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0.G8 — system canary classification is derived from the reserved code
 * prefix so historical canary definitions can be separated (never mutated)
 * by the product workspace.
 */
class WorkflowCanaryPolicyTest {

    @Test
    void canaryPrefixClassifiesAsSystemCanary() {
        assertThat(WorkflowCanaryPolicy.isSystemCanary("Y2-PROD-CANARY-6737ff62eebc")).isTrue();
        assertThat(WorkflowCanaryPolicy.isSystemCanary("Y2-PROD-CANARY-")).isTrue();
    }

    @Test
    void businessCodesAreNotCanaries() {
        assertThat(WorkflowCanaryPolicy.isSystemCanary("LEAVE-APPROVAL")).isFalse();
        assertThat(WorkflowCanaryPolicy.isSystemCanary("Y2-PROD-CANARY")).isFalse(); // prefix w/o separator
        assertThat(WorkflowCanaryPolicy.isSystemCanary("MY-Y2-PROD-CANARY-X")).isFalse();
        assertThat(WorkflowCanaryPolicy.isSystemCanary(null)).isFalse();
        assertThat(WorkflowCanaryPolicy.isSystemCanary("")).isFalse();
    }
}
