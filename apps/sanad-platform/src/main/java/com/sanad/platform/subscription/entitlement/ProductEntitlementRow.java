package com.sanad.platform.subscription.entitlement;

/**
 * A module entitlement row carried by a product (ADD_ON / METERED) attached to
 * a tenant's subscription item — read model over {@code subscription_items}
 * ⨝ {@code product_entitlements}.
 *
 * @param moduleEnabled  whether this product enables the module outright
 * @param capabilityCode nullable capability code being overridden
 * @param booleanValue   OR-merged into boolean capabilities
 * @param limitValue     max-merged into numeric limits
 * @param quotaValue     max-merged into quotas
 * @param quotaPeriod    DAILY | MONTHLY | YEARLY | TOTAL
 */
public record ProductEntitlementRow(
        boolean moduleEnabled,
        String capabilityCode,
        Boolean booleanValue,
        Long limitValue,
        Long quotaValue,
        String quotaPeriod) {
}
