package com.sanad.platform.hr.onboarding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * T9 RED acceptance sentinel for HRM onboarding.
 *
 * <p>This test intentionally avoids compile-time references to T9 production
 * classes so the RED result proves the onboarding contract is missing rather
 * than failing Java compilation or PostgreSQL/environment setup. GREEN must
 * introduce the governed onboarding service before behavioral acceptance can
 * proceed to eligibility, checklist/task creation, ownership/deadlines,
 * required-task completion gating, and lifecycle events.</p>
 */
class HrOnboardingServiceTest {

    private static final String ONBOARDING_SERVICE =
            "com.sanad.platform.hr.onboarding.HrOnboardingService";

    @Test
    @DisplayName("T9 RED: governed onboarding service contract exists")
    void governedOnboardingServiceContractExists() {
        assertThatCode(() -> Class.forName(ONBOARDING_SERVICE))
                .as("T9 RED: HrOnboardingService is required before onboarding can start from an eligible accepted-offer/hire boundary")
                .doesNotThrowAnyException();
    }
}
