package com.sanad.platform.crm.lead.domain;

import java.util.Set;

/**
 * Domain policy for lead status transitions.
 * Enforces valid state machine transitions.
 */
public final class LeadStatusPolicy {
    private static final Set<String> VALID_STATUSES = Set.of("NEW", "ASSIGNED", "CONTACTED", "QUALIFIED", "DISQUALIFIED", "ARCHIVED", "CONVERTED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("CONVERTED", "DISQUALIFIED", "ARCHIVED");

    public static void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status.toUpperCase())) {
            throw new IllegalArgumentException("Invalid lead status: " + status);
        }
    }

    public static boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status.toUpperCase());
    }

    public static void assertCanConvert(String currentStatus) {
        if ("CONVERTED".equals(currentStatus)) {
            throw new IllegalStateException("Lead already converted");
        }
    }

    private LeadStatusPolicy() {}
}
