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
    void recalculationIsAllowedOnlyWhileCalculated() {
        assertThat(PayrollLifecycle.CALCULATED.canRecalculate()).isTrue();
        assertThat(PayrollLifecycle.DRAFT.canRecalculate()).isFalse();
        assertThat(PayrollLifecycle.REVIEWED.canRecalculate()).isFalse();
        assertThat(PayrollLifecycle.APPROVED.canRecalculate()).isFalse();
        assertThat(PayrollLifecycle.EXPORTED.canRecalculate()).isFalse();
        assertThat(PayrollLifecycle.CANCELLED.canRecalculate()).isFalse();

        assertThatThrownBy(() -> PayrollLifecycle.requireRecalculationAllowed(PayrollLifecycle.APPROVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_RECALCULATION_NOT_ALLOWED");
    }

    @Test
    void exportedIsTerminalAndApprovedCannotTransitionBackToCalculated() {
        assertThat(PayrollLifecycle.EXPORTED.allowedTargets()).isEmpty();
        assertThatThrownBy(() -> PayrollLifecycle.requireTransition(
                PayrollLifecycle.APPROVED, PayrollLifecycle.CALCULATED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HRM_PAYROLL_LIFECYCLE_INVALID");
    }
}
