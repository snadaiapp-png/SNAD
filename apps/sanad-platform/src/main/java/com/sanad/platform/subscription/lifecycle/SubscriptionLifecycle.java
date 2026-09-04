package com.sanad.platform.subscription.lifecycle;

import java.util.Map;
import java.util.Set;

/**
 * Subscription lifecycle state machine — single source of truth for transition
 * legality. The frontend never writes {@code status} directly; it invokes
 * commands and the backend validates via this table.
 *
 * <p>Statuses: DRAFT, PENDING_ACTIVATION, PENDING_PAYMENT, TRIAL, ACTIVE,
 * PAST_DUE, GRACE_PERIOD, PAUSED, SUSPENDED, CANCELLED, EXPIRED, TERMINATED.
 * The legacy value TRIALING maps to TRIAL (and is accepted everywhere TRIAL
 * is, so existing rows and callers keep working).
 */
public final class SubscriptionLifecycle {

    /** Command → allowed (fromStatus → toStatus) transitions. */
    public static final Map<String, Map<String, String>> COMMANDS = Map.ofEntries(
            Map.entry("ACTIVATE", Map.of(
                    "DRAFT", "ACTIVE",
                    "PENDING_ACTIVATION", "ACTIVE",
                    "PENDING_PAYMENT", "ACTIVE",
                    "TRIAL", "ACTIVE",
                    "TRIALING", "ACTIVE")),
            Map.entry("START_TRIAL", Map.of(
                    "DRAFT", "TRIAL",
                    "TRIALING", "TRIALING")),
            Map.entry("PAUSE", Map.of(
                    "ACTIVE", "PAUSED",
                    "TRIAL", "PAUSED",
                    "TRIALING", "PAUSED")),
            Map.entry("RESUME", Map.of(
                    "PAUSED", "ACTIVE")),
            Map.entry("SUSPEND", Map.of(
                    "ACTIVE", "SUSPENDED",
                    "PAST_DUE", "SUSPENDED",
                    "GRACE_PERIOD", "SUSPENDED",
                    "TRIAL", "SUSPENDED",
                    "TRIALING", "SUSPENDED")),
            Map.entry("CANCEL", Map.of(
                    "DRAFT", "CANCELLED",
                    "PENDING_ACTIVATION", "CANCELLED",
                    "PENDING_PAYMENT", "CANCELLED",
                    "TRIAL", "CANCELLED",
                    "TRIALING", "CANCELLED",
                    "ACTIVE", "CANCELLED",
                    "PAST_DUE", "CANCELLED",
                    "GRACE_PERIOD", "CANCELLED",
                    "PAUSED", "CANCELLED")),
            Map.entry("RENEW", Map.of(
                    "ACTIVE", "ACTIVE",
                    "TRIAL", "ACTIVE",
                    "TRIALING", "ACTIVE",
                    "PAST_DUE", "ACTIVE",
                    "GRACE_PERIOD", "ACTIVE")),
            Map.entry("EXPIRE", Map.of(
                    "TRIAL", "EXPIRED",
                    "TRIALING", "EXPIRED",
                    "GRACE_PERIOD", "EXPIRED")),
            Map.entry("TERMINATE", Map.of(
                    "ACTIVE", "TERMINATED",
                    "PAUSED", "TERMINATED",
                    "SUSPENDED", "TERMINATED",
                    "PAST_DUE", "TERMINATED",
                    "GRACE_PERIOD", "TERMINATED",
                    "TRIAL", "TERMINATED",
                    "TRIALING", "TERMINATED",
                    "CANCELLED", "TERMINATED")),
            Map.entry("MARK_PAST_DUE", Map.of(
                    "ACTIVE", "PAST_DUE",
                    "TRIAL", "PAST_DUE",
                    "TRIALING", "PAST_DUE")),
            Map.entry("ENTER_GRACE", Map.of(
                    "PAST_DUE", "GRACE_PERIOD")),
            Map.entry("SCHEDULE_CANCELLATION", Map.of(
                    // stays in the current status; cancellation happens at period end
                    "ACTIVE", "ACTIVE",
                    "TRIAL", "TRIAL",
                    "TRIALING", "TRIALING",
                    "PAST_DUE", "PAST_DUE")),
            Map.entry("REQUEST_ACTIVATION", Map.of(
                    "DRAFT", "PENDING_ACTIVATION",
                    "PENDING_PAYMENT", "PENDING_PAYMENT")),
            Map.entry("PAYMENT_RECEIVED", Map.of(
                    "PENDING_PAYMENT", "PENDING_ACTIVATION",
                    "PAST_DUE", "ACTIVE",
                    "GRACE_PERIOD", "ACTIVE"))
    );

    public static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_ACTIVATION", "PENDING_PAYMENT", "TRIAL", "TRIALING", "ACTIVE",
            "PAST_DUE", "GRACE_PERIOD", "PAUSED", "SUSPENDED", "CANCELLED", "EXPIRED", "TERMINATED");

    public static final Set<String> TERMINAL_STATUSES = Set.of("CANCELLED", "EXPIRED", "TERMINATED");

    /** Legacy status → current status. Existing DB rows/callers keep working. */
    public static final Map<String, String> LEGACY_ALIASES = Map.of(
            "TRIALING", "TRIAL");

    private SubscriptionLifecycle() {
    }

    public record Transition(String command, String fromStatus, String toStatus) {
    }

    public static Transition transition(String command, String fromStatus) {
        Map<String, String> table = COMMANDS.get(command);
        if (table == null) {
            throw new IllegalArgumentException("Unknown subscription command: " + command);
        }
        String from = normalize(fromStatus);
        if (!STATUSES.contains(fromStatus)) {
            throw new IllegalArgumentException("Unknown subscription status: " + fromStatus);
        }
        String to = table.get(fromStatus);
        if (to == null) {
            throw new IllegalStateException(
                    "Illegal subscription transition: " + command + " from " + fromStatus);
        }
        return new Transition(command, fromStatus, to);
    }

    public static boolean isLegal(String command, String fromStatus) {
        Map<String, String> table = COMMANDS.get(command);
        return table != null && table.containsKey(fromStatus);
    }

    public static String normalize(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Subscription status must not be null");
        }
        return LEGACY_ALIASES.getOrDefault(status, status);
    }
}
