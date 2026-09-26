package com.sanad.platform.subscription.commercial;

import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService.EffectiveSubscription;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService.HistoricalSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.sanad.platform.subscription.commercial.TenantCommercialStateService.AccessDecision;
import static com.sanad.platform.subscription.commercial.TenantCommercialStateService.CommercialAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantCommercialStateServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private SubscriptionResolutionService resolution;
    private TenantCommercialStateService service;

    @BeforeEach
    void setUp() {
        resolution = mock(SubscriptionResolutionService.class);
        service = new TenantCommercialStateService(resolution);
    }

    @Test
    void activeTenantWithoutEffectiveSubscriptionFailsClosedAndCanCreateInitialSubscription() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.empty());
        when(resolution.findLatestHistorical(TENANT)).thenReturn(Optional.empty());

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.NO_EFFECTIVE_SUBSCRIPTION);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.CREATE_SUBSCRIPTION);
        assertThat(state.loginAllowed()).isFalse();
        assertThat(state.anomalyCode()).isEqualTo("ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION");
    }

    @Test
    void unknownSubscriptionStatusFailsClosed() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "FUTURE_STATUS", "CURRENT", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.BLOCKED_UNKNOWN_STATE);
        assertThat(state.loginAllowed()).isFalse();
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.BLOCKED);
        assertThat(state.anomalyCode()).isEqualTo("UNKNOWN_SUBSCRIPTION_STATUS");
    }

    @Test
    void activeSubscriptionAllowsLoginAndUpgrade() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "ACTIVE", "CURRENT", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.ACCESS_ALLOWED);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.UPGRADE);
        assertThat(state.loginAllowed()).isTrue();
        assertThat(state.effectiveSubscriptionId()).isEqualTo(SUB);
    }

    @Test
    void activeLifecycleWithPastDueBillingStateFailsClosed() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "ACTIVE", "PAST_DUE", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_PAST_DUE);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        assertThat(state.loginAllowed()).isFalse();
        assertThat(state.anomalyCode()).isEqualTo("BILLING_STATE_PAST_DUE");
    }

    @Test
    void activeLifecycleWithSuspendedBillingStateFailsClosed() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "ACTIVE", "SUSPENDED", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_SUSPENDED);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        assertThat(state.loginAllowed()).isFalse();
        assertThat(state.anomalyCode()).isEqualTo("BILLING_STATE_SUSPENDED");
    }

    @Test
    void activeLifecycleWithMissingOrTerminalBillingStateFailsClosed() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "ACTIVE", null, Instant.now())));
        var missing = service.resolve(TENANT, "ACTIVE");
        assertThat(missing.accessDecision()).isEqualTo(AccessDecision.BLOCKED_UNKNOWN_STATE);
        assertThat(missing.loginAllowed()).isFalse();
        assertThat(missing.anomalyCode()).isEqualTo("MISSING_BILLING_STATE");

        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "ACTIVE", "CANCELLED", Instant.now())));
        var terminal = service.resolve(TENANT, "ACTIVE");
        assertThat(terminal.accessDecision()).isEqualTo(AccessDecision.BLOCKED_UNKNOWN_STATE);
        assertThat(terminal.loginAllowed()).isFalse();
        assertThat(terminal.anomalyCode()).isEqualTo("BILLING_LIFECYCLE_MISMATCH");
    }

    @Test
    void trialSubscriptionAllowsLoginAndUpgradeForSupportedBillingShapes() {
        for (String billingState : new String[]{"TRIALING", "CURRENT"}) {
            when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                    new EffectiveSubscription(SUB, TENANT, PLAN, "TRIAL", billingState, Instant.now())));

            var state = service.resolve(TENANT, "ACTIVE");

            assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_TRIAL);
            assertThat(state.commercialAction()).isEqualTo(CommercialAction.UPGRADE);
            assertThat(state.loginAllowed()).isTrue();
        }
    }

    @Test
    void trialLifecycleHonorsRestrictiveBillingState() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "TRIAL", "SUSPENDED", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_SUSPENDED);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        assertThat(state.loginAllowed()).isFalse();
    }

    @Test
    void pastDueAndGracePeriodFailClosedForLogin() {
        for (String status : new String[]{"PAST_DUE", "GRACE_PERIOD"}) {
            when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                    new EffectiveSubscription(SUB, TENANT, PLAN, status, "PAST_DUE", Instant.now())));

            var state = service.resolve(TENANT, "ACTIVE");

            assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_PAST_DUE);
            assertThat(state.loginAllowed()).isFalse();
            assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        }
    }

    @Test
    void pausedAndSuspendedSubscriptionsFailClosed() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "PAUSED", "CURRENT", Instant.now())));
        var paused = service.resolve(TENANT, "ACTIVE");
        assertThat(paused.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_PAUSED);
        assertThat(paused.loginAllowed()).isFalse();

        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                new EffectiveSubscription(SUB, TENANT, PLAN, "SUSPENDED", "SUSPENDED", Instant.now())));
        var suspended = service.resolve(TENANT, "ACTIVE");
        assertThat(suspended.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_SUSPENDED);
        assertThat(suspended.loginAllowed()).isFalse();
    }

    @Test
    void pendingSubscriptionStatesFailClosed() {
        for (String status : new String[]{"DRAFT", "PENDING_ACTIVATION", "PENDING_PAYMENT"}) {
            when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
                    new EffectiveSubscription(SUB, TENANT, PLAN, status, "CURRENT", Instant.now())));

            var state = service.resolve(TENANT, "ACTIVE");

            assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_PENDING);
            assertThat(state.loginAllowed()).isFalse();
            assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        }
    }

    @Test
    void cancelledHistoryUsesResumeOnlySemantics() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.empty());
        when(resolution.findLatestHistorical(TENANT)).thenReturn(Optional.of(
                new HistoricalSubscription(SUB, "CANCELLED", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_TERMINAL);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.RESUME);
        assertThat(state.loginAllowed()).isFalse();
    }

    @Test
    void expiredHistoryUsesSuccessorSemantics() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.empty());
        when(resolution.findLatestHistorical(TENANT)).thenReturn(Optional.of(
                new HistoricalSubscription(SUB, "EXPIRED", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_TERMINAL);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.CREATE_SUCCESSOR);
        assertThat(state.loginAllowed()).isFalse();
    }

    @Test
    void terminatedHistoryIsBlocked() {
        when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.empty());
        when(resolution.findLatestHistorical(TENANT)).thenReturn(Optional.of(
                new HistoricalSubscription(SUB, "TERMINATED", Instant.now())));

        var state = service.resolve(TENANT, "ACTIVE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.SUBSCRIPTION_TERMINAL);
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.BLOCKED);
        assertThat(state.loginAllowed()).isFalse();
    }

    @Test
    void nonActiveTenantOperationalStatesAlwaysBlockLogin() {
        for (String tenantStatus : new String[]{"PENDING", "TRIAL", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED"}) {
            var state = service.resolve(TENANT, tenantStatus);

            assertThat(state.accessDecision()).isEqualTo(AccessDecision.TENANT_NOT_ACTIVE);
            assertThat(state.loginAllowed()).isFalse();
            assertThat(state.commercialAction()).isEqualTo(CommercialAction.NONE);
        }
    }

    @Test
    void unknownTenantStatusFailsClosed() {
        var state = service.resolve(TENANT, "FUTURE_TENANT_STATE");

        assertThat(state.accessDecision()).isEqualTo(AccessDecision.BLOCKED_UNKNOWN_STATE);
        assertThat(state.loginAllowed()).isFalse();
        assertThat(state.commercialAction()).isEqualTo(CommercialAction.BLOCKED);
        assertThat(state.anomalyCode()).isEqualTo("UNKNOWN_TENANT_STATUS");
    }
}
