package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.TaxCalculationPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Default {@link TaxCalculationPort} (v20260816.5).
 *
 * <p>Returns a flat 15% VAT (the Saudi standard rate). Suitable for demo /
 * single-region deployments in KSA. A production deployment should provide
 * a real implementation that respects product tax category, customer
 * jurisdiction, and ZATCA e-invoicing rules.
 */
@Component
public class SimpleTaxAdapter implements TaxCalculationPort {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.15");

    @Override
    public BigDecimal calculateTax(UUID tenantId, UUID storeId, BigDecimal subtotal, String currency) {
        if (subtotal == null || subtotal.signum() <= 0) return BigDecimal.ZERO;
        return subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
