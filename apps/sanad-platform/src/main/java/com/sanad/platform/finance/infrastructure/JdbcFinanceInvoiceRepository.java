package com.sanad.platform.finance.infrastructure;

import com.sanad.platform.finance.domain.FinanceInvoice;
import com.sanad.platform.finance.domain.FinanceInvoiceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcFinanceInvoiceRepository implements FinanceInvoiceRepository {

    private final JdbcTemplate jdbc;

    public JdbcFinanceInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<FinanceInvoice> MAPPER = (rs, rowNum) -> new FinanceInvoice(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("invoice_number"),
            rs.getString("customer_type"),
            rs.getObject("customer_id", UUID.class),
            rs.getString("customer_name"),
            rs.getDate("issue_date").toLocalDate(),
            rs.getDate("due_date") != null ? rs.getDate("due_date").toLocalDate() : null,
            rs.getString("currency"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("tax_amount"),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("paid_amount"),
            FinanceInvoice.Status.valueOf(rs.getString("status")),
            rs.getString("notes"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public FinanceInvoice save(FinanceInvoice invoice) {
        jdbc.update("""
                INSERT INTO finance_invoices
                    (id, tenant_id, invoice_number, customer_type, customer_id, customer_name,
                     issue_date, due_date, currency, subtotal, tax_amount, total_amount,
                     paid_amount, status, notes, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    customer_name = EXCLUDED.customer_name,
                    due_date = EXCLUDED.due_date,
                    subtotal = EXCLUDED.subtotal,
                    tax_amount = EXCLUDED.tax_amount,
                    total_amount = EXCLUDED.total_amount,
                    paid_amount = EXCLUDED.paid_amount,
                    status = EXCLUDED.status,
                    notes = EXCLUDED.notes,
                    version = EXCLUDED.version,
                    updated_at = EXCLUDED.updated_at
                """,
                invoice.id(), invoice.tenantId(), invoice.invoiceNumber(),
                invoice.customerType(), invoice.customerId(), invoice.customerName(),
                Date.valueOf(invoice.issueDate()),
                invoice.dueDate() != null ? Date.valueOf(invoice.dueDate()) : null,
                invoice.currency(), invoice.subtotal(), invoice.taxAmount(),
                invoice.totalAmount(), invoice.paidAmount(),
                invoice.status().name(), invoice.notes(), invoice.version(),
                Timestamp.from(invoice.createdAt()), Timestamp.from(invoice.updatedAt())
        );
        return invoice;
    }

    @Override
    public Optional<FinanceInvoice> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM finance_invoices WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public Optional<FinanceInvoice> findByNumber(UUID tenantId, String invoiceNumber) {
        return jdbc.query("SELECT * FROM finance_invoices WHERE tenant_id = ? AND invoice_number = ?",
                MAPPER, tenantId, invoiceNumber).stream().findFirst();
    }

    @Override
    public List<FinanceInvoice> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM finance_invoices WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<FinanceInvoice> findByTenantAndStatus(UUID tenantId, FinanceInvoice.Status status, int limit) {
        return jdbc.query("SELECT * FROM finance_invoices WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, status.name(), Math.max(1, Math.min(limit, 1000)));
    }
}
