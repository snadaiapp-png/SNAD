package com.sanad.platform.module.entitlement;

import com.sanad.platform.module.registry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to subscription lifecycle events and triggers entitlement recalculation.
 *
 * <p>This component bridges the existing subscription engine (in
 * {@code SaasAdministrationService}) with the new {@link EntitlementResolver}.
 * When a subscription changes state (activation, upgrade, downgrade,
 * cancellation, suspension, resume), the entitlement cache is invalidated
 * and recomputed.
 *
 * <p>This component does NOT modify the existing subscription logic — it only
 * listens to events and triggers cache refresh.
 */
@Component
public class SubscriptionEntitlementListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementListener.class);

    private final EntitlementResolver entitlementResolver;

    public SubscriptionEntitlementListener(EntitlementResolver entitlementResolver) {
        this.entitlementResolver = entitlementResolver;
    }

    /**
     * Handle subscription activation.
     * Called when a new subscription is created and activated.
     */
    @EventListener
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info("Subscription activated for tenant {}, recalculating entitlements", event.tenantId());
        entitlementResolver.recalculateEntitlements(event.tenantId());
    }

    /**
     * Handle subscription plan change (upgrade or downgrade).
     */
    @EventListener
    public void onSubscriptionPlanChanged(SubscriptionPlanChangedEvent event) {
        log.info("Subscription plan changed for tenant {} ({} → {}), recalculating entitlements",
                event.tenantId(), event.oldPlanId(), event.newPlanId());
        entitlementResolver.recalculateEntitlements(event.tenantId());
    }

    /**
     * Handle subscription suspension.
     */
    @EventListener
    public void onSubscriptionSuspended(SubscriptionSuspendedEvent event) {
        log.info("Subscription suspended for tenant {}, recalculating entitlements", event.tenantId());
        entitlementResolver.recalculateEntitlements(event.tenantId());
    }

    /**
     * Handle subscription cancellation.
     */
    @EventListener
    public void onSubscriptionCancelled(SubscriptionCancelledEvent event) {
        log.info("Subscription cancelled for tenant {}, recalculating entitlements", event.tenantId());
        entitlementResolver.recalculateEntitlements(event.tenantId());
    }

    /**
     * Handle subscription resume.
     */
    @EventListener
    public void onSubscriptionResumed(SubscriptionResumedEvent event) {
        log.info("Subscription resumed for tenant {}, recalculating entitlements", event.tenantId());
        entitlementResolver.recalculateEntitlements(event.tenantId());
    }

    // === Event records ===

    public record SubscriptionActivatedEvent(UUID tenantId, UUID subscriptionId, UUID planId) {}

    public record SubscriptionPlanChangedEvent(UUID tenantId, UUID subscriptionId,
                                               UUID oldPlanId, UUID newPlanId) {}

    public record SubscriptionSuspendedEvent(UUID tenantId, UUID subscriptionId) {}

    public record SubscriptionCancelledEvent(UUID tenantId, UUID subscriptionId) {}

    public record SubscriptionResumedEvent(UUID tenantId, UUID subscriptionId) {}
}
