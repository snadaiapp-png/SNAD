package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.application.BillingWebhookService;
import com.sanad.platform.subscription.billing.domain.BillingPaymentProvider;
import com.sanad.platform.subscription.billing.domain.SubscriptionFinancePort;
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
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R13-G07.0 corrective PostgreSQL Direct acceptance.
 *
 * <p>Covers: typed/versioned billing outbox conformance, refund-state
 * correctness (SUCCEEDED -> REFUNDED), Finance-owned refund authority and
 * convergence ordering, and outbox transactional atomicity. Docker and
 * Testcontainers are forbidden; this suite runs against a host-native
 * PostgreSQL with the real FORCE-RLS schema.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class R0C13G07CorrectivePostgresTest {

    private static final String TEST_WEBHOOK_SECRET =
            "g07-0-" + UUID.randomUUID() + "-" + UUID.randomUUID();

    private static final Set<String> SENSITIVE_METADATA_KEYS = Set.of(
            "card_number", "pan", "cvc", "cvv", "track_data", "pin",
            "password", "secret", "api_key", "apikey", "token", "authorization",
            "payload", "rawpayload", "body", "raw_body");

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("sanad.subscription.billing.provider.mode", () -> "TEST");
        registry.add("sanad.subscription.billing.provider.test-webhook-secret",
                () -> TEST_WEBHOOK_SECRET);
    }

    @Autowired private BillingWebhookService webhookService;
    @Autowired private SubscriptionFinancePort financePort;
    @Autowired private BillingPaymentProvider provider;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.sanad.platform.subscription.billing.application.BillingReconciliationService
            reconciliationService;

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

    // ------------------------------------------------------------------
    // TASK A — typed/versioned outbox conformance
    // ------------------------------------------------------------------

    @Test
    void settlementSuccessEmitsTypedPaymentSucceededExactlyOnce() {
        sendEvent("evt_g07_success_" + compact(UUID.randomUUID()), "payment.succeeded");

        assertThat(outboxCount("BILLING.PAYMENT_SUCCEEDED.v1")).isEqualTo(1L);
        Map<String, Object> row = outboxSingleRow("BILLING.PAYMENT_SUCCEEDED.v1");
        assertThat(row.get("aggregate_id")).isEqualTo(paymentAttemptId);
        assertThat(((Number) row.get("event_version")).intValue()).isEqualTo(1);
        assertThat(row.get("idempotency_key"))
                .isEqualTo("PAYMENT_SUCCEEDED:" + paymentAttemptId);
        assertThat(row.get("aggregate_type")).isEqualTo("BILLING_PAYMENT_ATTEMPT");
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
    }

    @Test
    void duplicateVerifiedWebhookDoesNotDuplicatePaymentSucceeded() {
        String eventId = "evt_g07_dup_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, "payment.succeeded", providerPaymentRef);
        String signature = sign(payload);

        webhookService.receive(payload, signature);
        BillingWebhookService.WebhookReceipt replay = webhookService.receive(payload, signature);

        assertThat(replay.duplicate()).isTrue();
        assertThat(outboxCount("BILLING.PAYMENT_SUCCEEDED.v1")).isEqualTo(1L);
        assertThat(financePaymentCount()).isEqualTo(1L);
    }

    @Test
    void providerFailureEmitsTypedPaymentFailedExactlyOnce() {
        String eventId = "evt_g07_failed_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, "payment.failed", providerPaymentRef);
        String signature = sign(payload);

        webhookService.receive(payload, signature);
        webhookService.receive(payload, signature);

        assertThat(outboxCount("BILLING.PAYMENT_FAILED.v1")).isEqualTo(1L);
        Map<String, Object> row = outboxSingleRow("BILLING.PAYMENT_FAILED.v1");
        assertThat(row.get("aggregate_id")).isEqualTo(paymentAttemptId);
        assertThat(paymentAttemptState()).isEqualTo("FAILED");
    }

    @Test
    void reconciliationRunWithDiscrepanciesEmitsReconciliationExceptionEvent() {
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE subscription_billing_payment_attempts "
                            + "SET provider_payment_ref = NULL WHERE tenant_id = ? AND id = ?",
                    tenantId, paymentAttemptId);
            return null;
        });

        String reconKey = "g07-recon-" + compact(UUID.randomUUID());
        var result = reconciliationService.reconcileReadOnly(tenantId, reconKey, null);
        assertThat(result.totalItems()).isEqualTo(1);
        assertThat(result.counts()).containsEntry("MISSING_PROVIDER_REFERENCE", 1);

        assertThat(outboxCount("BILLING.RECONCILIATION_EXCEPTION.v1")).isEqualTo(1L);
        Map<String, Object> row = outboxSingleRow("BILLING.RECONCILIATION_EXCEPTION.v1");
        assertThat(row.get("aggregate_type")).isEqualTo("BILLING_RECONCILIATION_RUN");
        assertThat(row.get("idempotency_key"))
                .isEqualTo("RECONCILIATION_EXCEPTION:" + row.get("aggregate_id"));

        // replaying the same reconciliation idempotency key must return the
        // persisted run and must not duplicate the exception event
        var replayed = reconciliationService.reconcileReadOnly(tenantId, reconKey, null);
        assertThat(replayed.replay()).isTrue();
        assertThat(replayed.runId()).isEqualTo(result.runId());
        assertThat(outboxCount("BILLING.RECONCILIATION_EXCEPTION.v1")).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void outboxEventMetadataMustNotContainSensitiveOrRawPayloadFields() {
        sendEvent("evt_g07_meta_" + compact(UUID.randomUUID()), "payment.succeeded");
        sendEvent("evt_g07_meta_refund_" + compact(UUID.randomUUID()), "payment.refunded");

        List<Map<String, Object>> rows = inTenant(tenantId, () -> jdbc.queryForList(
                "SELECT event_type, payload_metadata::text AS payload_metadata_text "
                        + "FROM subscription_billing_outbox WHERE tenant_id = ?",
                tenantId));
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            String metadataJson = String.valueOf(row.get("payload_metadata_text"));
            assertThat(metadataJson).isNotBlank();
            for (String sensitive : SENSITIVE_METADATA_KEYS) {
                assertThat(metadataJson.toLowerCase(Locale.ROOT))
                        .as("outbox event %s must not expose sensitive field %s",
                                row.get("event_type"), sensitive)
                        .doesNotContain("\"" + sensitive + "\"");
            }
        }
    }

    // ------------------------------------------------------------------
    // TASK B + C — refund state correctness and Finance authority
    // ------------------------------------------------------------------

    @Test
    void verifiedRefundEventConvergesSucceededAttemptToRefunded() {
        sendEvent("evt_g07_settle_" + compact(UUID.randomUUID()), "payment.succeeded");
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
        assertThat(financePaymentStatus()).isEqualTo("COMPLETED");

        sendEvent("evt_g07_refund_" + compact(UUID.randomUUID()), "payment.refunded");

        assertThat(paymentAttemptState()).isEqualTo("REFUNDED");
        assertThat(financePaymentStatus()).isEqualTo("REFUNDED");
        assertThat(outboxCount("BILLING.REFUND_RECORDED.v1")).isEqualTo(1L);

        // billing projection and canonical lifecycle are not part of the
        // G07.0 refund convergence surface
        assertThat(billingInvoiceState(billingInvoiceId).get("status")).isEqualTo("PAID");
        Map<String, Object> subscription = subscriptionState(subscriptionId);
        assertThat(subscription.get("status")).isEqualTo("ACTIVE");
        assertThat(subscription.get("billing_state")).isEqualTo("CURRENT");
    }

    @Test
    void duplicateVerifiedRefundDoesNotProduceDuplicateFinanceTransition() {
        sendEvent("evt_g07_settle2_" + compact(UUID.randomUUID()), "payment.succeeded");
        sendEvent("evt_g07_refund_a_" + compact(UUID.randomUUID()), "payment.refunded");
        sendEvent("evt_g07_refund_b_" + compact(UUID.randomUUID()), "payment.refunded");

        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(financePaymentStatus()).isEqualTo("REFUNDED");
        assertThat(outboxCount("BILLING.REFUND_RECORDED.v1")).isEqualTo(1L);
        assertThat(paymentAttemptState()).isEqualTo("REFUNDED");
    }

    @Test
    void financeRefundFailureRollsBackLocalRefundProjectionAndOutbox() {
        sendEvent("evt_g07_settle3_" + compact(UUID.randomUUID()), "payment.succeeded");
        assertThat(financePaymentStatus()).isEqualTo("COMPLETED");

        // sabotage the authoritative Finance refund path only
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE finance_payments SET status = 'CANCELLED' WHERE tenant_id = ?",
                    tenantId);
            return null;
        });

        String refundEventId = "evt_g07_refund_fail_" + compact(UUID.randomUUID());
        assertThatThrownBy(() -> sendEvent(refundEventId, "payment.refunded"))
                .isInstanceOf(RuntimeException.class);

        // convergence order held: Finance was attempted first and stayed
        // authoritative; the local attempt and outbox are rolled back with it
        assertThat(financePaymentStatus()).isEqualTo("CANCELLED");
        assertThat(paymentAttemptState()).isEqualTo("SUCCEEDED");
        assertThat(outboxCount("BILLING.REFUND_RECORDED.v1")).isZero();
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_provider_events "
                        + "WHERE tenant_id = ? AND provider_event_id = ?",
                tenantId, refundEventId))).isZero();
    }

    @Test
    void refundRetryAfterFinanceRecoveryConvergesSafely() {
        sendEvent("evt_g07_settle4_" + compact(UUID.randomUUID()), "payment.succeeded");
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE finance_payments SET status = 'CANCELLED' WHERE tenant_id = ?",
                    tenantId);
            return null;
        });
        assertThatThrownBy(() -> sendEvent(
                "evt_g07_refund_retry_fail_" + compact(UUID.randomUUID()),
                "payment.refunded"))
                .isInstanceOf(RuntimeException.class);

        // Finance recovers (authoritative state back to COMPLETED) and the
        // retry converges without duplicate transitions
        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE finance_payments SET status = 'COMPLETED' WHERE tenant_id = ?",
                    tenantId);
            return null;
        });
        sendEvent("evt_g07_refund_retry_ok_" + compact(UUID.randomUUID()), "payment.refunded");

        assertThat(paymentAttemptState()).isEqualTo("REFUNDED");
        assertThat(financePaymentStatus()).isEqualTo("REFUNDED");
        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(outboxCount("BILLING.REFUND_RECORDED.v1")).isEqualTo(1L);
    }

    @Test
    void refundDoesNotMutateTerminalSubscriptionStatus() {
        sendEvent("evt_g07_settle5_" + compact(UUID.randomUUID()), "payment.succeeded");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("ACTIVE");

        inTenant(tenantId, () -> {
            jdbc.update(
                    "UPDATE tenant_subscriptions SET status = 'CANCELLED' WHERE id = ?",
                    subscriptionId);
            return null;
        });

        sendEvent("evt_g07_refund_term_" + compact(UUID.randomUUID()), "payment.refunded");

        assertThat(paymentAttemptState()).isEqualTo("REFUNDED");
        assertThat(subscriptionState(subscriptionId).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void refundEventForNeverSucceededAttemptFailsClosed() {
        // fixture attempt is PENDING and was never settled: a verified refund
        // for it is an anomaly and must fail closed, never silently reclassify
        assertThatThrownBy(() -> sendEvent(
                "evt_g07_refund_anomaly_" + compact(UUID.randomUUID()),
                "payment.refunded"))
                .isInstanceOf(RuntimeException.class);
        assertThat(paymentAttemptState()).isEqualTo("PENDING");
        assertThat(financePaymentCount()).isZero();
    }

    @Test
    void financePortRefundIsIdempotentAndTenantScoped() {
        sendEvent("evt_g07_settle6_" + compact(UUID.randomUUID()), "payment.succeeded");

        var first = financePort.recordRefund(
                tenantId, billingInvoiceId, paymentAttemptId, 10_350L, "SAR");
        var second = financePort.recordRefund(
                tenantId, billingInvoiceId, paymentAttemptId, 10_350L, "SAR");

        assertThat(second.financePaymentId()).isEqualTo(first.financePaymentId());
        assertThat(financePaymentCount()).isEqualTo(1L);
        assertThat(financePaymentStatus()).isEqualTo("REFUNDED");

        // partial refunds have no accounting model in G07.0 and fail closed
        assertThatThrownBy(() -> financePort.recordRefund(
                tenantId, billingInvoiceId, paymentAttemptId, 10_349L, "SAR"))
                .isInstanceOf(IllegalStateException.class);

        // cross-tenant refund attempts are rejected
        UUID otherTenant = seedTenant();
        assertThatThrownBy(() -> financePort.recordRefund(
                otherTenant, billingInvoiceId, paymentAttemptId, 10_350L, "SAR"))
                .isInstanceOf(RuntimeException.class);
        assertThat(financePaymentCount()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void sendEvent(String eventId, String eventType) {
        byte[] payload = payload(eventId, eventType, providerPaymentRef);
        webhookService.receive(payload, sign(payload));
    }

    private long outboxCount(String eventType) {
        return inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_outbox "
                        + "WHERE tenant_id = ? AND event_type = ?",
                tenantId, eventType));
    }

    private Map<String, Object> outboxSingleRow(String eventType) {
        return inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT event_type, event_version, aggregate_type, aggregate_id, "
                        + "idempotency_key, payload_metadata, status "
                        + "FROM subscription_billing_outbox "
                        + "WHERE tenant_id = ? AND event_type = ?",
                tenantId, eventType));
    }

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G07.0 " + compact(id), "r13-g070-" + compact(id),
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
                        + "VALUES (?, ?, ?, 'R13 G07.0 test', 'ACTIVE', 'SAR', "
                        + "10350, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G070-" + compact(id), "G07.0 Plan " + compact(id),
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
                        + "'R13 G07.0 corrective invoice', ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G070-" + compact(id), currency,
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
                                tenant, "g070-customer-" + suffix, "g070-customer-idem-" + suffix));
        BillingPaymentProvider.PaymentIntent intent =
                provider.createPaymentIntent(
                        new BillingPaymentProvider.CreatePaymentIntentCommand(
                                tenant, invoice, providerCustomer.providerCustomerRef(),
                                amountMinor, currency, "g070-intent-" + suffix));

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
                    intent.providerPaymentRef(), "g070-attempt-" + suffix,
                    amountMinor, currency);
            return null;
        });
        return new ProviderBinding(attemptId, intent.providerPaymentRef());
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

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    private record ProviderBinding(
            UUID paymentAttemptId,
            String providerPaymentRef
    ) {}
}
