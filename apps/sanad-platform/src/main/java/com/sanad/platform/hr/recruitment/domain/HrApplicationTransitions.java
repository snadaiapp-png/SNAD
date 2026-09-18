package com.sanad.platform.hr.recruitment.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * HRM-G1 — HrApplication transition guard (design §6.2 matrix).
 *
 * <p>Forward-only one-stage advancement; REJECTED/WITHDRAWN reachable from
 * every pre-HIRED stage with mandatory registered reason; HIRED reachable
 * ONLY from OFFER (the §7 conversion performs the state change inside its
 * transaction). Backward transitions and stage skips are forbidden.
 * Fail-closed on unknown pairs.</p>
 */
public final class HrApplicationTransitions {

    private static final Map<HrApplicationState, Set<HrApplicationState>> ALLOWED = build();

    private static Map<HrApplicationState, Set<HrApplicationState>> build() {
        Map<HrApplicationState, Set<HrApplicationState>> m = new EnumMap<>(HrApplicationState.class);
        m.put(HrApplicationState.APPLIED, Set.of(
                HrApplicationState.SCREENING, HrApplicationState.REJECTED, HrApplicationState.WITHDRAWN));
        m.put(HrApplicationState.SCREENING, Set.of(
                HrApplicationState.INTERVIEW, HrApplicationState.REJECTED, HrApplicationState.WITHDRAWN));
        m.put(HrApplicationState.INTERVIEW, Set.of(
                HrApplicationState.OFFER, HrApplicationState.REJECTED, HrApplicationState.WITHDRAWN));
        m.put(HrApplicationState.OFFER, Set.of(
                HrApplicationState.HIRED, HrApplicationState.REJECTED, HrApplicationState.WITHDRAWN));
        m.put(HrApplicationState.HIRED, Set.of());
        m.put(HrApplicationState.REJECTED, Set.of());
        m.put(HrApplicationState.WITHDRAWN, Set.of());
        return Map.copyOf(m);
    }

    private HrApplicationTransitions() {
    }

    public static boolean isAllowed(HrApplicationState from, HrApplicationState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean requiresReason(HrApplicationState from, HrApplicationState to) {
        return to == HrApplicationState.REJECTED || to == HrApplicationState.WITHDRAWN;
    }

    public static HrTransitionDecision check(HrTransitionContext ctx, HrApplicationState from, HrApplicationState to) {
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
