package com.sanad.platform.subscription.billing.application;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * R13-G06 read-only-by-default billing reconciliation.
 *
 * <p>"Read-only" here means no mutation of provider, Finance, SCP billing
 * projection or subscription lifecycle state. The service writes only
 * reconciliation run/item evidence, which is intentionally tenant-scoped,
 * FORCE-RLS protected and idempotent.</p>
 */
@Service
public class BillingReconciliationService {

    private final JdbcTemplate jdbc;
    private final BillingPaymentProvider provider;
    private final TenantRlsTransactionContext tenantRlsContext;

    public BillingReconciliationService(
            JdbcTemplate jdbc,
            BillingPaymentProvider provider,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.tenantRlsContext = tenantRlsContext;
    }

    @Transactional
    public ReconciliationResult reconcileReadOnly(
            UUID tenantId,
            String idempotencyKey,
            UUID requestedBy
    ) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey is too long");
        }

        tenantRlsContext.applyForCurrentTransaction(tenantId);

        UUID existingRunId = jdbc.query(
                "SELECT id FROM subscription_billing_reconciliation_runs "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                tenantId, key);
        if (existingRunId != null) {
            return loadResult(tenantId, existingRunId, true);
        }

        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO subscription_billing_reconciliation_runs "
                        + "(id, tenant_id, idempotency_key, mode, state, requested_by, started_at, summary_metadata) "
                        + "VALUES (?, ?, ?, 'READ_ONLY', 'RUNNING', ?, ?, '{}'::jsonb)",
                runId, tenantId, key, requestedBy, Timestamp.from(now));

        List<AttemptView> attempts = jdbc.query(
                "SELECT pa.id AS payment_attempt_id, pa.billing_invoice_id, pa.provider_payment_ref, "
                        + "pa.amount_minor, pa.currency_code, pa.state AS payment_state, "
                        + "bi.total_minor AS billing_total_minor, bi.currency_code AS billing_currency, "
                        + "bi.status AS billing_status, "
                        + "fl.id AS finance_link_id, fi.id AS finance_invoice_id, "
                        + "fi.status AS finance_status "
                        + "FROM subscription_billing_payment_attempts pa "
                        + "JOIN billing_invoices bi "
                        + "ON bi.tenant_id = pa.tenant_id AND bi.id = pa.billing_invoice_id "
                        + "LEFT JOIN subscription_billing_finance_links fl "
                        + "ON fl.tenant_id = pa.tenant_id AND fl.billing_invoice_id = pa.billing_invoice_id "
                        + "LEFT JOIN finance_invoices fi "
                        + "ON fi.tenant_id = fl.tenant_id AND fi.id = fl.finance_invoice_id "
                        + "WHERE pa.tenant_id = ? ORDER BY pa.created_at, pa.id",
                (rs, rowNum) -> new AttemptView(
                        rs.getObject("payment_attempt_id", UUID.class),
                        rs.getObject("billing_invoice_id", UUID.class),
                        rs.getString("provider_payment_ref"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency_code"),
                        rs.getString("payment_state"),
                        rs.getLong("billing_total_minor"),
                        rs.getString("billing_currency"),
                        rs.getString("billing_status"),
                        rs.getObject("finance_link_id", UUID.class),
                        rs.getObject("finance_invoice_id", UUID.class),
                        rs.getString("finance_status")),
                tenantId);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AttemptView attempt : attempts) {
            Classification classification = classify(tenantId, attempt);
            counts.merge(classification.name(), 1, Integer::sum);
            insertItem(tenantId, runId, attempt, classification);
        }

        int matched = counts.getOrDefault(Classification.MATCHED.name(), 0);
        int total = attempts.size();
        int discrepancies = total - matched;
        jdbc.update(
                "UPDATE subscription_billing_reconciliation_runs "
                        + "SET state = 'COMPLETED', completed_at = ?, "
                        + "summary_metadata = jsonb_build_object("
                        + "'total', ?, 'matched', ?, 'discrepancies', ?) "
                        + "WHERE tenant_id = ? AND id = ? AND state = 'RUNNING'",
                Timestamp.from(Instant.now()), total, matched, discrepancies, tenantId, runId);

        return new ReconciliationResult(runId, false, total, Map.copyOf(counts));
    }

    private Classification classify(UUID tenantId, AttemptView attempt) {
        if (attempt.providerPaymentRef() == null || attempt.providerPaymentRef().isBlank()) {
            return Classification.MISSING_PROVIDER_REFERENCE;
        }

        BillingPaymentProvider.PaymentState providerState;
        try {
            providerState = provider.queryPaymentState(
                    new BillingPaymentProvider.QueryPaymentStateQuery(
                            tenantId, attempt.providerPaymentRef()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Classification.MISSING_PROVIDER_REFERENCE;
        }

        if (providerState.amountMinor() != attempt.amountMinor()
                || providerState.amountMinor() != attempt.billingTotalMinor()) {
            return Classification.AMOUNT_MISMATCH;
        }

        String providerCurrency = normalizeCurrency(providerState.currencyCode());
        if (!providerCurrency.equals(normalizeCurrency(attempt.currencyCode()))
                || !providerCurrency.equals(normalizeCurrency(attempt.billingCurrency()))) {
            return Classification.CURRENCY_MISMATCH;
        }

        if (attempt.financeLinkId() == null || attempt.financeInvoiceId() == null) {
            return Classification.MISSING_FINANCE_INVOICE;
        }

        boolean providerPaid =
                providerState.status() == BillingPaymentProvider.PaymentStatus.SUCCEEDED;
        boolean financePaid = "PAID".equals(attempt.financeStatus());

        if (providerPaid && !financePaid) {
            return Classification.PROVIDER_PAID_FINANCE_PENDING;
        }
        if (!providerPaid && financePaid) {
            return Classification.FINANCE_PAID_PROVIDER_UNCONFIRMED;
        }
        return Classification.MATCHED;
    }

    private void insertItem(
            UUID tenantId,
            UUID runId,
            AttemptView attempt,
            Classification classification
    ) {
        boolean matched = classification == Classification.MATCHED;
        jdbc.update(
                "INSERT INTO subscription_billing_reconciliation_items "
                        + "(id, tenant_id, reconciliation_run_id, billing_invoice_id, "
                        + "finance_link_id, payment_attempt_id, provider_event_id, classification, "
                        + "expected_amount_minor, observed_amount_minor, expected_currency, "
                        + "observed_currency, details_metadata, state, created_at, resolved_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, "
                        + "jsonb_build_object('paymentState', ?, 'billingStatus', ?, 'financeStatus', ?), "
                        + "?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                runId,
                attempt.billingInvoiceId(),
                attempt.financeLinkId(),
                attempt.paymentAttemptId(),
                classification.name(),
                attempt.billingTotalMinor(),
                attempt.amountMinor(),
                normalizeCurrency(attempt.billingCurrency()),
                normalizeCurrency(attempt.currencyCode()),
                attempt.paymentState(),
                attempt.billingStatus(),
                attempt.financeStatus(),
                matched ? "RESOLVED" : "OPEN",
                Timestamp.from(Instant.now()),
                matched ? Timestamp.from(Instant.now()) : null);
    }

    private ReconciliationResult loadResult(
            UUID tenantId,
            UUID runId,
            boolean replay
    ) {
        List<Map<String, Object>> grouped = jdbc.queryForList(
                "SELECT classification, COUNT(*) AS count "
                        + "FROM subscription_billing_reconciliation_items "
                        + "WHERE tenant_id = ? AND reconciliation_run_id = ? "
                        + "GROUP BY classification ORDER BY classification",
                tenantId, runId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Map<String, Object> row : grouped) {
            String classification = String.valueOf(row.get("classification"));
            int count = ((Number) row.get("count")).intValue();
            counts.put(classification, count);
            total += count;
        }
        return new ReconciliationResult(runId, replay, total, Map.copyOf(counts));
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public enum Classification {
        MATCHED,
        MISSING_FINANCE_INVOICE,
        MISSING_PROVIDER_REFERENCE,
        AMOUNT_MISMATCH,
        CURRENCY_MISMATCH,
        PROVIDER_PAID_FINANCE_PENDING,
        FINANCE_PAID_PROVIDER_UNCONFIRMED
    }

    public record ReconciliationResult(
            UUID runId,
            boolean replay,
            int totalItems,
            Map<String, Integer> counts
    ) {}

    private record AttemptView(
            UUID paymentAttemptId,
            UUID billingInvoiceId,
            String providerPaymentRef,
            long amountMinor,
            String currencyCode,
            String paymentState,
            long billingTotalMinor,
            String billingCurrency,
            String billingStatus,
            UUID financeLinkId,
            UUID financeInvoiceId,
            String financeStatus
    ) {}
}
