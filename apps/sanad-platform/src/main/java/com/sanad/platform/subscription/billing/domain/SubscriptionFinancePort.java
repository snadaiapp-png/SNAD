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
}
