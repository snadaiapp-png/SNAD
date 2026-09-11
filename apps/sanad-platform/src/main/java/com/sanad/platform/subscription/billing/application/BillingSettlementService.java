package com.sanad.platform.subscription.billing.application;

import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.domain.SubscriptionFinancePort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * R13-G06 provider-settlement convergence authority.
 *
 * <p>Ordering is deliberate and transactional:
 * verified provider truth -> Finance authoritative settlement -> SCP billing
 * projection -> BillingStateService -> canonical SubscriptionCommandService.
 * This service never writes Finance tables or tenant_subscriptions directly.</p>
 */
@Service
public class BillingSettlementService {

    private final JdbcTemplate jdbc;
    private final BillingPaymentProvider provider;
    private final SubscriptionFinancePort financePort;
    private final BillingStateService billingStateService;
    private final TenantRlsTransactionContext tenantRlsContext;

    public BillingSettlementService(
            JdbcTemplate jdbc,
            BillingPaymentProvider provider,
            SubscriptionFinancePort financePort,
            BillingStateService billingStateService,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.financePort = financePort;
        this.billingStateService = billingStateService;
        this.tenantRlsContext = tenantRlsContext;
    }

    @Transactional
    public SettlementResult handleVerifiedProviderEvent(
            UUID tenantId,
            UUID paymentAttemptId,
            UUID billingInvoiceId,
            String providerPaymentRef,
            String eventType
    ) {
        if (tenantId == null || paymentAttemptId == null || billingInvoiceId == null) {
            throw new IllegalArgumentException("settlement identifiers must not be null");
        }
        if (providerPaymentRef == null || providerPaymentRef.isBlank()) {
            throw new IllegalArgumentException("providerPaymentRef must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }

        tenantRlsContext.applyForCurrentTransaction(tenantId);
        AttemptSnapshot attempt = loadAttemptForUpdate(tenantId, paymentAttemptId);
        if (!billingInvoiceId.equals(attempt.billingInvoiceId())
                || !providerPaymentRef.equals(attempt.providerPaymentRef())) {
            throw new IllegalStateException("Verified provider event does not match stored payment attempt");
        }

        return switch (eventType) {
            case "payment.succeeded" -> settleSuccess(tenantId, attempt);
            case "payment.failed" -> recordFailure(attempt, "FAILED");
            case "payment.cancelled" -> recordFailure(attempt, "CANCELLED");
            case "payment.refunded" -> recordFailure(attempt, "REFUNDED");
            default -> new SettlementResult(
                    attempt.id(), attempt.billingInvoiceId(), null, null,
                    attempt.state(), null, false);
        };
    }

    private SettlementResult settleSuccess(UUID tenantId, AttemptSnapshot attempt) {
        BillingPaymentProvider.PaymentState providerState =
                provider.queryPaymentState(new BillingPaymentProvider.QueryPaymentStateQuery(
                        tenantId, attempt.providerPaymentRef()));
        if (providerState.status() != BillingPaymentProvider.PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Verified payment.succeeded event did not resolve to SUCCEEDED provider state");
        }

        InvoiceSnapshot invoice = loadInvoiceForUpdate(
                tenantId, attempt.billingInvoiceId(), attempt.subscriptionId());

        if (providerState.amountMinor() != attempt.amountMinor()
                || providerState.amountMinor() != invoice.totalMinor()) {
            throw new IllegalStateException(
                    "Provider settlement amount mismatch: provider="
                            + providerState.amountMinor() + ", attempt=" + attempt.amountMinor()
                            + ", invoice=" + invoice.totalMinor());
        }

        String providerCurrency = normalizeCurrency(providerState.currencyCode());
        String attemptCurrency = normalizeCurrency(attempt.currencyCode());
        String invoiceCurrency = normalizeCurrency(invoice.currencyCode());
        if (!providerCurrency.equals(attemptCurrency)
                || !providerCurrency.equals(invoiceCurrency)) {
            throw new IllegalStateException(
                    "Provider settlement currency mismatch: provider=" + providerCurrency
                            + ", attempt=" + attemptCurrency
                            + ", invoice=" + invoiceCurrency);
        }

        if ("VOID".equals(invoice.status())) {
            throw new IllegalStateException("Void billing invoice cannot be settled");
        }

        SubscriptionFinancePort.SettlementLink finance =
                financePort.recordSettlement(
                        tenantId,
                        attempt.billingInvoiceId(),
                        attempt.id(),
                        providerState.amountMinor(),
                        providerCurrency);

        markBillingProjectionPaid(tenantId, invoice, attempt.providerPaymentRef());
        markAttemptSucceeded(tenantId, attempt.id());

        // Canonical convergence only after Finance is authoritative and the SCP
        // invoice projection is paid. BillingStateService remains the sole
        // billing_state writer and delegates lifecycle status to the canonical
        // SubscriptionCommandService.
        String resultingBillingState = billingStateService.evaluateAndTransition(tenantId);

        return new SettlementResult(
                attempt.id(),
                attempt.billingInvoiceId(),
                finance.financeInvoiceId(),
                finance.financePaymentId(),
                "SUCCEEDED",
                resultingBillingState,
                true);
    }

    private SettlementResult recordFailure(AttemptSnapshot attempt, String targetState) {
        // Never allow a late failure/cancel event to downgrade a completed or
        // refunded payment attempt. The signed provider event remains durable
        // in the inbox even when the attempt state is already terminal.
        if ("SUCCEEDED".equals(attempt.state()) || "REFUNDED".equals(attempt.state())) {
            return new SettlementResult(
                    attempt.id(), attempt.billingInvoiceId(), null, null,
                    attempt.state(), null, false);
        }

        jdbc.update(
                "UPDATE subscription_billing_payment_attempts "
                        + "SET state = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
                targetState, Timestamp.from(Instant.now()), attempt.id(), attempt.tenantId());
        return new SettlementResult(
                attempt.id(), attempt.billingInvoiceId(), null, null,
                targetState, null, false);
    }

    private AttemptSnapshot loadAttemptForUpdate(UUID tenantId, UUID paymentAttemptId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, tenant_id, subscription_id, billing_invoice_id, "
                            + "provider_payment_ref, state, amount_minor, currency_code "
                            + "FROM subscription_billing_payment_attempts "
                            + "WHERE tenant_id = ? AND id = ? FOR UPDATE",
                    (rs, rowNum) -> new AttemptSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("subscription_id", UUID.class),
                            rs.getObject("billing_invoice_id", UUID.class),
                            rs.getString("provider_payment_ref"),
                            rs.getString("state"),
                            rs.getLong("amount_minor"),
                            rs.getString("currency_code")),
                    tenantId, paymentAttemptId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Payment attempt not found for tenant", e);
        }
    }

