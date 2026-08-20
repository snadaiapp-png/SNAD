package com.sanad.platform.crm.calls.domain;

import java.util.Map;
import java.util.Set;

/**
 * Call lifecycle statuses (G8-03 §10) with the legal transition map.
 *
 * <p>Monotonicity guard (G8-03 §11): an out-of-order event may never move a
 * call BACK to an older state. Terminal states are {@code COMPLETED} /
 * {@code MISSED} / {@code REJECTED} / {@code BUSY} / {@code FAILED}.
 */
public enum CallStatus {
    RINGING,
    ANSWERED,
    COMPLETED,
    MISSED,
    REJECTED,
    BUSY,
    FAILED;

    /** Legal transitions, e.g. RINGING -> ANSWERED; ANSWERED -> COMPLETED. */
    private static final Map<CallStatus, Set<CallStatus>> ALLOWED = Map.of(
            RINGING, Set.of(ANSWERED, MISSED, REJECTED, BUSY, FAILED),
            ANSWERED, Set.of(COMPLETED, FAILED),
            COMPLETED, Set.of(),
            MISSED, Set.of(),
            REJECTED, Set.of(),
            BUSY, Set.of(),
            FAILED, Set.of());

    private static final Map<CallStatus, Integer> RANK = Map.of(
            RINGING, 0, ANSWERED, 1, COMPLETED, 2,
            MISSED, 3, REJECTED, 3, BUSY, 3, FAILED, 3);

    /** Whether {@code from -> to} is a legal, non-regressing transition. */
    public static boolean isAllowedTransition(CallStatus from, CallStatus to) {
        if (from == to) return true; // duplicate event — idempotent by caller
        if (RANK.getOrDefault(to, 0) < RANK.getOrDefault(from, 0)) return false;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == MISSED || this == REJECTED
                || this == BUSY || this == FAILED;
    }
}
