package com.sanad.platform.subscription.commercial;

import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService.EffectiveSubscription;
import com.sanad.platform.subscription.lifecycle.SubscriptionResolutionService.HistoricalSubscription;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical derived commercial-state policy for Executive tenant operations.
 *
 * <p>This service deliberately does not mirror subscription lifecycle into
 * {@code tenants.status}. It resolves the unique effective subscription and
 * derives access/action decisions from the two bounded contexts. Historical
 * terminal rows are used only for continuation semantics.</p>
 */
@Service
public class TenantCommercialStateService {

    private static final Set<String> KNOWN_TENANT_STATUSES = Set.of(
            "PENDING", "TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED", "ARCHIVED");
    private static final Set<String> KNOWN_BILLING_STATES = Set.of(
            "TRIALING", "CURRENT", "PAST_DUE", "SUSPENDED", "CANCELLED");

    private final SubscriptionResolutionService resolution;

    public TenantCommercialStateService(SubscriptionResolutionService resolution) {
        this.resolution = resolution;
    }

    public enum AccessDecision {
        ACCESS_ALLOWED,
        TENANT_NOT_ACTIVE,
        NO_EFFECTIVE_SUBSCRIPTION,
        SUBSCRIPTION_TRIAL,
        SUBSCRIPTION_PAST_DUE,
        SUBSCRIPTION_SUSPENDED,
        SUBSCRIPTION_PAUSED,
        SUBSCRIPTION_PENDING,
        SUBSCRIPTION_TERMINAL,
        AMBIGUOUS_EFFECTIVE_SUBSCRIPTION,
        BLOCKED_UNKNOWN_STATE
    }

    public enum CommercialAction {
        UPGRADE,
        RESUME,
        CREATE_SUBSCRIPTION,
        CREATE_SUCCESSOR,
        NONE,
        BLOCKED
    }

    public record TenantCommercialState(
            UUID tenantId,
            String tenantStatus,
            UUID effectiveSubscriptionId,
            String effectiveSubscriptionStatus,
            String billingState,
            AccessDecision accessDecision,
            CommercialAction commercialAction,
            String anomalyCode,
            boolean loginAllowed
    ) {
    }

    public TenantCommercialState resolve(UUID tenantId, String tenantStatus) {
        String normalizedTenantStatus = normalize(tenantStatus);
        if (!KNOWN_TENANT_STATUSES.contains(normalizedTenantStatus)) {
            return state(tenantId, normalizedTenantStatus, null,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "UNKNOWN_TENANT_STATUS", false);
        }

        if (!"ACTIVE".equals(normalizedTenantStatus)) {
            return state(tenantId, normalizedTenantStatus, null,
                    AccessDecision.TENANT_NOT_ACTIVE, CommercialAction.NONE,
                    "TENANT_NOT_ACTIVE", false);
        }

        Optional<EffectiveSubscription> effective = resolution.findEffectiveSubscription(tenantId);
        if (effective.isPresent()) {
            return fromEffective(tenantId, normalizedTenantStatus, effective.get());
        }

        Optional<HistoricalSubscription> latest = resolution.findLatestHistorical(tenantId);
        if (latest.isEmpty()) {
            return state(tenantId, normalizedTenantStatus, null,
                    AccessDecision.NO_EFFECTIVE_SUBSCRIPTION, CommercialAction.CREATE_SUBSCRIPTION,
                    "ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION", false);
        }

        HistoricalSubscription historical = latest.get();
        String historicalStatus = normalize(historical.status());
        CommercialAction action = switch (historicalStatus) {
            case "CANCELLED" -> CommercialAction.RESUME;
            case "EXPIRED" -> CommercialAction.CREATE_SUCCESSOR;
            case "TERMINATED" -> CommercialAction.BLOCKED;
            default -> CommercialAction.BLOCKED;
        };
        String anomaly = switch (historicalStatus) {
            case "CANCELLED" -> "CANCELLED_HISTORY_WITHOUT_EFFECTIVE_SUBSCRIPTION";
            case "EXPIRED" -> "EXPIRED_HISTORY_WITHOUT_EFFECTIVE_SUBSCRIPTION";
            case "TERMINATED" -> "TERMINATED_HISTORY_WITHOUT_EFFECTIVE_SUBSCRIPTION";
            default -> "NON_TERMINAL_HISTORY_WITHOUT_EFFECTIVE_SUBSCRIPTION";
        };

        return new TenantCommercialState(
                tenantId,
                normalizedTenantStatus,
                null,
                historicalStatus,
                null,
                AccessDecision.SUBSCRIPTION_TERMINAL,
                action,
                anomaly,
                false);
    }

