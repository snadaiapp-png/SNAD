package com.sanad.platform.hr.onboarding.domain;

import com.sanad.platform.hr.recruitment.domain.HrTransitionContext;
import com.sanad.platform.hr.recruitment.domain.HrTransitionDecision;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * HRM-G1 — HrOnboardingPlan/Task transition guard (design §6.4 matrix).
 *
 * <p>Plan: ACTIVE→COMPLETED (derived-only; the service layer may call this
 * only from the completion derivation, never from a manual command) /
 * ACTIVE→CANCELLED (registered reason; cascades cancel OPEN tasks).
 * Task: OPEN→DONE / OPEN→WAIVED (registered reason). DONE and WAIVED are
 * terminal and never swap. Fail-closed on unknown pairs.</p>
 */
public final class HrOnboardingTransitions {

    private static final Map<HrOnboardingPlanState, Set<HrOnboardingPlanState>> PLAN_ALLOWED = Map.of(
            HrOnboardingPlanState.ACTIVE, Set.of(HrOnboardingPlanState.COMPLETED, HrOnboardingPlanState.CANCELLED),
            HrOnboardingPlanState.COMPLETED, Set.of(),
            HrOnboardingPlanState.CANCELLED, Set.of());

    private static final Map<HrOnboardingTaskState, Set<HrOnboardingTaskState>> TASK_ALLOWED = build();

    private static Map<HrOnboardingTaskState, Set<HrOnboardingTaskState>> build() {
        Map<HrOnboardingTaskState, Set<HrOnboardingTaskState>> m = new EnumMap<>(HrOnboardingTaskState.class);
        m.put(HrOnboardingTaskState.OPEN, Set.of(HrOnboardingTaskState.DONE, HrOnboardingTaskState.WAIVED));
        m.put(HrOnboardingTaskState.DONE, Set.of());
        m.put(HrOnboardingTaskState.WAIVED, Set.of());
        return Map.copyOf(m);
    }

    private HrOnboardingTransitions() {
    }

    public static boolean isPlanTransitionAllowed(HrOnboardingPlanState from, HrOnboardingPlanState to) {
        return PLAN_ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTaskTransitionAllowed(HrOnboardingTaskState from, HrOnboardingTaskState to) {
        return TASK_ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean taskTransitionRequiresReason(HrOnboardingTaskState from, HrOnboardingTaskState to) {
        return to == HrOnboardingTaskState.WAIVED;
    }

    public static boolean planTransitionRequiresReason(HrOnboardingPlanState from, HrOnboardingPlanState to) {
        return to == HrOnboardingPlanState.CANCELLED;
    }

    public static HrTransitionDecision checkTask(HrTransitionContext ctx, HrOnboardingTaskState from, HrOnboardingTaskState to) {
        if (!isTaskTransitionAllowed(from, to)) {
            return HrTransitionDecision.forbid("HRM_TRANSITION_FORBIDDEN");
        }
        if (taskTransitionRequiresReason(from, to)) {
            if (ctx == null || ctx.reasonCode() == null
                    || !OnboardingReasonCodes.isRegistered(ctx.reasonCode())) {
                return HrTransitionDecision.forbid("HRM_REASON_REJECTED");
            }
        }
        return HrTransitionDecision.allow();
    }

    public static HrTransitionDecision checkPlan(HrTransitionContext ctx, HrOnboardingPlanState from, HrOnboardingPlanState to) {
        if (!isPlanTransitionAllowed(from, to)) {
            return HrTransitionDecision.forbid("HRM_TRANSITION_FORBIDDEN");
        }
        if (planTransitionRequiresReason(from, to)) {
            if (ctx == null || ctx.reasonCode() == null
                    || !OnboardingReasonCodes.isRegistered(ctx.reasonCode())) {
                return HrTransitionDecision.forbid("HRM_REASON_REJECTED");
            }
        }
        return HrTransitionDecision.allow();
    }
}
