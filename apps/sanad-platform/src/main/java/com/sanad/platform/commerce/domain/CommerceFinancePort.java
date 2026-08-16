package com.sanad.platform.commerce.domain;

import java.util.UUID;

/**
 * Commerce finance port (v20260816.5).
 *
 * <p>Decouples the commerce checkout flow from the Finance module so that
 * orders placed in a storefront can be recorded in the general ledger /
 * accounts-receivable subledger without coupling to the Finance service's
 * concrete types.
 *
 * <p>The default {@code CommerceFinanceAdapter} is a no-op that logs —
 * suitable for test / demo deployments where the Finance module is not
 * entitled. A production deployment would provide a real implementation
 * backed by the Finance module.
 */
public interface CommerceFinancePort {

    /**
     * Record a completed commerce order in the finance ledger
     * (e.g. as an accounts-receivable invoice or revenue entry).
     */
    void recordOrder(UUID tenantId, UUID orderId);

    /**
     * Create / resolve an invoice reference for the given order.
     * The returned reference should be stable for the lifetime of the order.
     */
    String createInvoiceReference(UUID tenantId, UUID orderId);
}