    private TenantCommercialState fromEffective(
            UUID tenantId,
            String tenantStatus,
            EffectiveSubscription subscription
    ) {
        String status = normalize(subscription.status());
        String billingState = normalize(subscription.billingState());

        if (billingState.isEmpty()) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "MISSING_BILLING_STATE", false);
        }
        if (!KNOWN_BILLING_STATES.contains(billingState)) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "UNKNOWN_BILLING_STATE", false);
        }
        if ("CANCELLED".equals(billingState)) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "BILLING_LIFECYCLE_MISMATCH", false);
        }
        if ("SUSPENDED".equals(billingState)) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_SUSPENDED, CommercialAction.NONE,
                    "BILLING_STATE_SUSPENDED", false);
        }
        if ("PAST_DUE".equals(billingState)) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_PAST_DUE, CommercialAction.NONE,
                    "BILLING_STATE_PAST_DUE", false);
        }
        if ("TRIALING".equals(billingState)
                && !Set.of("TRIAL", "TRIALING").contains(status)) {
            return state(tenantId, tenantStatus, subscription,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "BILLING_LIFECYCLE_MISMATCH", false);
        }

        return switch (status) {
            case "ACTIVE" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.ACCESS_ALLOWED, CommercialAction.UPGRADE, null, true);
            case "TRIAL", "TRIALING" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_TRIAL, CommercialAction.UPGRADE, null, true);
            case "PAST_DUE", "GRACE_PERIOD" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_PAST_DUE, CommercialAction.NONE,
                    "SUBSCRIPTION_PAYMENT_RESTRICTED", false);
            case "PAUSED" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_PAUSED, CommercialAction.NONE,
                    "SUBSCRIPTION_PAUSED", false);
            case "SUSPENDED" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_SUSPENDED, CommercialAction.NONE,
                    "SUBSCRIPTION_SUSPENDED", false);
            case "DRAFT", "PENDING_ACTIVATION", "PENDING_PAYMENT" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_PENDING, CommercialAction.NONE,
                    "SUBSCRIPTION_PENDING", false);
            case "CANCELLED", "EXPIRED", "TERMINATED" -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.SUBSCRIPTION_TERMINAL, CommercialAction.BLOCKED,
                    "TERMINAL_SUBSCRIPTION_RESOLVED_AS_EFFECTIVE", false);
            default -> state(tenantId, tenantStatus, subscription,
                    AccessDecision.BLOCKED_UNKNOWN_STATE, CommercialAction.BLOCKED,
                    "UNKNOWN_SUBSCRIPTION_STATUS", false);
        };
    }

    private static TenantCommercialState state(
            UUID tenantId,
            String tenantStatus,
            EffectiveSubscription subscription,
            AccessDecision decision,
            CommercialAction action,
            String anomalyCode,
            boolean loginAllowed
    ) {
        return new TenantCommercialState(
                tenantId,
                tenantStatus,
                subscription == null ? null : subscription.id(),
                subscription == null ? null : normalize(subscription.status()),
                subscription == null ? null : subscription.billingState(),
                decision,
                action,
                anomalyCode,
                loginAllowed);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
