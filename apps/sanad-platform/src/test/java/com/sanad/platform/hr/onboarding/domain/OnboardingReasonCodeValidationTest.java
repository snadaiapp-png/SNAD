package com.sanad.platform.hr.onboarding.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — onboarding reason-code registry validation (fail-closed).
 */
class OnboardingReasonCodeValidationTest {

    @Test
    void all_returnsTheExactRegisteredUniverse() {
        assertThat(OnboardingReasonCodes.all()).containsExactlyInAnyOrder(
                OnboardingReasonCodes.TASK_WAIVER,
                OnboardingReasonCodes.PLAN_CANCELLATION);
    }

    @Test
    void registeredCodes_validate() {
        assertThat(OnboardingReasonCodes.isRegistered(OnboardingReasonCodes.TASK_WAIVER)).isTrue();
        assertThat(OnboardingReasonCodes.isRegistered(OnboardingReasonCodes.PLAN_CANCELLATION)).isTrue();
    }

    @Test
    void unknownOrNullCodes_failClosed() {
        assertThat(OnboardingReasonCodes.isRegistered("UNKNOWN")).isFalse();
        assertThat(OnboardingReasonCodes.isRegistered("")).isFalse();
        assertThat(OnboardingReasonCodes.isRegistered(null)).isFalse();
    }
}
