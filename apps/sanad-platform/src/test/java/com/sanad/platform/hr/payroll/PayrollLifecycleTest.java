package com.sanad.platform.hr.payroll;

import com.sanad.platform.hr.payroll.application.PayrollLifecycle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayrollLifecycleTest {

    @Test
    void allowsOnlyGovernedForwardTransitions() {
        assertThat(PayrollLifecycle.DRAFT.canTransitionTo(PayrollLifecycle.CALCULATED)).isTrue();
        assertThat(PayrollLifecycle.CALCULATED.canTransitionTo(PayrollLifecycle.REVIEWED)).isTrue();
        assertThat(PayrollLifecycle.REVIEWED.canTransitionTo(PayrollLifecycle.APPROVED)).isTrue();
        assertThat(PayrollLifecycle.APPROVED.canTransitionTo(PayrollLifecycle.EXPORTED)).isTrue();

        assertThat(PayrollLifecycle.DRAFT.canTransitionTo(PayrollLifecycle.APPROVED)).isFalse();
        assertThat(PayrollLifecycle.CALCULATED.canTransitionTo(PayrollLifecycle.EXPORTED)).isFalse();
        assertThat(PayrollLifecycle.APPROVED.canTransitionTo(PayrollLifecycle.CALCULATED)).isFalse();
        assertThat(PayrollLifecycle.EXPORTED.canTransitionTo(PayrollLifecycle.REVIEWED)).isFalse();
    }

    @Test
    void exportedIsTerminalAndApprovedCannotRecalculate() {
        assertThat(PayrollLifecycle.EXPORTED.allowedTargets()).isEmpty();
        assertThatThrownBy(() -> PayrollLifecycle.requireTransition(
                PayrollLifecycle.APPROVED, PayrollLifecycle.CALCULATED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");
    }
}
