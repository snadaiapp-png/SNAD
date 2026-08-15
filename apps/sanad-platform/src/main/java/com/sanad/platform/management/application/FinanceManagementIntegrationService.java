package com.sanad.platform.management.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only executive aggregation over the existing Finance module.
 * Finance remains the source of truth; this service only projects data for
 * Senior Management and never mutates Finance state.
 */
@Service
public class FinanceManagementIntegrationService {

    private final JdbcTemplate jdbc;

    public FinanceManagementIntegrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(UUID tenantId) {
        var overview = new HashMap<String, Object>();

        Integer invoiceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM finance_invoices WHERE tenant_id = ?",
                Integer.class, tenantId);
        overview.put("totalInvoices", invoiceCount != null ? invoiceCount : 0);

        BigDecimal invoiceTotal = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM finance_invoices WHERE tenant_id = ? AND status <> 'CANCELLED'",
                BigDecimal.class, tenantId);
        overview.put("invoiceTotalValue", invoiceTotal != null ? invoiceTotal : BigDecimal.ZERO);

        var invoiceStatuses = jdbc.queryForList(
                "SELECT status, COUNT(*) AS count FROM finance_invoices WHERE tenant_id = ? GROUP BY status ORDER BY status",
                tenantId);
        overview.put("invoiceStatusCounts", invoiceStatuses);

        Integer paymentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?",
                Integer.class, tenantId);
        overview.put("totalPayments", paymentCount != null ? paymentCount : 0);

        BigDecimal collected = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM finance_payments WHERE tenant_id = ? AND status = 'COMPLETED'",
                BigDecimal.class, tenantId);
        overview.put("collectedRevenue", collected != null ? collected : BigDecimal.ZERO);

        BigDecimal outstanding = jdbc.queryForObject(
                "SELECT COALESCE(SUM(GREATEST(total_amount - paid_amount, 0)), 0) " +
                        "FROM finance_invoices WHERE tenant_id = ? AND status <> 'CANCELLED'",
                BigDecimal.class, tenantId);
        overview.put("outstandingAmount", outstanding != null ? outstanding : BigDecimal.ZERO);

        var paymentStatuses = jdbc.queryForList(
                "SELECT status, COUNT(*) AS count FROM finance_payments WHERE tenant_id = ? GROUP BY status ORDER BY status",
                tenantId);
        overview.put("paymentStatusCounts", paymentStatuses);

        return overview;
    }
}
