package com.sanad.platform.hr.recruitment.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * HRM-G1 — HrOffer transition guard (design §6.3 matrix).
 *
 * <p>DRAFT→PENDING_APPROVAL (policy path) or DRAFT→EXTENDED (no-approval
 * path); PENDING_APPROVAL→EXTENDED (approve) / →DRAFT (reject, reason);
 * EXTENDED→ACCEPTED/DECLINED/WITHDRAWN(reason)/EXPIRED. EXTENDED→DRAFT
 * forbidden (new OfferVersion + re-extension instead). ACCEPTED, DECLINED,
 * EXPIRED, WITHDRAWN are terminal. Fail-closed on unknown pairs.</p>
 */
public final class HrOfferTransitions {

    private static final Map<HrOfferState, Set<HrOfferState>> ALLOWED = build();

    private static Map<HrOfferState, Set<HrOfferState>> build() {
        Map<HrOfferState, Set<HrOfferState>> m = new EnumMap<>(HrOfferState.class);
        m.put(HrOfferState.DRAFT, Set.of(HrOfferState.PENDING_APPROVAL, HrOfferState.EXTENDED));
        m.put(HrOfferState.PENDING_APPROVAL, Set.of(HrOfferState.EXTENDED, HrOfferState.DRAFT));
        m.put(HrOfferState.EXTENDED, Set.of(
                HrOfferState.ACCEPTED, HrOfferState.DECLINED, HrOfferState.WITHDRAWN, HrOfferState.EXPIRED));
        m.put(HrOfferState.ACCEPTED, Set.of());
        m.put(HrOfferState.DECLINED, Set.of());
        m.put(HrOfferState.EXPIRED, Set.of());
        m.put(HrOfferState.WITHDRAWN, Set.of());
        return Map.copyOf(m);
    }

    private HrOfferTransitions() {
    }

    public static boolean isAllowed(HrOfferState from, HrOfferState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean requiresReason(HrOfferState from, HrOfferState to) {
        return from == HrOfferState.PENDING_APPROVAL && to == HrOfferState.DRAFT
                || from == HrOfferState.EXTENDED && to == HrOfferState.WITHDRAWN;
    }

    public static HrTransitionDecision check(HrTransitionContext ctx, HrOfferState from, HrOfferState to) {
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
