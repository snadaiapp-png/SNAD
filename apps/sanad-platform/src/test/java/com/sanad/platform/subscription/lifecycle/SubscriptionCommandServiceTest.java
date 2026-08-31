package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionCommandService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionCommandService — command execution")
class SubscriptionCommandServiceTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PlatformAuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubscriptionCommandService service;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString("c3000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new SubscriptionCommandService(jdbc, auditService, eventPublisher);
    }

    private void activeSubscriptionRow() {
        when(jdbc.queryForMap(
                eq("SELECT tenant_id, plan_id, status FROM tenant_subscriptions WHERE id = ?"),
                eq(SUBSCRIPTION_ID)))
                .thenReturn(Map.of(
                        "tenant_id", TENANT_ID,
                        "plan_id", PLAN_ID,
                        "status", "ACTIVE"));
    }

    @Test
    @DisplayName("suspend: legal transition updates status, writes ledger + audit, fires event")
    void suspendWritesEverything() {
        activeSubscriptionRow();

        SubscriptionCommandService.CommandResult result =
                service.execute(SUBSCRIPTION_ID, "SUSPEND", "policy violation", null, null);

        assertThat(result.toStatus()).isEqualTo("SUSPENDED");
        verify(jdbc).update(contains("UPDATE tenant_subscriptions SET status = 'SUSPENDED'"),
                eq(SUBSCRIPTION_ID));
        verify(jdbc).update(contains("INSERT INTO subscription_commands"),
                any(UUID.class), eq(SUBSCRIPTION_ID), eq(TENANT_ID), eq("SUSPEND"),
                eq("ACTIVE"), eq("SUSPENDED"), eq("policy violation"),
                eq(null), eq(null), any());
        verify(auditService).success(any(), eq(TENANT_ID), eq("SUBSCRIPTION_SUSPEND"),
                eq("subscription"), eq(SUBSCRIPTION_ID.toString()), eq("policy violation"),
                any(), any());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(events.capture());
        assertThat(events.getValue())
                .isInstanceOf(SubscriptionEntitlementListener.SubscriptionSuspendedEvent.class);
    }

    @Test
    @DisplayName("activate: fires the activation entitlement event")
    void activateFiresActivationEvent() {
        when(jdbc.queryForMap(
                eq("SELECT tenant_id, plan_id, status FROM tenant_subscriptions WHERE id = ?"),
                eq(SUBSCRIPTION_ID)))
                .thenReturn(Map.of(
                        "tenant_id", TENANT_ID,
                        "plan_id", PLAN_ID,
                        "status", "TRIAL"));

        service.execute(SUBSCRIPTION_ID, "ACTIVATE", null, null, null);

        verify(eventPublisher).publishEvent(
                any(SubscriptionEntitlementListener.SubscriptionActivatedEvent.class));
    }

    @Test
    @DisplayName("illegal transition is rejected and nothing is written")
    void illegalTransitionWritesNothing() {
        activeSubscriptionRow();

        assertThatThrownBy(() -> service.execute(SUBSCRIPTION_ID, "RESUME", null, null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(jdbc, never()).update(contains("UPDATE tenant_subscriptions"), (Object) any());
        verify(jdbc, never()).update(contains("INSERT INTO subscription_commands"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("unknown subscription is rejected")
    void unknownSubscriptionRejected() {
        when(jdbc.queryForMap(
                eq("SELECT tenant_id, plan_id, status FROM tenant_subscriptions WHERE id = ?"),
                eq(SUBSCRIPTION_ID)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.execute(SUBSCRIPTION_ID, "SUSPEND", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscription");
    }

    @Test
    @DisplayName("cancel sets cancelled_at alongside the status")
    void cancelSetsCancelledAt() {
        activeSubscriptionRow();

        SubscriptionCommandService.CommandResult result =
                service.execute(SUBSCRIPTION_ID, "CANCEL", "customer request", null, null);

        assertThat(result.toStatus()).isEqualTo("CANCELLED");
        verify(jdbc).update(contains("cancelled_at"), eq(SUBSCRIPTION_ID));
    }
}
