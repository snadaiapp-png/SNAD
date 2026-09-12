package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.application.BillingWebhookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R13-G05 governed acceptance:
 * signed webhook -> trusted stored binding -> durable inbox -> audit/outbox,
 * with replay safety and zero raw payload/CHD persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class R0C13G05WebhookPostgresTest {

    private static final String TEST_WEBHOOK_SECRET =
            "g05-" + UUID.randomUUID() + "-" + UUID.randomUUID();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("sanad.subscription.billing.provider.mode", () -> "TEST");
        registry.add("sanad.subscription.billing.provider.test-webhook-secret",
                () -> TEST_WEBHOOK_SECRET);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private BillingWebhookService webhookService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Set<UUID> tenantIds = new LinkedHashSet<>();
    private final Set<UUID> planIds = new LinkedHashSet<>();

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID subscriptionId;
    private UUID billingInvoiceId;
    private String providerPaymentRef;

    @BeforeEach
    void setUp() {
        tenantId = seedTenant();
        otherTenantId = seedTenant();
        UUID plan = seedPlan();
        subscriptionId = seedSubscription(tenantId, plan);
        billingInvoiceId = seedBillingInvoice(tenantId, subscriptionId);
        providerPaymentRef = "test_pi_g05_" + compact(billingInvoiceId);
        seedProviderBinding(
                tenantId, subscriptionId, billingInvoiceId, providerPaymentRef);
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
                    return null;
                });
            } catch (RuntimeException ignored) {
                // Disposable CI database remains the final cleanup boundary.
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
    void invalidSignatureIsHttpRejectedWithZeroSideEffects() throws Exception {
        byte[] payload = payload("evt_bad_sig", providerPaymentRef, otherTenantId, false);

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", "test-hmac-sha256=deadbeef")
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isZero();
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isZero();
        assertThat(auditCount("evt_bad_sig")).isZero();
    }

    @Test
    void verifiedWebhookResolvesTenantOnlyFromStoredProviderBinding() throws Exception {
        String eventId = "evt_trusted_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, providerPaymentRef, otherTenantId, true);

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.providerEventId").value(eventId))
                .andExpect(jsonPath("$.duplicate").value(false));

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isEqualTo(1L);
        assertThat(tenantCount("subscription_billing_provider_events", otherTenantId)).isZero();
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isEqualTo(1L);
        assertThat(auditCount(eventId)).isEqualTo(1L);

        var stored = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT tenant_id, subscription_id, billing_invoice_id, provider, "
                        + "provider_event_id, provider_payment_ref, payload_sha256, "
                        + "signature_verified, processing_state "
                        + "FROM subscription_billing_provider_events "
                        + "WHERE provider = 'TEST' AND provider_event_id = ?",
                eventId));

        assertThat(stored.get("tenant_id")).isEqualTo(tenantId);
        assertThat(stored.get("subscription_id")).isEqualTo(subscriptionId);
        assertThat(stored.get("billing_invoice_id")).isEqualTo(billingInvoiceId);
        assertThat(stored.get("provider_payment_ref")).isEqualTo(providerPaymentRef);
        assertThat(stored.get("signature_verified")).isEqualTo(Boolean.TRUE);
        assertThat(stored.get("processing_state")).isEqualTo("PROCESSED");

        String outboxPayload = inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT payload_metadata::text FROM subscription_billing_outbox "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                String.class, tenantId, outboxKey(eventId)));
        assertThat(outboxPayload)
                .contains(eventId)
                .contains(providerPaymentRef)
                .doesNotContain(otherTenantId.toString())
                .doesNotContain("PAN_SHOULD_NOT_PERSIST")
                .doesNotContain("CVC_SHOULD_NOT_PERSIST")
                .doesNotContain("SENSITIVE_SENTINEL_SHOULD_NOT_PERSIST");
    }

    @Test
    void duplicateWebhookIsSideEffectFreeAndReplayMismatchFailsClosed() throws Exception {
        String eventId = "evt_replay_" + compact(UUID.randomUUID());
        byte[] firstPayload = payload(eventId, providerPaymentRef, otherTenantId, false);

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", sign(firstPayload))
                        .content(firstPayload))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", sign(firstPayload))
                        .content(firstPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isEqualTo(1L);
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isEqualTo(1L);
        assertThat(auditCount(eventId)).isEqualTo(1L);

        byte[] changedPayload = (
                "{\"eventId\":\"" + eventId + "\","
                        + "\"eventType\":\"payment.failed\","
                        + "\"paymentRef\":\"" + providerPaymentRef + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", sign(changedPayload))
                        .content(changedPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLAY_MISMATCH"));

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isEqualTo(1L);
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isEqualTo(1L);
        assertThat(auditCount(eventId)).isEqualTo(1L);
    }

    @Test
    void outboxFailureRollsBackInboxAndAuditThenRetrySucceeds() {
        String eventId = "evt_retry_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, providerPaymentRef, otherTenantId, false);

        inTenant(tenantId, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_outbox "
                            + "(event_id, tenant_id, event_type, aggregate_type, aggregate_id, idempotency_key) "
                            + "VALUES (?, ?, 'BILLING.PAYMENT_PENDING.v1', 'BILLING_INVOICE', ?, ?)",
                    UUID.randomUUID(), tenantId, billingInvoiceId, outboxKey(eventId));
            return null;
        });

        assertThatThrownBy(() -> webhookService.receive(payload, sign(payload)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isZero();
        assertThat(auditCount(eventId)).isZero();

        inTenant(tenantId, () -> {
            jdbc.update(
                    "DELETE FROM subscription_billing_outbox "
                            + "WHERE tenant_id = ? AND idempotency_key = ?",
                    tenantId, outboxKey(eventId));
            return null;
        });

        var retry = webhookService.receive(payload, sign(payload));
        assertThat(retry.duplicate()).isFalse();
        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isEqualTo(1L);
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isEqualTo(1L);
        assertThat(auditCount(eventId)).isEqualTo(1L);
    }

    @Test
    void verifiedProviderResolutionPolicyIsSelectOnlyAndFailClosedWithoutWebhookContext() {
        long hidden = new TransactionTemplate(transactionManager).execute(status ->
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM subscription_billing_payment_attempts "
                                + "WHERE provider = 'TEST' AND provider_payment_ref = ?",
                        Long.class, providerPaymentRef));
        assertThat(hidden).isZero();

        var policy = jdbc.queryForMap(
                "SELECT cmd, qual FROM pg_policies "
                        + "WHERE schemaname = 'public' "
                        + "AND tablename = 'subscription_billing_payment_attempts' "
                        + "AND policyname = 'provider_webhook_resolution'");
        assertThat(policy.get("cmd")).isEqualTo("SELECT");
        assertThat(policy.get("qual").toString())
                .contains("app.billing_webhook_verified")
                .contains("app.billing_provider");
    }

    @Test
    void signedUnknownProviderReferenceIsRejectedWithZeroSideEffects() throws Exception {
        String eventId = "evt_unknown_" + compact(UUID.randomUUID());
        String unknownPaymentRef = "test_pi_unknown_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, unknownPaymentRef, otherTenantId, false);

        mockMvc.perform(post("/api/v1/billing/provider/webhook")
                        .contentType("application/json")
                        .header("X-Billing-Signature", sign(payload))
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRUSTED_BINDING_NOT_FOUND"));

        assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isZero();
        assertThat(tenantCount("subscription_billing_provider_events", otherTenantId)).isZero();
        assertThat(tenantCount("subscription_billing_outbox", tenantId)).isZero();
        assertThat(tenantCount("subscription_billing_outbox", otherTenantId)).isZero();
        assertThat(auditCount(eventId)).isZero();
    }

    @Test
    void concurrentDuplicateWebhooksCommitExactlyOnce() throws Exception {
        String eventId = "evt_concurrent_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, providerPaymentRef, otherTenantId, false);
        String signature = sign(payload);

        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BillingWebhookService.WebhookReceipt>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent webhook start barrier timed out");
                    }
                    return webhookService.receive(payload, signature);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int duplicateReceipts = 0;
            for (Future<BillingWebhookService.WebhookReceipt> future : futures) {
                if (future.get(30, TimeUnit.SECONDS).duplicate()) {
                    duplicateReceipts++;
                }
            }

            assertThat(duplicateReceipts).isEqualTo(callers - 1);
            assertThat(tenantCount("subscription_billing_provider_events", tenantId)).isEqualTo(1L);
            assertThat(tenantCount("subscription_billing_outbox", tenantId)).isEqualTo(1L);
            assertThat(auditCount(eventId)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void billingOutboxContractIsDeterministicTypedAndVersioned() {
        String eventId = "evt_outbox_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, providerPaymentRef, otherTenantId, false);

        webhookService.receive(payload, sign(payload));

        var row = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT event_type, event_version, aggregate_type, aggregate_id, "
                        + "idempotency_key, status, payload_metadata::text AS payload_metadata "
                        + "FROM subscription_billing_outbox "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                tenantId,
                outboxKey(eventId)));

        assertThat(row.get("event_type"))
                .isEqualTo(BillingWebhookService.OUTBOX_EVENT_TYPE);
        assertThat(((Number) row.get("event_version")).intValue()).isEqualTo(1);
        assertThat(row.get("aggregate_type")).isEqualTo("BILLING_INVOICE");
        assertThat(row.get("aggregate_id")).isEqualTo(billingInvoiceId);
        assertThat(row.get("idempotency_key")).isEqualTo(outboxKey(eventId));
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("payload_metadata").toString())
                .contains("\"provider\": \"TEST\"")
                .contains(eventId)
                .contains(providerPaymentRef);
    }

    @Test
    void rawPayloadCardAndSecretFieldsAreNeverPersisted() {
        String eventId = "evt_chd_" + compact(UUID.randomUUID());
        byte[] payload = payload(eventId, providerPaymentRef, otherTenantId, true);
        webhookService.receive(payload, sign(payload));

        String eventText = inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT concat_ws('|', provider_event_id, provider_payment_ref, event_type, payload_sha256) "
                        + "FROM subscription_billing_provider_events "
                        + "WHERE tenant_id = ? AND provider_event_id = ?",
                String.class, tenantId, eventId));
        String outboxText = inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT payload_metadata::text FROM subscription_billing_outbox "
                        + "WHERE tenant_id = ? AND idempotency_key = ?",
                String.class, tenantId, outboxKey(eventId)));
        String auditText = jdbc.queryForObject(
                "SELECT concat_ws('|', before_state, after_state, reason, failure_reason) "
                        + "FROM platform_audit_logs "
                        + "WHERE action = 'BILLING.WEBHOOK.ACCEPTED' AND resource_id = ?",
                String.class, eventId);

        for (String persisted : new String[]{eventText, outboxText, auditText}) {
            assertThat(persisted)
                    .doesNotContain("PAN_SHOULD_NOT_PERSIST")
                    .doesNotContain("\"card_number\"")
                    .doesNotContain("\"cvc\"")
                    .doesNotContain("SENSITIVE_SENTINEL_SHOULD_NOT_PERSIST")
                    .doesNotContain("\"authorization\"");
        }
    }

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G05 " + compact(id), "r13-g05-" + compact(id), now, now);
        return id;
    }

    private UUID seedPlan() {
        UUID id = UUID.randomUUID();
        planIds.add(id);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO saas_plans "
                        + "(id, code, name, description, status, currency_code, monthly_price_minor, annual_price_minor, "
                        + "trial_days, max_users, max_organizations, storage_mb, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'R13 G05 test', 'ACTIVE', 'SAR', 10350, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G05-" + compact(id), "G05 Plan " + compact(id), now, now);
        return id;
    }

    private UUID seedSubscription(UUID tenant, UUID plan) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenant_subscriptions "
                        + "(id, tenant_id, plan_id, status, billing_cycle, seat_quantity, credit_balance_minor, "
                        + "started_at, current_period_start, current_period_end, cancel_at_period_end, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, ?, ?, ?, FALSE, ?, ?)",
                id, tenant, plan,
                Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plus(Duration.ofDays(30))),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedBillingInvoice(UUID tenant, UUID subscription) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, "
                        + "subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor, "
                        + "description, period_start, period_end, due_at, paid_at, payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 9000, 0, 1350, 10350, 0, "
                        + "'R13 G05 webhook invoice', ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G05-" + compact(id),
                Timestamp.from(now.minus(Duration.ofDays(1))),
                Timestamp.from(now.plus(Duration.ofDays(29))),
                Timestamp.from(now.plus(Duration.ofDays(14))),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private void seedProviderBinding(
            UUID tenant,
            UUID subscription,
            UUID invoice,
            String paymentRef
    ) {
        UUID customerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        inTenant(tenant, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_provider_customers "
                            + "(id, tenant_id, provider, provider_customer_ref) "
                            + "VALUES (?, ?, 'TEST', ?)",
                    customerId, tenant, "test_cus_g05_" + compact(customerId));
            jdbc.update(
                    "INSERT INTO subscription_billing_payment_attempts "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, provider_customer_id, "
                            + "provider, provider_payment_ref, idempotency_key, state, amount_minor, currency_code) "
                            + "VALUES (?, ?, ?, ?, ?, 'TEST', ?, ?, 'PENDING', 10350, 'SAR')",
                    attemptId, tenant, subscription, invoice, customerId,
                    paymentRef, "g05-attempt-" + compact(attemptId));
            return null;
        });
    }

    private long tenantCount(String table, UUID tenant) {
        return inTenant(tenant, () -> {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?",
                    Long.class, tenant);
            return count == null ? 0L : count;
        });
    }

    private long auditCount(String eventId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs "
                        + "WHERE action = 'BILLING.WEBHOOK.ACCEPTED' AND resource_id = ?",
                Long.class, eventId);
        return count == null ? 0L : count;
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

    private static byte[] payload(
            String eventId,
            String paymentRef,
            UUID untrustedTenant,
            boolean includeSensitiveFields
    ) {
        String sensitive = includeSensitiveFields
                ? ",\"card_number\":\"PAN_SHOULD_NOT_PERSIST\",\"cvc\":\"CVC_SHOULD_NOT_PERSIST\","
                    + "\"secret\":\"SENSITIVE_SENTINEL_SHOULD_NOT_PERSIST\",\"authorization\":\"Bearer SENSITIVE_SENTINEL_SHOULD_NOT_PERSIST\""
                : "";
        return (
                "{\"eventId\":\"" + eventId + "\","
                        + "\"eventType\":\"payment.pending\","
                        + "\"paymentRef\":\"" + paymentRef + "\","
                        + "\"tenantId\":\"" + untrustedTenant + "\"" + sensitive + "}")
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

    private static String outboxKey(String eventId) {
        try {
            String material = "TEST:" + eventId;
            String digest = HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(material.getBytes(StandardCharsets.UTF_8)));
            return "PROVIDER_EVENT:TEST:" + digest;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
