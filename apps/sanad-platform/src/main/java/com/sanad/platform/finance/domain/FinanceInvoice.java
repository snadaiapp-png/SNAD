package com.sanad.platform.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Finance Invoice — a customer invoice with line items.
 *
 * <p>State machine: DRAFT → ISSUED → PARTIALLY_PAID → PAID
 *                  DRAFT → CANCELLED
 *                  ISSUED → OVERDUE
 */
public record FinanceInvoice(
        UUID id,
        UUID tenantId,
        String invoiceNumber,
        String customerType,
        UUID customerId,
        String customerName,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        Status status,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status { DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }

    public static FinanceInvoice create(
            UUID tenantId, String invoiceNumber, String customerType,
            UUID customerId, String customerName,
            LocalDate issueDate, LocalDate dueDate, String currency,
            String notes) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) throw new IllegalArgumentException("invoiceNumber must not be blank");
        if (issueDate == null) throw new IllegalArgumentException("issueDate must not be null");
        var now = Instant.now();
        return new FinanceInvoice(
                UUID.randomUUID(), tenantId, invoiceNumber, customerType, customerId, customerName,
                issueDate, dueDate, currency != null ? currency : "SAR",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, Status.DRAFT, notes,
                0, now, now
        );
    }

    public FinanceInvoice issue() {
        requireStatus(Status.DRAFT, "issue");
        return withStatus(Status.ISSUED);
    }

    public FinanceInvoice cancel() {
        if (status == Status.PAID) throw new IllegalStateException("Cannot cancel PAID invoice");
        return withStatus(Status.CANCELLED);
    }

    /**
     * Legacy status-only transition. Prefer {@link #markPaidWithAmount(BigDecimal)}
     * whenever an actual settlement amount is available.
     */
    public FinanceInvoice markPaid() {
        requireStatus(Status.ISSUED, "markPaid");
        return withStatus(Status.PAID);
    }

    /**
     * Apply a full settlement while preserving the financial amount invariant:
     * PAID invoices must carry the actual amount that settled the invoice.
     */
    public FinanceInvoice markPaidWithAmount(BigDecimal amount) {
        requireStatus(Status.ISSUED, "markPaidWithAmount");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("paid amount must be positive");
        }
        if (totalAmount == null || amount.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "paid amount " + amount + " must equal invoice total " + totalAmount);
        }
        return new FinanceInvoice(id, tenantId, invoiceNumber, customerType, customerId, customerName,
                issueDate, dueDate, currency, subtotal, taxAmount, totalAmount, amount,
                Status.PAID, notes, version + 1, createdAt, Instant.now());
    }

    private FinanceInvoice withStatus(Status newStatus) {
        return new FinanceInvoice(id, tenantId, invoiceNumber, customerType, customerId, customerName,
                issueDate, dueDate, currency, subtotal, taxAmount, totalAmount, paidAmount,
                newStatus, notes, version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
