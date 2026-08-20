package com.sanad.platform.commerce.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commerce finance port.
 *
 * <p>Decouples Commerce from Finance while preserving the financial
 * system-of-record invariants required by checkout and settlement flows.
 */
public interface CommerceFinancePort {

    /**
     * Idempotently record a commerce order in Finance and persist the
     * commerce-order ↔ finance-invoice linkage. The resulting invoice is
     * issued, but not necessarily paid.
     */
    void recordOrder(UUID tenantId, UUID orderId);

    /**
     * Idempotently settle the Finance invoice linked to a paid commerce order.
     * Implementations must create/link the invoice when needed, persist the
     * actual paid amount, transition the invoice to PAID, and reject a replay
     * that attempts to settle the same invoice with a different amount.
     */
    void markOrderSettled(UUID tenantId, UUID orderId, BigDecimal paidAmount);

    /**
     * Create / resolve an invoice reference for the given order.
     * The returned reference is stable for the lifetime of the order.
     */
    String createInvoiceReference(UUID tenantId, UUID orderId);
}
