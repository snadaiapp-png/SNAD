package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifecycleControllerCommandBoundaryTest {

    @Mock private ControlPlaneAccessGuard accessGuard;
    @Mock private SubscriptionCommandService commandService;
    @Mock private SubscriptionChangeService changeService;
    @Mock private ProvisioningJobRunner provisioningRunner;
    @Mock private JdbcTemplate jdbc;
    @Mock private Authentication authentication;

    private LifecycleController controller;

    private static final UUID SUBSCRIPTION_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID ACTOR_TENANT =
            UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID ACTOR_USER =
            UUID.fromString("00000000-0000-4000-8000-000000000202");

    @BeforeEach
    void setUp() {
        controller = new LifecycleController(
                accessGuard, commandService, changeService, provisioningRunner, jdbc);
        when(authentication.getDetails()).thenReturn(Map.of(
                "tenant_id", ACTOR_TENANT.toString(),
                "user_id", ACTOR_USER.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ACTIVATE", "START_TRIAL", "RENEW", "CANCEL", "RESUME", "SCHEDULE_CANCELLATION",
            "MARK_PAST_DUE", "ENTER_GRACE", "REQUEST_ACTIVATION", "PAYMENT_RECEIVED", "EXPIRE"
    })
    void governedCommandsFailClosedOnGenericEndpoint(String command) {
        assertThatThrownBy(() -> controller.executeCommand(
                SUBSCRIPTION_ID,
                command,
                new LifecycleController.LifecycleCommandRequest("operator request"),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(commandService, never()).execute(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelledResumeMustUseGovernedResumePath() {
        assertThatThrownBy(() -> controller.executeCommand(
                SUBSCRIPTION_ID,
                "RESUME",
                new LifecycleController.LifecycleCommandRequest("resume"),
                authentication))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(commandService, never()).execute(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void safeOperatorSuspendStillDelegatesToCanonicalCommandService() {
        var expected = new SubscriptionCommandService.CommandResult(
                SUBSCRIPTION_ID, "SUSPEND", "ACTIVE", "SUSPENDED");
        when(commandService.execute(
                SUBSCRIPTION_ID, "SUSPEND", "policy",
                ACTOR_TENANT, ACTOR_USER, authentication))
                .thenReturn(expected);

        var response = controller.executeCommand(
                SUBSCRIPTION_ID,
                "suspend",
                new LifecycleController.LifecycleCommandRequest("policy"),
                authentication);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(commandService).execute(
                SUBSCRIPTION_ID, "SUSPEND", "policy",
                ACTOR_TENANT, ACTOR_USER, authentication);
    }
}
