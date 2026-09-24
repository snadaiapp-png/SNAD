package com.sanad.platform.finance.domain;

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
        java.math.BigDecimal subtotal,
        java.math.BigDecimal taxAmount,
        java.math.BigDecimal totalAmount,
        java.math.BigDecimal paidAmount,
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
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, Status.DRAFT, notes,
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

    public FinanceInvoice markPaid() {
        requireStatus(Status.ISSUED, "markPaid");
        return withStatus(Status.PAID);
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
