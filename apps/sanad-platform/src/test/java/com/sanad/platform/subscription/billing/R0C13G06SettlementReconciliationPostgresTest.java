package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.application.BillingReconciliationService;
import com.sanad.platform.subscription.billing.application.BillingWebhookService;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R13-G06 PostgreSQL Direct acceptance.
 *
 * <p>Exercises the real FORCE-RLS schema and the provider -> Finance -> SCP
 * projection -> BillingStateService -> canonical lifecycle convergence path.
 * Docker/Testcontainers are forbidden.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class R0C13G06SettlementReconciliationPostgresTest {

    private static final String TEST_WEBHOOK_SECRET =
            "g06-" + UUID.randomUUID() + "-" + UUID.randomUUID();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("sanad.subscription.billing.provider.mode", () -> "TEST");
        registry.add("sanad.subscription.billing.provider.test-webhook-secret",
                () -> TEST_WEBHOOK_SECRET);
    }

    @Autowired private BillingWebhookService webhookService;
    @Autowired private BillingReconciliationService reconciliationService;
    @Autowired private BillingPaymentProvider provider;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Set<UUID> tenantIds = new LinkedHashSet<>();
    private final Set<UUID> planIds = new LinkedHashSet<>();

    private UUID tenantId;
    private UUID planId;
    private UUID subscriptionId;
    private UUID billingInvoiceId;
    private UUID paymentAttemptId;
    private String providerPaymentRef;

    @BeforeEach
    void setUp() {
        tenantId = seedTenant();
        planId = seedPlan();
        subscriptionId = seedSubscription(tenantId, planId, "PAST_DUE", "PAST_DUE");
        billingInvoiceId = seedBillingInvoice(
                tenantId, subscriptionId, "SAR", 10_350L, Duration.ofDays(-5));
        ProviderBinding binding = seedProviderPayment(
                tenantId, subscriptionId, billingInvoiceId, 10_350L, "SAR");
        paymentAttemptId = binding.paymentAttemptId();
        providerPaymentRef = binding.providerPaymentRef();
    }

    @AfterEach
    void cleanup() {
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
                // Disposable CI database remains authoritative.
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

    @Test
    void providerSuccessSettlesFinanceThenProjectionThenCanonicalLifecycle() {
        sendEvent("evt_success_" + compact(UUID.randomUUID()), "payment.succeeded");

        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(financePaymentStatus()).isEqualTo("COMPLETED");
        assertThat(financeInvoiceStatus()).isEqualTo("PAID");

        Map<String, Object> billing = billingInvoiceState(billingInvoiceId);
        assertThat(billing.get("status")).isEqualTo("PAID");
        assertThat(((Number) billing.get("amount_paid_minor")).longValue()).isEqualTo(10_350L);
        assertThat(billing.get("payment_reference")).isEqualTo(providerPaymentRef);

        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");

        Map<String, Object> subscription = subscriptionState(subscriptionId);
        assertThat(subscription.get("status")).isEqualTo("ACTIVE");
        assertThat(subscription.get("billing_state")).isEqualTo("CURRENT");

        Long commands = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands "
                        + "WHERE subscription_id = ? AND command = 'PAYMENT_RECEIVED'",
                Long.class, subscriptionId);
        assertThat(commands).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicateSuccessWebhookCreatesExactlyOneSettlement() throws Exception {
        String eventId = "evt_concurrent_settle_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, "payment.succeeded", providerPaymentRef);
        String signature = sign(payload);

        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<BillingWebhookService.WebhookReceipt>> futures =
                new java.util.ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("G06 concurrency barrier timed out");
                    }
                    return webhookService.receive(payload, signature);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int duplicates = 0;
            for (Future<BillingWebhookService.WebhookReceipt> future : futures) {
                if (future.get(40, TimeUnit.SECONDS).duplicate()) {
                    duplicates++;
                }
            }
            assertThat(duplicates).isEqualTo(callers - 1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("ACTIVE");
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_provider_events "
                        + "WHERE tenant_id = ? AND provider_event_id = ?",
                tenantId, eventId))).isEqualTo(1L);
    }

    @Test
    void amountAndCurrencyMismatchFailClosedBeforeFinanceOrLifecycleMutation() {
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts SET amount_minor = 10349 "
                            + "WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });

        assertThatThrownBy(() ->
                sendEvent("evt_amount_" + compact(UUID.randomUUID()), "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("amount mismatch");

        assertNoSettlementDomainMutation();

        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts "
                            + "SET amount_minor = 10350, currency_code = 'USD' "
                            + "WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });

        assertThatThrownBy(() ->
                sendEvent("evt_currency_" + compact(UUID.randomUUID()), "payment.succeeded"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currency mismatch");

        assertNoSettlementDomainMutation();
    }

    @Test
    void providerFailureLeavesInvoiceOpenAndDunningOwnedStateUnchanged() {
        sendEvent("evt_failed_" + compact(UUID.randomUUID()), "payment.failed");

        assertThat(paymentAttemptState()).isEqualTo("FAILED");
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("OPEN");
        assertThat(financePaymentCount()).isZero();

        Map<String, Object> subscription = subscriptionState(subscriptionId);
        assertThat(subscription.get("status")).isEqualTo("PAST_DUE");
        assertThat(subscription.get("billing_state")).isEqualTo("PAST_DUE");
    }

    @Test
    void lateSuccessfulPaymentCannotResurrectTerminalSubscription() {
        jdbc.update(
                "UPDATE tenant_subscriptions SET status = 'CANCELLED' "
                        + "WHERE id = ?",
                subscriptionId);

        sendEvent("evt_terminal_" + compact(UUID.randomUUID()), "payment.succeeded");

        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("PAID");
        Map<String, Object> terminal = subscriptionState(subscriptionId);
        assertThat(terminal.get("status")).isEqualTo("CANCELLED");
        assertThat(terminal.get("billing_state")).isEqualTo("PAST_DUE");
    }

    @Test
    void historicalSubscriptionPaymentCannotDunnOrRecoverSuccessor() {
        jdbc.update(
                "UPDATE tenant_subscriptions SET status = 'CANCELLED' WHERE id = ?",
                subscriptionId);
        UUID successor = seedSubscription(tenantId, planId, "ACTIVE", "CURRENT");

        sendEvent("evt_historical_" + compact(UUID.randomUUID()), "payment.succeeded");

        Map<String, Object> historical = subscriptionState(subscriptionId);
        Map<String, Object> current = subscriptionState(successor);
        assertThat(historical.get("status")).isEqualTo("CANCELLED");
        assertThat(current.get("status")).isEqualTo("ACTIVE");
        assertThat(current.get("billing_state")).isEqualTo("CURRENT");

        Long successorCommands = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                Long.class, successor);
        assertThat(successorCommands).isZero();
    }

    @Test
    void financeFailureRollsBackWebhookProjectionAndCanRetrySafely() {
        UUID conflictingBillingInvoice = seedBillingInvoice(
                tenantId, subscriptionId, "SAR", 5_000L, Duration.ofDays(10));
        UUID dummyFinanceInvoice = UUID.randomUUID();
        String targetExternalReference = "SCP_INVOICE:" + billingInvoiceId;

        inTenant(tenantId, () -> {
            seedFinanceInvoice(tenantId, dummyFinanceInvoice, "DUMMY:" + dummyFinanceInvoice);
            jdbc.update(
                    "INSERT INTO subscription_billing_finance_links "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, "
                            + "finance_invoice_id, external_reference) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId,
                    conflictingBillingInvoice, dummyFinanceInvoice, targetExternalReference);
            return null;
        });

        String eventId = "evt_finance_retry_" + compact(UUID.randomUUID());
        assertThatThrownBy(() -> sendEvent(eventId, "payment.succeeded"))
                .isInstanceOf(RuntimeException.class);

        assertThat(financePaymentCount()).isZero();
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("OPEN");
        assertThat(paymentAttemptState()).isEqualTo("PENDING");
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_provider_events "
                        + "WHERE tenant_id = ? AND provider_event_id = ?",
                tenantId, eventId))).isZero();

        inTenant(tenantId, () -> {
            jdbc.update(
                    "DELETE FROM subscription_billing_finance_links "
                            + "WHERE tenant_id = ? AND billing_invoice_id = ?",
                    tenantId, conflictingBillingInvoice);
            jdbc.update(
                    "DELETE FROM finance_invoices WHERE tenant_id = ? AND id = ?",
                    tenantId, dummyFinanceInvoice);
            return null;
        });

        sendEvent(eventId, "payment.succeeded");
        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("PAID");
    }

    @Test
    void reconciliationDetectsMissingFinanceAndIsReadOnlyReplaySafe() {
        String key = "g06-recon-" + compact(UUID.randomUUID());

        var first = reconciliationService.reconcileReadOnly(tenantId, key, null);
        var replay = reconciliationService.reconcileReadOnly(tenantId, key, null);

        assertThat(first.replay()).isFalse();
        assertThat(first.totalItems()).isEqualTo(1);
        assertThat(first.counts())
                .containsEntry("MISSING_FINANCE_INVOICE", 1);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.runId()).isEqualTo(first.runId());

        assertThat(financePaymentCount()).isZero();
        assertThat(paymentAttemptState()).isEqualTo("PENDING");
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("OPEN");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("PAST_DUE");

        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_reconciliation_runs "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                tenantId, key))).isEqualTo(1L);
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_reconciliation_items "
                        + "WHERE tenant_id = ? AND reconciliation_run_id = ?",
                tenantId, first.runId()))).isEqualTo(1L);
    }

    @Test
    void reconciliationDetectsAmountCurrencyAndMissingProviderReference() {
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts SET amount_minor = 10349 "
                            + "WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });
        var amount = reconciliationService.reconcileReadOnly(
                tenantId, "g06-amount-" + compact(UUID.randomUUID()), null);
        assertThat(amount.counts()).containsEntry("AMOUNT_MISMATCH", 1);

        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts "
                            + "SET amount_minor = 10350, currency_code = 'USD' "
                            + "WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });
        var currency = reconciliationService.reconcileReadOnly(
                tenantId, "g06-currency-" + compact(UUID.randomUUID()), null);
        assertThat(currency.counts()).containsEntry("CURRENCY_MISMATCH", 1);

        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts "
                            + "SET currency_code = 'SAR', provider_payment_ref = NULL "
                            + "WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });
        var missing = reconciliationService.reconcileReadOnly(
                tenantId, "g06-missing-provider-" + compact(UUID.randomUUID()), null);
        assertThat(missing.counts()).containsEntry("MISSING_PROVIDER_REFERENCE", 1);

        assertThat(financePaymentCount()).isZero();
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("OPEN");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("PAST_DUE");
    }

    @Test
    void webhookAndReconciliationRaceConvergesDeterministicallyToMatched() throws Exception {
        String eventId = "evt_race_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, "payment.succeeded", providerPaymentRef);
        String signature = sign(payload);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> webhook = pool.submit(() -> {
                ready.countDown();
                await(start);
                return webhookService.receive(payload, signature);
            });
            Future<?> reconciliation = pool.submit(() -> {
                ready.countDown();
                await(start);
                return reconciliationService.reconcileReadOnly(
                        tenantId, "g06-race-first-" + compact(UUID.randomUUID()), null);
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            webhook.get(40, TimeUnit.SECONDS);
            reconciliation.get(40, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        var converged = reconciliationService.reconcileReadOnly(
                tenantId, "g06-race-final-" + compact(UUID.randomUUID()), null);

        assertThat(converged.totalItems()).isEqualTo(1);
        assertThat(converged.counts()).containsEntry("MATCHED", 1);
        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("ACTIVE");
        assertThat(subscriptionState(subscriptionId).get("billing_state")).isEqualTo("CURRENT");
    }

    private void sendEvent(String eventId, String eventType) {
        byte[] payload = payload(eventId, eventType, providerPaymentRef);
        webhookService.receive(payload, sign(payload));
    }

    private void assertNoSettlementDomainMutation() {
        assertThat(financePaymentCount()).isZero();
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("OPEN");
        Map<String, Object> subscription = subscriptionState(subscriptionId);
        assertThat(subscription.get("status")).isEqualTo("PAST_DUE");
        assertThat(subscription.get("billing_state")).isEqualTo("PAST_DUE");
    }

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G06 " + compact(id), "r13-g06-" + compact(id),
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
                        + "VALUES (?, ?, ?, 'R13 G06 test', 'ACTIVE', 'SAR', "
                        + "10350, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G06-" + compact(id), "G06 Plan " + compact(id),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedSubscription(
            UUID tenant,
            UUID plan,
            String status,
            String billingState
    ) {
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
            UUID tenant,
            UUID subscription,
            String currency,
            long totalMinor,
            Duration dueOffset
    ) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        long subtotal = totalMinor;
        jdbc.update(
                "INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, "
                        + "subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor, "
                        + "description, period_start, period_end, due_at, paid_at, payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', ?, ?, 0, 0, ?, 0, "
                        + "'R13 G06 settlement invoice', ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G06-" + compact(id), currency,
                subtotal, totalMinor,
                Timestamp.from(now.minus(Duration.ofDays(30))),
                Timestamp.from(now),
                Timestamp.from(now.plus(dueOffset)),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private ProviderBinding seedProviderPayment(
            UUID tenant,
            UUID subscription,
            UUID invoice,
            long amountMinor,
            String currency
    ) {
        String suffix = compact(UUID.randomUUID());
        BillingPaymentProvider.ProviderCustomer providerCustomer =
                provider.ensureProviderCustomer(
                        new BillingPaymentProvider.EnsureCustomerCommand(
                                tenant, "g06-customer-" + suffix, "g06-customer-idem-" + suffix));
        BillingPaymentProvider.PaymentIntent intent =
                provider.createPaymentIntent(
                        new BillingPaymentProvider.CreatePaymentIntentCommand(
                                tenant, invoice, providerCustomer.providerCustomerRef(),
                                amountMinor, currency, "g06-intent-" + suffix));

        UUID customerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        inTenant(tenant, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_provider_customers "
                            + "(id, tenant_id, provider, provider_customer_ref) "
                            + "VALUES (?, ?, 'TEST', ?)",
                    customerId, tenant, providerCustomer.providerCustomerRef());
            jdbc.update(
                    "INSERT INTO subscription_billing_payment_attempts "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, provider_customer_id, "
                            + "provider, provider_payment_ref, idempotency_key, state, amount_minor, currency_code) "
                            + "VALUES (?, ?, ?, ?, ?, 'TEST', ?, ?, 'PENDING', ?, ?)",
                    attemptId, tenant, subscription, invoice, customerId,
                    intent.providerPaymentRef(), "g06-attempt-" + suffix,
                    amountMinor, currency);
            return null;
        });
        return new ProviderBinding(attemptId, intent.providerPaymentRef());
    }

    private void seedFinanceInvoice(UUID tenant, UUID invoiceId, String externalReference) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO finance_invoices "
                        + "(id, tenant_id, invoice_number, customer_type, customer_id, customer_name, "
                        + "issue_date, due_date, currency, subtotal, tax_amount, total_amount, paid_amount, "
                        + "status, notes, version, external_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'MANUAL', NULL, 'R13 G06 dummy', ?, ?, 'SAR', "
                        + "1.00, 0.00, 1.00, 0.00, 'ISSUED', 'test', 0, ?, ?, ?)",
                invoiceId, tenant, "FIN-G06-" + compact(invoiceId),
                java.sql.Date.valueOf(LocalDate.now()),
                java.sql.Date.valueOf(LocalDate.now().plusDays(14)),
                externalReference, Timestamp.from(now), Timestamp.from(now));
    }

    private Map<String, Object> billingInvoiceState(UUID invoiceId) {
        return jdbc.queryForMap(
                "SELECT status, amount_paid_minor, payment_reference "
                        + "FROM billing_invoices WHERE tenant_id = ? AND id = ?",
                tenantId, invoiceId);
    }

    private Map<String, Object> subscriptionState(UUID subId) {
        return jdbc.queryForMap(
                "SELECT status, billing_state FROM tenant_subscriptions WHERE id = ?",
                subId);
    }

    private String paymentAttemptState() {
        return inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT state FROM subscription_billing_payment_attempts "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, paymentAttemptId));
    }

    private long financePaymentCount() {
        return inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?",
                tenantId));
    }

    private String financePaymentStatus() {
        return inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT status FROM finance_payments WHERE tenant_id = ?",
                String.class, tenantId));
    }

    private String financeInvoiceStatus() {
        return inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT status FROM finance_invoices WHERE tenant_id = ? "
                        + "AND external_reference = ?",
                String.class, tenantId, "SCP_INVOICE:" + billingInvoiceId));
    }

    private <T> T inTenant(UUID tenant, Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenant.toString());
            return action.get();
        });
    }

    private long scalarLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static byte[] payload(
            String eventId,
            String eventType,
            String paymentRef
    ) {
        return (
                "{\"eventId\":\"" + eventId + "\","
                        + "\"eventType\":\"" + eventType + "\","
                        + "\"paymentRef\":\"" + paymentRef + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    TEST_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return "test-hmac-sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("G06 race barrier timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("G06 race interrupted", e);
        }
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record ProviderBinding(
            UUID paymentAttemptId,
            String providerPaymentRef
    ) {}
}
