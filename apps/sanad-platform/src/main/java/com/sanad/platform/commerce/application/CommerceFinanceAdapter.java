package com.sanad.platform.commerce.application;

import com.sanad.platform.commerce.domain.CommerceFinancePort;
import com.sanad.platform.finance.domain.FinanceInvoice;
import com.sanad.platform.finance.domain.FinanceInvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Production Commerce ↔ Finance adapter.
 *
 * <p>{@link #recordOrder(UUID, UUID)} idempotently creates and links an
 * ISSUED Finance invoice. {@link #markOrderSettled(UUID, UUID, BigDecimal)}
 * is the stronger paid-order invariant: it ensures the invoice exists, then
 * persists the actual paid amount and transitions the linked invoice to PAID.
 */
@Component
public class CommerceFinanceAdapter implements CommerceFinancePort {

    private static final Logger log = LoggerFactory.getLogger(CommerceFinanceAdapter.class);

    private final JdbcTemplate jdbc;
    private final FinanceInvoiceRepository invoiceRepo;

    public CommerceFinanceAdapter(JdbcTemplate jdbc, FinanceInvoiceRepository invoiceRepo) {
        this.jdbc = jdbc;
        this.invoiceRepo = invoiceRepo;
    }

    @Override
    public void recordOrder(UUID tenantId, UUID orderId) {
        String externalRef = externalReference(orderId);
        UUID existingInvoiceId = findLinkedInvoiceId(tenantId, orderId);
        if (existingInvoiceId != null) {
            log.info("recordOrder idempotent replay: tenant={} orderId={} already linked to invoiceId={}",
                    tenantId, orderId, existingInvoiceId);
            return;
        }

        OrderTotals order;
        try {
            order = jdbc.queryForObject(
                    "SELECT id, store_id, customer_reference, customer_snapshot, currency, "
                            + "subtotal, tax_total, grand_total "
                            + "FROM commerce_orders WHERE tenant_id = ? AND id = ?",
                    (rs, rowNum) -> new OrderTotals(
                            rs.getObject("id", UUID.class),
                            rs.getObject("store_id", UUID.class),
                            rs.getString("customer_reference"),
                            rs.getString("customer_snapshot"),
                            rs.getString("currency"),
                            rs.getBigDecimal("subtotal"),
                            rs.getBigDecimal("tax_total"),
                            rs.getBigDecimal("grand_total")),
                    tenantId, orderId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("recordOrder: commerce order not found tenant={} orderId={}", tenantId, orderId);
            return;
        }
        if (order == null) return;

        String invoiceNumber = generateInvoiceNumber(tenantId);
        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();
        Date issueDate = Date.valueOf(LocalDate.now(java.time.ZoneOffset.UTC));
        String customerName = extractCustomerName(order.customerSnapshot());

        jdbc.update("INSERT INTO finance_invoices (id, tenant_id, invoice_number, customer_type, "
                        + "customer_id, customer_name, issue_date, currency, subtotal, tax_amount, total_amount, "
                        + "paid_amount, status, notes, version, external_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'MANUAL', NULL, ?, ?, ?, ?, ?, ?, 0, 'ISSUED', ?, 0, ?, ?, ?)",
                invoiceId, tenantId, invoiceNumber,
                customerName != null ? customerName : "Commerce Order " + orderId.toString().substring(0, 8),
                issueDate,
                order.currency() != null ? order.currency() : "SAR",
                order.subtotal() != null ? order.subtotal() : BigDecimal.ZERO,
                order.taxTotal() != null ? order.taxTotal() : BigDecimal.ZERO,
                order.grandTotal() != null ? order.grandTotal() : BigDecimal.ZERO,
                "Auto-generated from commerce order " + orderId,
                externalRef,
                Timestamp.from(now), Timestamp.from(now));

        jdbc.update("INSERT INTO commerce_order_finance_links (id, tenant_id, commerce_order_id, finance_invoice_id, "
                        + "linked_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, orderId, invoiceId, Timestamp.from(now));

        log.info("recordOrder: created finance invoice {} for commerce order {} (tenant={})",
                invoiceId, orderId, tenantId);
    }

    @Override
    public void markOrderSettled(UUID tenantId, UUID orderId, BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.signum() <= 0) {
            throw new IllegalArgumentException("paidAmount must be positive");
        }

        UUID invoiceId = findLinkedInvoiceId(tenantId, orderId);
        if (invoiceId == null) {
            recordOrder(tenantId, orderId);
            invoiceId = findLinkedInvoiceId(tenantId, orderId);
        }
        if (invoiceId == null) {
            throw new IllegalStateException(
                    "Finance invoice could not be created/linked for commerce order " + orderId);
        }

        UUID resolvedInvoiceId = invoiceId;
        FinanceInvoice invoice = invoiceRepo.findById(tenantId, resolvedInvoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Linked Finance invoice not found: " + resolvedInvoiceId));

        if (invoice.status() == FinanceInvoice.Status.PAID) {
            if (invoice.paidAmount() != null && invoice.paidAmount().compareTo(paidAmount) == 0) {
                log.info("markOrderSettled idempotent replay: tenant={} orderId={} invoiceId={} amount={}",
                        tenantId, orderId, resolvedInvoiceId, paidAmount);
                return;
            }
            throw new IllegalStateException(
                    "Linked Finance invoice already PAID with a different amount for order " + orderId);
        }

        FinanceInvoice settled = invoice.markPaidWithAmount(paidAmount);
        invoiceRepo.save(settled);
        log.info("markOrderSettled: tenant={} orderId={} invoiceId={} paidAmount={}",
                tenantId, orderId, resolvedInvoiceId, settled.paidAmount());
    }

    @Override
    public String createInvoiceReference(UUID tenantId, UUID orderId) {
        UUID invoiceId = findLinkedInvoiceId(tenantId, orderId);
        if (invoiceId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT invoice_number FROM finance_invoices WHERE tenant_id = ? AND id = ?",
                    String.class, tenantId, invoiceId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID findLinkedInvoiceId(UUID tenantId, UUID orderId) {
        try {
            return jdbc.queryForObject(
                    "SELECT finance_invoice_id FROM commerce_order_finance_links "
                            + "WHERE tenant_id = ? AND commerce_order_id = ?",
                    UUID.class, tenantId, orderId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String externalReference(UUID orderId) {
        return "COMMERCE_ORDER:" + orderId;
    }

    private String generateInvoiceNumber(UUID tenantId) {
        String period = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        Long next = jdbc.queryForObject(
                """
                INSERT INTO finance_invoice_number_sequences (tenant_id, period, last_value)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, period) DO UPDATE
                    SET last_value = finance_invoice_number_sequences.last_value + 1,
                        updated_at = NOW()
                RETURNING last_value
                """,
                Long.class, tenantId, period);
        long n = next != null ? next : 1L;
        return "INV-" + period + "-" + String.format("%05d", n);
    }

    private String extractCustomerName(String customerSnapshotJson) {
        if (customerSnapshotJson == null || customerSnapshotJson.isBlank()) return null;
        int idx = customerSnapshotJson.indexOf("\"name\"");
        if (idx < 0) return null;
        int colon = customerSnapshotJson.indexOf(':', idx);
        if (colon < 0) return null;
        int startQuote = customerSnapshotJson.indexOf('"', colon + 1);
        if (startQuote < 0) return null;
        int endQuote = customerSnapshotJson.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return customerSnapshotJson.substring(startQuote + 1, endQuote);
    }

    private record OrderTotals(UUID id, UUID storeId, String customerReference,
                               String customerSnapshot, String currency,
                               BigDecimal subtotal, BigDecimal taxTotal,
                               BigDecimal grandTotal) {}
}
