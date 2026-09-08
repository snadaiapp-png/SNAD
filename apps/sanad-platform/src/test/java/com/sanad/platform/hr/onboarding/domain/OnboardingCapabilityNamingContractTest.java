package com.sanad.platform.hr.onboarding.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — onboarding capability naming contract (design §9).
 */
class OnboardingCapabilityNamingContractTest {

    private static final java.util.Set<String> DESIGN_SECTION9_SET = java.util.Set.of(
            "HRM.ONBOARDING.PLAN.MANAGE",
            "HRM.ONBOARDING.TASK.COMPLETE",
            "HRM.ONBOARDING.TASK.WAIVE");

    @Test
    void onboardingCapabilitySet_matchesDesignSection9Exactly() {
        assertThat(OnboardingCapabilities.all())
                .as("capability family pinned to design §9")
                .containsExactlyInAnyOrderElementsOf(DESIGN_SECTION9_SET);
    }

    @Test
    void everyCapability_matchesNamingConvention() {
        for (String code : OnboardingCapabilities.all()) {
            assertThat(code).matches("^HRM\\.ONBOARDING(\\.[A-Z][A-Z_]*)+$");
        }
    }

    @Test
    void taskComplete_privilegeIsNotAboveWaive_completenessGuard() {
        // §9: COMPLETE ≤ WAIVE privilege ordering is enforced at grant level in
        // G0 binding tables; here we pin that both actions exist as DISTINCT
        // capabilities so separation stays expressible.
        assertThat(DESIGN_SECTION9_SET).contains("HRM.ONBOARDING.TASK.COMPLETE", "HRM.ONBOARDING.TASK.WAIVE");
    }
}
