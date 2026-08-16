package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.CommerceFinancePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link CommerceFinancePort} (v20260816.5).
 *
 * <p>No-op implementation that logs at INFO. Suitable for demo / test
 * deployments where the Finance module is not entitled or where commerce
 * orders should not yet flow into the general ledger.
 *
 * <p>A production deployment should provide a real implementation that
 * writes an accounts-receivable invoice (or revenue entry) for each paid
 * order.
 */
@Component
public class CommerceFinanceAdapter implements CommerceFinancePort {

    private static final Logger log = LoggerFactory.getLogger(CommerceFinanceAdapter.class);

    @Override
    public void recordOrder(UUID tenantId, UUID orderId) {
        log.info("recordOrder (no-op): tenant={}, orderId={}", tenantId, orderId);
    }

    @Override
    public String createInvoiceReference(UUID tenantId, UUID orderId) {
        String ref = "INV-" + orderId.toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("createInvoiceReference (no-op): tenant={}, orderId={} -> {}", tenantId, orderId, ref);
        return ref;
    }
}
