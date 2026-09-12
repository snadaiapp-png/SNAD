package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security/business-invariant regression gate for the generic lifecycle route.
 *
 * <p>The lifecycle state table contains internal commands used by billing,
 * provisioning and renewal orchestration. The generic operator endpoint must
 * never expose those primitives directly, otherwise an EXECUTIVE_MANAGE caller
 * could manufacture state without the side effects that make that state true
 * (for example PAYMENT_RECEIVED without a payment, or ACTIVATE without
 * provisioning).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleController — governed command boundary")
class LifecycleControllerCommandBoundaryTest {

    @Mock private ControlPlaneAccessGuard accessGuard;
    @Mock private SubscriptionCommandService commandService;
    @Mock private SubscriptionChangeService changeService;
    @Mock private ProvisioningJobRunner provisioningRunner;
    @Mock private JdbcTemplate jdbc;
    @Mock private Authentication authentication;

    private LifecycleController controller;
    private static final UUID SUBSCRIPTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");

    @BeforeEach
    void setUp() {
        controller = new LifecycleController(
                accessGuard, commandService, changeService, provisioningRunner, jdbc);
    }

    @ParameterizedTest(name = "{0} is routed away from the generic endpoint")
    @ValueSource(strings = {
            "ACTIVATE",
            "START_TRIAL",
            "RENEW",
            "SCHEDULE_CANCELLATION",
            "MARK_PAST_DUE",
            "ENTER_GRACE",
            "REQUEST_ACTIVATION",
            "PAYMENT_RECEIVED"
    })
    void governedCommandsFailClosed(String command) {
        assertThatThrownBy(() -> controller.executeCommand(
                SUBSCRIPTION_ID, command,
                new LifecycleController.LifecycleCommandRequest("operator request"),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(commandService, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("PAYMENT_RECEIVED cannot reactivate a subscription through the operator route")
    void paymentReceivedCannotBypassBilling() {
        assertThatThrownBy(() -> controller.executeCommand(
                SUBSCRIPTION_ID, "payment_received",
                new LifecycleController.LifecycleCommandRequest("manual payment override"),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BillingStateService");

        verify(commandService, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("CANCELLED resume is routed to the governed resume service")
    void cancelledResumeCannotBypassPeriodAndBillingSideEffects() {
        when(jdbc.queryForObject(
                eq("SELECT status FROM tenant_subscriptions WHERE id = ?"),
                eq(String.class), eq(SUBSCRIPTION_ID)))
                .thenReturn("CANCELLED");

        assertThatThrownBy(() -> controller.executeCommand(
                SUBSCRIPTION_ID, "RESUME",
                new LifecycleController.LifecycleCommandRequest("resume"),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("governed resume endpoint");

        verify(commandService, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("PAUSED resume remains a direct lifecycle transition")
    void pausedResumeStillDelegates() {
        when(jdbc.queryForObject(
                eq("SELECT status FROM tenant_subscriptions WHERE id = ?"),
                eq(String.class), eq(SUBSCRIPTION_ID)))
                .thenReturn("PAUSED");
        SubscriptionCommandService.CommandResult expected =
                new SubscriptionCommandService.CommandResult(
                        SUBSCRIPTION_ID, "RESUME", "PAUSED", "ACTIVE");
        when(commandService.execute(SUBSCRIPTION_ID, "RESUME", "resume"))
                .thenReturn(expected);

        var response = controller.executeCommand(
                SUBSCRIPTION_ID, "resume",
                new LifecycleController.LifecycleCommandRequest("resume"),
                authentication);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(commandService).execute(SUBSCRIPTION_ID, "RESUME", "resume");
    }

    @Test
    @DisplayName("safe operator suspension still delegates to the canonical command service")
    void suspendStillDelegates() {
        SubscriptionCommandService.CommandResult expected =
                new SubscriptionCommandService.CommandResult(
                        SUBSCRIPTION_ID, "SUSPEND", "ACTIVE", "SUSPENDED");
        when(commandService.execute(SUBSCRIPTION_ID, "SUSPEND", "policy"))
                .thenReturn(expected);

        var response = controller.executeCommand(
                SUBSCRIPTION_ID, "suspend",
                new LifecycleController.LifecycleCommandRequest("policy"),
                authentication);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(commandService).execute(SUBSCRIPTION_ID, "SUSPEND", "policy");
    }
}
