package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

abstract class R0C13G09PostgresSupport {

    protected static final String TEST_WEBHOOK_SECRET =
            "g09-" + UUID.randomUUID() + "-" + UUID.randomUUID();

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected BillingPaymentProvider provider;

    private final Set<UUID> tenantIds = new LinkedHashSet<>();
    private final Set<UUID> planIds = new LinkedHashSet<>();

    protected UUID tenantId;
    protected UUID subscriptionId;
    protected UUID billingInvoiceId;
    protected UUID paymentAttemptId;
    protected String providerPaymentRef;

    @BeforeEach
    void g09SetUp() {
        tenantId = seedTenant();
        UUID planId = seedPlan();
        subscriptionId = seedSubscription(tenantId, planId, "PAST_DUE", "PAST_DUE");
        billingInvoiceId = seedBillingInvoice(
                tenantId, subscriptionId, "SAR", 10_350L, Duration.ofDays(-5));
        ProviderBinding binding = seedProviderPayment(
                tenantId, subscriptionId, billingInvoiceId, 10_350L, "SAR");
        paymentAttemptId = binding.paymentAttemptId();
        providerPaymentRef = binding.providerPaymentRef();
    }

    @AfterEach
    void g09Cleanup() {
        for (UUID tenant : tenantIds) {
            try {
                inTenant(tenant, () -> {
                    jdbc.update("DELETE FROM subscription_billing_reconciliation_items WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_reconciliation_runs WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_outbox WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_provider_events WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_payment_attempts WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_finance_links WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM subscription_billing_provider_customers WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM finance_payments WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM finance_invoice_lines WHERE tenant_id = ?", tenant);
                    jdbc.update("DELETE FROM finance_invoices WHERE tenant_id = ?", tenant);
                    return null;
                });
            } catch (RuntimeException ignored) {
                // Disposable PostgreSQL Direct database remains authoritative.
            }
            jdbc.update("DELETE FROM platform_audit_logs WHERE target_tenant_id = ?", tenant);
            jdbc.update("DELETE FROM billing_invoices WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", tenant);
        }
        for (UUID plan : planIds) {
            jdbc.update("DELETE FROM saas_plan_entitlements WHERE plan_id = ?", plan);
            jdbc.update("DELETE FROM plan_versions WHERE plan_id = ?", plan);
            jdbc.update("DELETE FROM saas_plans WHERE id = ?", plan);
        }
        for (UUID tenant : tenantIds) {
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenant);
        }
    }

    protected void assertLocalBillingRemainsUnpaid() {
        Map<String, Object> invoice = jdbc.queryForMap(
                "SELECT status, amount_paid_minor FROM billing_invoices "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, billingInvoiceId);
        org.assertj.core.api.Assertions.assertThat(invoice.get("status")).isEqualTo("OPEN");
        org.assertj.core.api.Assertions.assertThat(
                ((Number) invoice.get("amount_paid_minor")).longValue()).isZero();

        Map<String, Object> subscription = jdbc.queryForMap(
                "SELECT status, billing_state FROM tenant_subscriptions WHERE id = ?",
                subscriptionId);
        org.assertj.core.api.Assertions.assertThat(subscription.get("status")).isEqualTo("PAST_DUE");
        org.assertj.core.api.Assertions.assertThat(subscription.get("billing_state")).isEqualTo("PAST_DUE");

        org.assertj.core.api.Assertions.assertThat(paymentAttemptState()).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(financePaymentCount()).isZero();
        org.assertj.core.api.Assertions.assertThat(outboxCount()).isZero();
    }

    protected long providerEventCount(String eventId) {
        return inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_provider_events "
                        + "WHERE tenant_id = ? AND provider_event_id = ?",
                tenantId, eventId));
    }

    protected long auditCount() {
        return scalarLong(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id = ?",
                tenantId);
    }

    protected long outboxCount() {
        return inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_outbox WHERE tenant_id = ?",
                tenantId));
    }

    protected String paymentAttemptState() {
        return inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT state FROM subscription_billing_payment_attempts "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, paymentAttemptId));
    }

    protected long financePaymentCount() {
        return inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?",
                tenantId));
    }

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G09 " + compact(id), "r13-g09-" + compact(id),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedPlan() {
        UUID id = UUID.randomUUID();
        planIds.add(id);
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO saas_plans "
                        + "(id, code, name, description, status, currency_code, "
                        + "monthly_price_minor, annual_price_minor, trial_days, max_users, "
                        + "max_organizations, storage_mb, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'R13 G09 test', 'ACTIVE', 'SAR', "
                        + "10350, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G09-" + compact(id), "G09 Plan " + compact(id),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedSubscription(UUID tenant, UUID plan, String status, String billingState) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenant_subscriptions "
                        + "(id, tenant_id, plan_id, status, billing_state, billing_cycle, "
                        + "seat_quantity, credit_balance_minor, started_at, current_period_start, "
                        + "current_period_end, cancel_at_period_end, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'MONTHLY', 1, 0, ?, ?, ?, FALSE, ?, ?)",
                id, tenant, plan, status, billingState,
                Timestamp.from(now.minus(Duration.ofDays(30))),
                Timestamp.from(now.minus(Duration.ofDays(30))),
                Timestamp.from(now.plus(Duration.ofDays(1))),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedBillingInvoice(
            UUID tenant, UUID subscription, String currency,
            long totalMinor, Duration dueOffset) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, "
                        + "subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor, "
                        + "description, period_start, period_end, due_at, paid_at, payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', ?, ?, 0, 0, ?, 0, "
                        + "'R13 G09 failure injection', ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G09-" + compact(id), currency,
                totalMinor, totalMinor,
                Timestamp.from(now.minus(Duration.ofDays(30))),
                Timestamp.from(now),
                Timestamp.from(now.plus(dueOffset)),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private ProviderBinding seedProviderPayment(
            UUID tenant, UUID subscription, UUID invoice,
            long amountMinor, String currency) {
        String suffix = compact(UUID.randomUUID());
        BillingPaymentProvider.ProviderCustomer customer =
                provider.ensureProviderCustomer(
                        new BillingPaymentProvider.EnsureCustomerCommand(
                                tenant, "g09-customer-" + suffix, "g09-customer-idem-" + suffix));
        BillingPaymentProvider.PaymentIntent intent =
                provider.createPaymentIntent(
                        new BillingPaymentProvider.CreatePaymentIntentCommand(
                                tenant, invoice, customer.providerCustomerRef(),
                                amountMinor, currency, "g09-intent-" + suffix));

        UUID customerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        inTenant(tenant, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_provider_customers "
                            + "(id, tenant_id, provider, provider_customer_ref) "
                            + "VALUES (?, ?, 'TEST', ?)",
                    customerId, tenant, customer.providerCustomerRef());
            jdbc.update(
                    "INSERT INTO subscription_billing_payment_attempts "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, provider_customer_id, "
                            + "provider, provider_payment_ref, idempotency_key, state, amount_minor, currency_code) "
                            + "VALUES (?, ?, ?, ?, ?, 'TEST', ?, ?, 'PENDING', ?, ?)",
                    attemptId, tenant, subscription, invoice, customerId,
                    intent.providerPaymentRef(), "g09-attempt-" + suffix,
                    amountMinor, currency);
            return null;
        });
        return new ProviderBinding(attemptId, intent.providerPaymentRef());
    }

    protected <T> T inTenant(UUID tenant, Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenant.toString());
            return action.get();
        });
    }

    protected long scalarLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    protected static byte[] payload(String eventId, String eventType, String paymentRef) {
        return (
                "{\"eventId\":\"" + eventId + "\","
                        + "\"eventType\":\"" + eventType + "\","
                        + "\"paymentRef\":\"" + paymentRef + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    protected static String sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    TEST_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return "test-hmac-sha256="
                    + java.util.HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record ProviderBinding(UUID paymentAttemptId, String providerPaymentRef) {}
}
