package com.sanad.platform.subscription.read;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only Executive billing projection that keeps accounting truth in Finance.
 *
 * <p>{@code billing_invoices} remains the SCP compatibility/dunning projection.
 * This query only links that projection to R0C13 Finance, payment-attempt and
 * reconciliation evidence. It never mutates any billing, provider or Finance
 * table and never infers a successful accounting state from missing evidence.</p>
 */
@Service
public class ExecutiveBillingQueryService {

    public static final String ACCOUNTING_SOURCE_OF_TRUTH = "FINANCE";
    public static final String PROJECTION_SOURCE = "SCP_BILLING_PROJECTION";

    public record BillingRow(
            UUID id,
            UUID tenantId,
            String tenantName,
            UUID subscriptionId,
            String invoiceNumber,
            String projectionStatus,
            String currencyCode,
            long subtotalMinor,
            long creditAppliedMinor,
            long taxMinor,
            long totalMinor,
            long amountPaidMinor,
            long outstandingMinor,
            String description,
            Instant periodStart,
            Instant periodEnd,
            Instant dueAt,
            Instant paidAt,
            String paymentReference,
            UUID financeLinkId,
            UUID financeInvoiceId,
            String financeStatus,
            String settlementState,
            String reconciliationClassification,
            String reconciliationState,
            String accountingSourceOfTruth,
            String projectionSource,
            Instant createdAt
    ) {
    }

    private final JdbcTemplate jdbc;
    private final TenantRlsTransactionContext tenantRlsContext;

    public ExecutiveBillingQueryService(
            JdbcTemplate jdbc,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.tenantRlsContext = tenantRlsContext;
    }

    @Transactional(readOnly = true)
    public List<BillingRow> list(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }

        // The route is already control-plane authorized. The trusted route
        // tenant id is applied to FORCE-RLS R0C13/Finance tables here.
        tenantRlsContext.applyForCurrentTransaction(tenantId);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bi.id,
                       bi.tenant_id,
                       t.name AS tenant_name,
                       bi.subscription_id,
                       bi.invoice_number,
                       bi.status,
                       bi.currency_code,
                       bi.subtotal_minor,
                       bi.credit_applied_minor,
                       bi.tax_minor,
                       bi.total_minor,
                       bi.amount_paid_minor,
                       bi.description,
                       bi.period_start,
                       bi.period_end,
                       bi.due_at,
                       bi.paid_at,
                       bi.payment_reference,
                       bi.created_at,
                       fl.id AS finance_link_id,
                       fl.finance_invoice_id,
                       fi.status AS finance_status,
                       (
                           SELECT pa.payment_state
                           FROM subscription_billing_payment_attempts pa
                           WHERE pa.tenant_id = bi.tenant_id
                             AND pa.billing_invoice_id = bi.id
                           ORDER BY pa.updated_at DESC, pa.created_at DESC, pa.id DESC
                           LIMIT 1
                       ) AS settlement_state,
                       (
                           SELECT ri.classification
                           FROM subscription_billing_reconciliation_items ri
                           WHERE ri.tenant_id = bi.tenant_id
                             AND ri.billing_invoice_id = bi.id
                           ORDER BY ri.created_at DESC, ri.id DESC
                           LIMIT 1
                       ) AS reconciliation_classification,
                       (
                           SELECT ri.state
                           FROM subscription_billing_reconciliation_items ri
                           WHERE ri.tenant_id = bi.tenant_id
                             AND ri.billing_invoice_id = bi.id
                           ORDER BY ri.created_at DESC, ri.id DESC
                           LIMIT 1
                       ) AS reconciliation_state
                FROM billing_invoices bi
                JOIN tenants t ON t.id = bi.tenant_id
                LEFT JOIN subscription_billing_finance_links fl
                  ON fl.tenant_id = bi.tenant_id
                 AND fl.billing_invoice_id = bi.id
                LEFT JOIN finance_invoices fi
                  ON fi.tenant_id = fl.tenant_id
                 AND fi.id = fl.finance_invoice_id
                WHERE bi.tenant_id = ?
                ORDER BY bi.created_at DESC, bi.id DESC
                """, tenantId);

        return rows.stream().map(this::map).toList();
    }

    private BillingRow map(Map<String, Object> row) {
        long totalMinor = longValue(row.get("total_minor"));
        long paidMinor = longValue(row.get("amount_paid_minor"));
        UUID financeLinkId = uuid(row.get("finance_link_id"));
        UUID financeInvoiceId = uuid(row.get("finance_invoice_id"));
        String financeStatus = string(row.get("finance_status"));
        String settlementState = string(row.get("settlement_state"));
        String reconciliationState = string(row.get("reconciliation_state"));

        return new BillingRow(
                uuid(row.get("id")),
                uuid(row.get("tenant_id")),
                string(row.get("tenant_name")),
                uuid(row.get("subscription_id")),
                string(row.get("invoice_number")),
                string(row.get("status")),
                string(row.get("currency_code")),
                longValue(row.get("subtotal_minor")),
                longValue(row.get("credit_applied_minor")),
                longValue(row.get("tax_minor")),
                totalMinor,
                paidMinor,
                Math.max(totalMinor - paidMinor, 0L),
                string(row.get("description")),
                instant(row.get("period_start")),
                instant(row.get("period_end")),
                instant(row.get("due_at")),
                instant(row.get("paid_at")),
                string(row.get("payment_reference")),
                financeLinkId,
                financeInvoiceId,
                financeLinkId == null || financeInvoiceId == null
                        ? "UNLINKED"
                        : financeStatus == null ? "UNKNOWN" : financeStatus,
                settlementState == null ? "UNKNOWN" : settlementState,
                string(row.get("reconciliation_classification")),
                reconciliationState == null ? "UNRECONCILED" : reconciliationState,
                ACCOUNTING_SOURCE_OF_TRUTH,
                PROJECTION_SOURCE,
                instant(row.get("created_at")));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        throw new IllegalArgumentException("Unsupported timestamp value: " + value.getClass().getName());
    }
}
