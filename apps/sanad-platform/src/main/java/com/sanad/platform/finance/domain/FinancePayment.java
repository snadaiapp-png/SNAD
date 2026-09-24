package com.sanad.platform.finance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Finance Payment — a payment record linked to an invoice.
 *
 * <p>State machine: PENDING → COMPLETED | FAILED | CANCELLED
 *                  COMPLETED → REFUNDED
 */
public record FinancePayment(
        UUID id,
        UUID tenantId,
        String paymentNumber,
        LocalDate paymentDate,
        PaymentMethod paymentMethod,
        java.math.BigDecimal amount,
        String currency,
        String referenceType,
        UUID referenceId,
        UUID invoiceId,
        Status status,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { PENDING, COMPLETED, FAILED, REFUNDED, CANCELLED }
    public enum PaymentMethod { CASH, BANK_TRANSFER, CREDIT_CARD, DEBIT_CARD, CHEQUE, ONLINE, OTHER }

    public static FinancePayment create(
            UUID tenantId, String paymentNumber, LocalDate paymentDate,
            PaymentMethod paymentMethod, java.math.BigDecimal amount, String currency,
            UUID invoiceId, String notes) {
        if (paymentNumber == null || paymentNumber.isBlank()) throw new IllegalArgumentException("paymentNumber must not be blank");
        if (paymentDate == null) throw new IllegalArgumentException("paymentDate must not be null");
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive");
        var now = Instant.now();
        return new FinancePayment(
                UUID.randomUUID(), tenantId, paymentNumber, paymentDate, paymentMethod,
                amount, currency != null ? currency : "SAR",
                null, null, invoiceId, Status.PENDING, notes,
                0, now, now
        );
    }

    public FinancePayment complete() {
        requireStatus(Status.PENDING, "complete");
        return withStatus(Status.COMPLETED);
    }

    public FinancePayment fail() {
        requireStatus(Status.PENDING, "fail");
        return withStatus(Status.FAILED);
    }

    public FinancePayment refund() {
        requireStatus(Status.COMPLETED, "refund");
        return withStatus(Status.REFUNDED);
    }

    private FinancePayment withStatus(Status newStatus) {
        return new FinancePayment(id, tenantId, paymentNumber, paymentDate, paymentMethod,
                amount, currency, referenceType, referenceId, invoiceId, newStatus, notes,
                version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
