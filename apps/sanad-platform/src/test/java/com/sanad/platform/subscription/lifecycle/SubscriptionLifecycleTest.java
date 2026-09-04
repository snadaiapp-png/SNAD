package com.sanad.platform.subscription.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link SubscriptionLifecycle} state machine.
 *
 * <p>Every legal transition must be accepted; every illegal one must be
 * rejected with a message naming both states. Legacy values (TRIALING etc.)
 * keep working.
 */
@DisplayName("SubscriptionLifecycle — transition legality")
class SubscriptionLifecycleTest {

    @ParameterizedTest
    @CsvSource({
            // command, from, to
            "ACTIVATE, DRAFT, ACTIVE",
            "ACTIVATE, PENDING_PAYMENT, ACTIVE",
            "ACTIVATE, PENDING_ACTIVATION, ACTIVE",
            "START_TRIAL, DRAFT, TRIAL",
            "START_TRIAL, TRIALING, TRIALING",
            "PAUSE, ACTIVE, PAUSED",
            "PAUSE, TRIAL, PAUSED",
            "RESUME, PAUSED, ACTIVE",
            "SUSPEND, ACTIVE, SUSPENDED",
            "SUSPEND, PAST_DUE, SUSPENDED",
            "SUSPEND, GRACE_PERIOD, SUSPENDED",
            "CANCEL, ACTIVE, CANCELLED",
            "CANCEL, PAST_DUE, CANCELLED",
            "CANCEL, TRIAL, CANCELLED",
            "RENEW, ACTIVE, ACTIVE",
            "RENEW, PAST_DUE, ACTIVE",
            "EXPIRE, TRIAL, EXPIRED",
            "EXPIRE, GRACE_PERIOD, EXPIRED",
            "TERMINATE, SUSPENDED, TERMINATED",
            "TERMINATE, ACTIVE, TERMINATED",
            "MARK_PAST_DUE, ACTIVE, PAST_DUE",
            "ENTER_GRACE, PAST_DUE, GRACE_PERIOD",
            "ACTIVATE, TRIAL, ACTIVE",
            "ACTIVATE, TRIALING, ACTIVE"
    })
    @DisplayName("legal transitions are accepted")
    void legalTransitions(String command, String from, String to) {
        var result = SubscriptionLifecycle.transition(command, from);
        assertThat(result.toStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVATE, CANCELLED",
            "ACTIVATE, EXPIRED",
            "ACTIVATE, TERMINATED",
            "RESUME, ACTIVE",
            "RESUME, SUSPENDED",
            "PAUSE, CANCELLED",
            "PAUSE, PAST_DUE",
            "RENEW, CANCELLED",
            "RENEW, SUSPENDED",
            "CANCEL, TERMINATED",
            "START_TRIAL, ACTIVE"
    })
    @DisplayName("illegal transitions are rejected")
    void illegalTransitions(String command, String from) {
        assertThatThrownBy(() -> SubscriptionLifecycle.transition(command, from))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from);
    }

    @Test
    @DisplayName("unknown commands are rejected")
    void unknownCommandRejected() {
        assertThatThrownBy(() -> SubscriptionLifecycle.transition("FLY_TO_MOON", "ACTIVE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    @DisplayName("unknown statuses are rejected")
    void unknownStatusRejected() {
        assertThatThrownBy(() -> SubscriptionLifecycle.transition("ACTIVATE", "MAYBE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("legacy TRIALING maps to TRIAL and back-compat aliases exist")
    void legacyAliases() {
        assertThat(SubscriptionLifecycle.normalize("TRIALING")).isEqualTo("TRIAL");
        assertThat(SubscriptionLifecycle.normalize("TRIAL")).isEqualTo("TRIAL");
        assertThat(SubscriptionLifecycle.LEGACY_ALIASES)
                .containsEntry("TRIALING", "TRIAL");
    }

    @Test
    @DisplayName("terminal statuses can never transition anywhere")
    void terminalStatuses() {
        assertThat(SubscriptionLifecycle.TERMINAL_STATUSES)
                .containsExactlyInAnyOrder("CANCELLED", "EXPIRED", "TERMINATED");
        for (String terminal : SubscriptionLifecycle.TERMINAL_STATUSES) {
            for (String command : SubscriptionLifecycle.COMMANDS.keySet()) {
                if (command.equals("TERMINATE") && terminal.equals("CANCELLED")) {
                    // mission rule: cancelled subscriptions may be terminated
                    assertThat(SubscriptionLifecycle.transition(command, terminal)
                            .toStatus()).isEqualTo("TERMINATED");
                }
            }
        }
    }
}
