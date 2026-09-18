package com.sanad.platform.hr.onboarding.domain;

import com.sanad.platform.hr.recruitment.domain.HrTransitionContext;
import com.sanad.platform.hr.recruitment.domain.HrTransitionDecision;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1-T1 — §6.4 HrOnboardingPlan / HrOnboardingTask transition guards.
 *
 * <p>Plan: ACTIVE→COMPLETED (derived only — no manual completion, no percent
 * fields) / ACTIVE→CANCELLED (reason). Task: OPEN→DONE / OPEN→WAIVED
 * (capability-gated + reason). DONE/WAIVED terminal; DONE↔WAIVED forbidden.</p>
 */
class HrOnboardingStateTransitionGuardTest {

    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private HrTransitionContext ctx(String reasonCode) {
        return new HrTransitionContext(TENANT, "user-onboarding", reasonCode);
    }

    @Test
    void plan_completesFromActive_derivedOnly() {
        assertThat(HrOnboardingTransitions.isPlanTransitionAllowed(HrOnboardingPlanState.ACTIVE, HrOnboardingPlanState.COMPLETED))
                .isTrue();
    }

    @Test
    void plan_cancelsFromActive_withReason() {
        assertThat(HrOnboardingTransitions.isPlanTransitionAllowed(HrOnboardingPlanState.ACTIVE, HrOnboardingPlanState.CANCELLED))
                .isTrue();
        HrTransitionDecision d = HrOnboardingTransitions
                .checkPlan(ctx(OnboardingReasonCodes.PLAN_CANCELLATION), HrOnboardingPlanState.ACTIVE, HrOnboardingPlanState.CANCELLED);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void plan_terminalStates_neverTransitionOut() {
        for (HrOnboardingPlanState to : HrOnboardingPlanState.values()) {
            assertThat(HrOnboardingTransitions.isPlanTransitionAllowed(HrOnboardingPlanState.COMPLETED, to))
                    .as("COMPLETED is terminal (to=%s)", to).isFalse();
            assertThat(HrOnboardingTransitions.isPlanTransitionAllowed(HrOnboardingPlanState.CANCELLED, to))
                    .as("CANCELLED is terminal (to=%s)", to).isFalse();
        }
    }

    @Test
    void plan_cancellation_requiresRegisteredReason() {
        assertThat(HrOnboardingTransitions
                .checkPlan(ctx("NOPE"), HrOnboardingPlanState.ACTIVE, HrOnboardingPlanState.CANCELLED).allowed())
                .isFalse();
    }

    @Test
    void task_openCompletesToDone() {
        assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.OPEN, HrOnboardingTaskState.DONE))
                .isTrue();
        assertThat(HrOnboardingTransitions
                .checkTask(ctx(null), HrOnboardingTaskState.OPEN, HrOnboardingTaskState.DONE).allowed())
                .isTrue();
    }

    @Test
    void task_openWaives_withRegisteredReason() {
        assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.OPEN, HrOnboardingTaskState.WAIVED))
                .isTrue();
        assertThat(HrOnboardingTransitions.taskTransitionRequiresReason(HrOnboardingTaskState.OPEN, HrOnboardingTaskState.WAIVED))
                .as("waiver reason mandatory")
                .isTrue();
        assertThat(HrOnboardingTransitions
                .checkTask(ctx(OnboardingReasonCodes.TASK_WAIVER), HrOnboardingTaskState.OPEN, HrOnboardingTaskState.WAIVED)
                .allowed())
                .isTrue();
    }

    @Test
    void task_waive_withoutReason_failsClosed() {
        assertThat(HrOnboardingTransitions
                .checkTask(ctx(null), HrOnboardingTaskState.OPEN, HrOnboardingTaskState.WAIVED).allowed())
                .isFalse();
    }

    @Test
    void task_terminalStates_neverTransitionOut_andNeverSwap() {
        for (HrOnboardingTaskState to : HrOnboardingTaskState.values()) {
            assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.DONE, to))
                    .as("DONE is terminal (to=%s)", to).isFalse();
            assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.WAIVED, to))
                    .as("WAIVED is terminal (to=%s)", to).isFalse();
        }
        assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.DONE, HrOnboardingTaskState.WAIVED))
                .as("DONE→WAIVED explicitly forbidden").isFalse();
        assertThat(HrOnboardingTransitions.isTaskTransitionAllowed(HrOnboardingTaskState.WAIVED, HrOnboardingTaskState.DONE))
                .as("WAIVED→DONE explicitly forbidden").isFalse();
    }

    @Test
    void guard_acceptsExactlyTheDesignSection6Matrix() {
        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "ACTIVE->COMPLETED",
                        "ACTIVE->CANCELLED"),
                HrOnboardingPlanState.class, HrOnboardingPlanState.values(),
                HrOnboardingTransitions::isPlanTransitionAllowed);
        com.sanad.platform.test.HrG1StateMatrixHarness.assertTerminals(
                java.util.Set.of(HrOnboardingPlanState.COMPLETED, HrOnboardingPlanState.CANCELLED),
                HrOnboardingPlanState.values(),
                HrOnboardingTransitions::isPlanTransitionAllowed);

        com.sanad.platform.test.HrG1StateMatrixHarness.assertExactly(
                java.util.Set.of(
                        "OPEN->DONE",
                        "OPEN->WAIVED"),
                HrOnboardingTaskState.class, HrOnboardingTaskState.values(),
                HrOnboardingTransitions::isTaskTransitionAllowed);
        com.sanad.platform.test.HrG1StateMatrixHarness.assertTerminals(
                java.util.Set.of(HrOnboardingTaskState.DONE, HrOnboardingTaskState.WAIVED),
                HrOnboardingTaskState.values(),
                HrOnboardingTransitions::isTaskTransitionAllowed);
    }
}
