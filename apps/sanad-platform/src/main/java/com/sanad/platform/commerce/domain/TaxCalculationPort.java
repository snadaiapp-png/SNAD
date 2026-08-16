package com.sanad.platform.commerce.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tax calculation port (v20260816.5).
 *
 * <p>Decouples commerce checkout from the concrete tax engine. The default
 * {@code SimpleTaxAdapter} returns a flat 15% VAT (the Saudi standard rate)
 * — suitable for demo / single-region deployments. A production deployment
 * would plug in a real engine (e.g. ZATCA e-inicing compliant calculator,
 * Avalara, or a custom multi-jurisdiction rules engine).
 */
public interface TaxCalculationPort {

    /**
     * Calculate the tax for a given cart subtotal.
     *
     * @param tenantId  the tenant owning the cart / order
     * @param storeId   the store the order belongs to
     * @param subtotal  the pre-tax subtotal of the order (major currency units)
     * @param currency  ISO-4217 currency code
     * @return the tax amount (>= 0)
     */
    BigDecimal calculateTax(UUID tenantId, UUID storeId, BigDecimal subtotal, String currency);
}
