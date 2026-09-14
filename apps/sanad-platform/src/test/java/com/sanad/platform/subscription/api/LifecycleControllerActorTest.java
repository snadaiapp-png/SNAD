package com.sanad.platform.subscription.api;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleController — authenticated actor propagation")
class LifecycleControllerActorTest {

    @Mock private ControlPlaneAccessGuard accessGuard;
    @Mock private SubscriptionCommandService commandService;
    @Mock private SubscriptionChangeService changeService;
    @Mock private ProvisioningJobRunner provisioningRunner;
    @Mock private JdbcTemplate jdbc;

    private LifecycleController controller;

    private static final UUID SUBSCRIPTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID VERSION_ID =
            UUID.fromString("d1000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ACTOR_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000011");

    @BeforeEach
    void setUp() {
        controller = new LifecycleController(
                accessGuard, commandService, changeService, provisioningRunner, jdbc);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", ACTOR_TENANT_ID.toString(),
                "user_id", ACTOR_USER_ID.toString()));
        return authentication;
    }

    @Test
    @DisplayName("lifecycle command forwards actor IDs and Authentication")
    void lifecycleForwardsActor() {
        var authentication = authentication();
        when(commandService.execute(
                eq(SUBSCRIPTION_ID), eq("SUSPEND"), eq("policy"),
                eq(ACTOR_TENANT_ID), eq(ACTOR_USER_ID), same(authentication)))
                .thenReturn(new SubscriptionCommandService.CommandResult(
                        SUBSCRIPTION_ID, "SUSPEND", "ACTIVE", "SUSPENDED"));

        controller.executeCommand(
                SUBSCRIPTION_ID,
                "suspend",
                new LifecycleController.LifecycleCommandRequest("policy"),
                authentication);

        verify(accessGuard).require(authentication);
        verify(commandService).execute(
                SUBSCRIPTION_ID, "SUSPEND", "policy",
                ACTOR_TENANT_ID, ACTOR_USER_ID, authentication);
    }

    @Test
    @DisplayName("plan change forwards actor IDs to the command ledger path")
    void planChangeForwardsActor() {
        var authentication = authentication();
        when(changeService.execute(
                eq(SUBSCRIPTION_ID), eq(VERSION_ID), eq("GLOBAL"), eq("upgrade"),
                eq(ACTOR_TENANT_ID), eq(ACTOR_USER_ID)))
                .thenReturn(new SubscriptionChangeService.ChangeResult(
                        SUBSCRIPTION_ID, "EXECUTED", "upgrade"));

        controller.executeChange(
                SUBSCRIPTION_ID,
                new LifecycleController.ExecuteChangeRequest(VERSION_ID, null, "upgrade"),
                authentication);

        verify(accessGuard).require(authentication);
        verify(changeService).execute(
                SUBSCRIPTION_ID, VERSION_ID, "GLOBAL", "upgrade",
                ACTOR_TENANT_ID, ACTOR_USER_ID);
    }
}
