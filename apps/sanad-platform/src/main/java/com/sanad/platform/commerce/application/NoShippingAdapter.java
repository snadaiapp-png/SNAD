package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.ShippingQuotePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link ShippingQuotePort} (v20260816.5).
 *
 * <p>Returns {@link BigDecimal#ZERO} for every cart — i.e. free shipping.
 * Suitable for digital-only / service-only stores or for demo deployments.
 *
 * <p>A production deployment should provide a real implementation backed by
 * a carrier API (e.g. Aramex, SMSA, DHL, FedEx) that returns live rates
 * based on the destination address, package weight, and dimensions.
 */
@Component
public class NoShippingAdapter implements ShippingQuotePort {

    @Override
    public BigDecimal getQuote(UUID tenantId, UUID storeId, BigDecimal cartTotal, String currency) {
        return BigDecimal.ZERO;
    }
}
