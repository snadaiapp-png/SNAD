package com.sanad.platform.subscription.billing.domain;

import java.util.UUID;

/**
 * R0C13 subscription-billing -> Finance integration boundary.
 *
 * <p>The subscription bounded context speaks only in integer minor units.
 * Finance owns accounting invoices/payments and their major-unit persistence.
 * Implementations must be idempotent and tenant-safe.</p>
 */
public interface SubscriptionFinancePort {

    FinanceInvoiceLink ensureInvoice(UUID tenantId, UUID billingInvoiceId);

    SettlementLink recordSettlement(
            UUID tenantId,
            UUID billingInvoiceId,
            UUID settlementId,
            long amountMinor,
            String currencyCode
    );

    /**
     * R13-G07.0 additive Finance-owned refund authority. Finance transitions
     * its own authoritative settlement payment COMPLETED -> REFUNDED; the
     * subscription bounded context never writes Finance tables directly.
     * Full-refund semantics only in G07.0 — partial refunds have no
     * accounting model yet and must fail closed. Implementations must be
     * idempotent and tenant-safe.
     */
    RefundLink recordRefund(
            UUID tenantId,
            UUID billingInvoiceId,
            UUID settlementId,
            long amountMinor,
            String currencyCode
    );

    record FinanceInvoiceLink(
            UUID billingInvoiceId,
            UUID financeInvoiceId,
            String externalReference,
            String financeInvoiceNumber
    ) {}

    record SettlementLink(
            UUID billingInvoiceId,
            UUID financeInvoiceId,
            UUID financePaymentId,
            String paymentNumber
    ) {}

    record RefundLink(
            UUID billingInvoiceId,
            UUID financeInvoiceId,
            UUID financePaymentId,
            String paymentNumber
    ) {}
}
