package com.sanad.platform.hr.recruitment.domain;

import java.util.Map;
import java.util.Set;

/**
 * HRM-G1 — HrJobOpening transition guard (design §6.1 matrix).
 *
 * <p>Pure domain contract: allowed/forbidden pairs are decided HERE and the
 * service layer (G1-T3) may only execute transitions this guard allows.
 * Fail-closed: unknown pairs are forbidden.</p>
 *
 * <p>Matrix (§6.1): DRAFT→PENDING_APPROVAL; PENDING_APPROVAL→OPEN (approve) /
 * →DRAFT (reject, reason) / →CANCELLED; OPEN⇄PAUSED; OPEN/PAUSED→CLOSED;
 * DRAFT/PENDING_APPROVAL/OPEN/PAUSED→CANCELLED. CLOSED and CANCELLED are
 * terminal; DRAFT→OPEN directly is forbidden (approval gate).</p>
 */
public final class HrJobOpeningTransitions {

    private static final Map<HrJobOpeningState, Set<HrJobOpeningState>> ALLOWED = Map.of(
            HrJobOpeningState.DRAFT, Set.of(HrJobOpeningState.PENDING_APPROVAL, HrJobOpeningState.CANCELLED),
            HrJobOpeningState.PENDING_APPROVAL, Set.of(
                    HrJobOpeningState.OPEN, HrJobOpeningState.DRAFT, HrJobOpeningState.CANCELLED),
            HrJobOpeningState.OPEN, Set.of(HrJobOpeningState.PAUSED, HrJobOpeningState.CLOSED, HrJobOpeningState.CANCELLED),
            HrJobOpeningState.PAUSED, Set.of(HrJobOpeningState.OPEN, HrJobOpeningState.CLOSED, HrJobOpeningState.CANCELLED),
            HrJobOpeningState.CLOSED, Set.of(),
            HrJobOpeningState.CANCELLED, Set.of());

    private HrJobOpeningTransitions() {
    }

    public static boolean isAllowed(HrJobOpeningState from, HrJobOpeningState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean requiresReason(HrJobOpeningState from, HrJobOpeningState to) {
        return from == HrJobOpeningState.PENDING_APPROVAL && to == HrJobOpeningState.DRAFT;
    }

    public static HrTransitionDecision check(HrTransitionContext ctx, HrJobOpeningState from, HrJobOpeningState to) {
        if (!isAllowed(from, to)) {
            return HrTransitionDecision.forbid("HRM_TRANSITION_FORBIDDEN");
        }
        if (requiresReason(from, to)) {
            if (ctx == null || ctx.reasonCode() == null
                    || !RecruitmentReasonCodes.isRegistered(ctx.reasonCode())) {
                return HrTransitionDecision.forbid("HRM_REASON_REJECTED");
            }
        }
        return HrTransitionDecision.allow();
    }
}
