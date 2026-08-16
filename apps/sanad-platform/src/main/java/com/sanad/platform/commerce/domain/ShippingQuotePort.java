package com.sanad.platform.commerce.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shipping quote port (v20260816.5).
 *
 * <p>Decouples commerce checkout from the concrete shipping / carrier
 * system. The default {@code NoShippingAdapter} returns a zero quote —
 * suitable for digital-only / service-only stores or for demo deployments.
 * A production deployment would plug in a real carrier API
 * (e.g. Aramex, SMSA, DHL, FedEx).
 */
public interface ShippingQuotePort {

    /**
     * Return a shipping quote for the given cart total.
     *
     * @param tenantId  the tenant owning the cart / order
     * @param storeId   the store the order belongs to
     * @param cartTotal the pre-shipping total (used to compute free-shipping thresholds, etc.)
     * @param currency  ISO-4217 currency code
     * @return the shipping amount (>= 0)
     */
    BigDecimal getQuote(UUID tenantId, UUID storeId, BigDecimal cartTotal, String currency);
}
