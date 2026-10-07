package com.sanad.platform.hr.payroll.application;

import java.util.Map;
import java.util.Set;

/**
 * G4-T5 governed payroll lifecycle. Country-neutral; no statutory behavior.
 */
public enum PayrollLifecycle {
    DRAFT,
    CALCULATED,
    REVIEWED,
    APPROVED,
    EXPORTED,
    CANCELLED;

    private static final Map<PayrollLifecycle, Set<PayrollLifecycle>> ALLOWED = Map.of(
            DRAFT, Set.of(CALCULATED, CANCELLED),
            CALCULATED, Set.of(REVIEWED, CANCELLED),
            REVIEWED, Set.of(APPROVED, CANCELLED),
            APPROVED, Set.of(EXPORTED),
            EXPORTED, Set.of(),
            CANCELLED, Set.of()
    );

    public Set<PayrollLifecycle> allowedTargets() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    public boolean canTransitionTo(PayrollLifecycle target) {
        return target != null && allowedTargets().contains(target);
    }

    public boolean canRecalculate() {
        return this == CALCULATED;
    }

    public static void requireRecalculationAllowed(PayrollLifecycle current) {
        if (current == null || !current.canRecalculate()) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_RECALCULATION_NOT_ALLOWED: " + String.valueOf(current));
        }
    }

    public static void requireTransition(PayrollLifecycle current, PayrollLifecycle target) {
        if (current == null || target == null || !current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "HRM_PAYROLL_LIFECYCLE_INVALID: "
                            + String.valueOf(current) + " -> " + String.valueOf(target));
        }
    }
}