    private InvoiceSnapshot loadInvoiceForUpdate(
            UUID tenantId,
            UUID billingInvoiceId,
            UUID expectedSubscriptionId
    ) {
        try {
            InvoiceSnapshot invoice = jdbc.queryForObject(
                    "SELECT id, tenant_id, subscription_id, status, currency_code, "
                            + "total_minor, amount_paid_minor "
                            + "FROM billing_invoices "
                            + "WHERE tenant_id = ? AND id = ? FOR UPDATE",
                    (rs, rowNum) -> new InvoiceSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("tenant_id", UUID.class),
                            rs.getObject("subscription_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("currency_code"),
                            rs.getLong("total_minor"),
                            rs.getLong("amount_paid_minor")),
                    tenantId, billingInvoiceId);
            if (invoice == null || !expectedSubscriptionId.equals(invoice.subscriptionId())) {
                throw new IllegalStateException(
                        "Billing invoice subscription does not match payment attempt");
            }
            return invoice;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Billing invoice not found for tenant", e);
        }
    }

    private void markBillingProjectionPaid(
            UUID tenantId,
            InvoiceSnapshot invoice,
            String providerPaymentRef
    ) {
        if ("PAID".equals(invoice.status())) {
            if (invoice.amountPaidMinor() != invoice.totalMinor()) {
                throw new IllegalStateException(
                        "Paid billing invoice has inconsistent paid amount");
            }
            return;
        }
        if (!"OPEN".equals(invoice.status())) {
            throw new IllegalStateException(
                    "Billing invoice cannot be settled from status " + invoice.status());
        }

        Instant now = Instant.now();
        int updated = jdbc.update(
                "UPDATE billing_invoices "
                        + "SET status = 'PAID', amount_paid_minor = total_minor, "
                        + "paid_at = COALESCE(paid_at, ?), "
                        + "payment_reference = COALESCE(payment_reference, ?), updated_at = ? "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'OPEN'",
                Timestamp.from(now), providerPaymentRef, Timestamp.from(now),
                tenantId, invoice.id());
        if (updated != 1) {
            throw new IllegalStateException(
                    "Billing invoice projection changed concurrently during settlement");
        }
    }

    private void markAttemptSucceeded(UUID tenantId, UUID paymentAttemptId) {
        int updated = jdbc.update(
                "UPDATE subscription_billing_payment_attempts "
                        + "SET state = 'SUCCEEDED', updated_at = ? "
                        + "WHERE tenant_id = ? AND id = ? "
                        + "AND state IN ('CREATED','PENDING','FAILED','SUCCEEDED')",
                Timestamp.from(Instant.now()), tenantId, paymentAttemptId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Payment attempt cannot transition to SUCCEEDED from its current state");
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        String currency = value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be a three-letter ISO code");
        }
        return currency;
    }

    public record SettlementResult(
            UUID paymentAttemptId,
            UUID billingInvoiceId,
            UUID financeInvoiceId,
            UUID financePaymentId,
            String paymentAttemptState,
            String billingState,
            boolean financeSettled
    ) {}

    private record AttemptSnapshot(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            UUID billingInvoiceId,
            String providerPaymentRef,
            String state,
            long amountMinor,
            String currencyCode
    ) {}

    private record InvoiceSnapshot(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            String status,
            String currencyCode,
            long totalMinor,
            long amountPaidMinor
    ) {}
}
