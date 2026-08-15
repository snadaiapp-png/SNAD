package com.sanad.platform.finance.infrastructure;

import com.sanad.platform.finance.domain.FinancePayment;
import com.sanad.platform.finance.domain.FinancePaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcFinancePaymentRepository implements FinancePaymentRepository {

    private final JdbcTemplate jdbc;

    public JdbcFinancePaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<FinancePayment> MAPPER = (rs, rowNum) -> new FinancePayment(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("payment_number"),
            rs.getDate("payment_date").toLocalDate(),
            FinancePayment.PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("reference_type"),
            rs.getObject("reference_id", UUID.class),
            rs.getObject("invoice_id", UUID.class),
            FinancePayment.Status.valueOf(rs.getString("status")),
            rs.getString("notes"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    public FinancePayment save(FinancePayment payment) {
        jdbc.update("""
                INSERT INTO finance_payments
                    (id, tenant_id, payment_number, payment_date, payment_method,
                     amount, currency, reference_type, reference_id, invoice_id,
                     status, notes, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    notes = EXCLUDED.notes,
                    version = EXCLUDED.version,
                    updated_at = EXCLUDED.updated_at
                """,
                payment.id(), payment.tenantId(), payment.paymentNumber(),
                Date.valueOf(payment.paymentDate()), payment.paymentMethod().name(),
                payment.amount(), payment.currency(),
                payment.referenceType(), payment.referenceId(), payment.invoiceId(),
                payment.status().name(), payment.notes(), payment.version(),
                Timestamp.from(payment.createdAt()), Timestamp.from(payment.updatedAt())
        );
        return payment;
    }

    @Override
    public Optional<FinancePayment> findById(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM finance_payments WHERE tenant_id = ? AND id = ?",
                MAPPER, tenantId, id).stream().findFirst();
    }

    @Override
    public List<FinancePayment> findByTenant(UUID tenantId, int limit) {
        return jdbc.query("SELECT * FROM finance_payments WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, tenantId, Math.max(1, Math.min(limit, 1000)));
    }

    @Override
    public List<FinancePayment> findByInvoice(UUID tenantId, UUID invoiceId) {
        return jdbc.query("SELECT * FROM finance_payments WHERE tenant_id = ? AND invoice_id = ? ORDER BY created_at DESC",
                MAPPER, tenantId, invoiceId);
    }

    @Override
    public long countCompletedThisMonth(UUID tenantId) {
        var monthStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
                .minus(Instant.now().atZone(java.time.ZoneOffset.UTC).getDayOfMonth() - 1, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS);
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ? AND status = 'COMPLETED' AND created_at >= ?",
                Long.class, tenantId, Timestamp.from(monthStart));
        return count != null ? count : 0L;
    }
}
