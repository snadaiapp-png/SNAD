package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.application.BillingReconciliationService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R13-G07.0 corrective PostgreSQL Direct acceptance — provider DISABLED mode.
 *
 * <p>Defect under correction: DisabledBillingPaymentProvider.queryPaymentState()
 * throws IllegalStateException and BillingReconciliationService used to map that
 * into MISSING_PROVIDER_REFERENCE, creating false reconciliation evidence.
 * Provider unavailability must classify PROVIDER_UNAVAILABLE (fail closed,
 * sanitized) while previously persisted reconciliation evidence remains
 * readable read-only. Docker/Testcontainers are forbidden.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class R0C13G07DisabledReconciliationPostgresTest {

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("sanad.subscription.billing.provider.mode", () -> "DISABLED");
    }

    @Autowired private BillingReconciliationService reconciliationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Set<UUID> tenantIds = new LinkedHashSet<>();
    private final Set<UUID> planIds = new LinkedHashSet<>();

    private UUID tenantId;
    private UUID planId;
    private UUID subscriptionId;
    private UUID billingInvoiceId;
    private UUID paymentAttemptId;

    @BeforeEach
    void setUp() {
        tenantId = seedTenant();
        planId = seedPlan();
        subscriptionId = seedSubscription(tenantId, planId, "ACTIVE", "CURRENT");
        billingInvoiceId = seedBillingInvoice(
                tenantId, subscriptionId, "SAR", 10_350L, Duration.ofDays(-5));
        paymentAttemptId = seedDisabledAttempt(
                tenantId, subscriptionId, billingInvoiceId, 10_350L, "SAR");
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
                // Disposable CI database remains authoritative.
            }
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
    void disabledProviderMustNotClassifyFalseMissingProviderReference() {
        BillingReconciliationService.ReconciliationResult result =
                reconciliationService.reconcileReadOnly(
                        tenantId, "g07-disabled-" + compact(UUID.randomUUID()), null);

        assertThat(result.totalItems()).isEqualTo(1);
        assertThat(result.counts())
                .as("provider unavailability in DISABLED mode must never be misread "
                        + "as a missing provider reference")
                .doesNotContainKey("MISSING_PROVIDER_REFERENCE");
        assertThat(result.counts()).containsEntry("PROVIDER_UNAVAILABLE", 1);

        Map<String, Object> item = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT classification, state, details_metadata "
                        + "FROM subscription_billing_reconciliation_items "
                        + "WHERE tenant_id = ? AND payment_attempt_id = ?",
                tenantId, paymentAttemptId));
        assertThat(item.get("classification")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(item.get("state")).isEqualTo("OPEN");
    }

    @Test
    void persistedReconciliationEvidenceRemainsReadableInDisabledMode() {
        String key = "g07-disabled-read-" + compact(UUID.randomUUID());
        BillingReconciliationService.ReconciliationResult first =
                reconciliationService.reconcileReadOnly(tenantId, key, null);
        assertThat(first.replay()).isFalse();

        // re-read path: same key returns the persisted run, healthy and read-only
        BillingReconciliationService.ReconciliationResult replay =
                reconciliationService.reconcileReadOnly(tenantId, key, null);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.totalItems()).isEqualTo(first.totalItems());
        assertThat(replay.counts()).isEqualTo(first.counts());

        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_reconciliation_items "
                        + "WHERE tenant_id = ?",
                tenantId))).isEqualTo(1L);
        assertThat(inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT state FROM subscription_billing_reconciliation_runs "
                        + "WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, first.runId()))).isEqualTo("COMPLETED");
    }

    @Test
    void disabledReconciliationMutatesOnlyItsOwnEvidence() {
        long financePaymentsBefore = scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?", tenantId);
        Map<String, Object> subscriptionBefore = jdbc.queryForMap(
                "SELECT status, billing_state FROM tenant_subscriptions WHERE id = ?",
                subscriptionId);
        Map<String, Object> invoiceBefore = jdbc.queryForMap(
                "SELECT status, amount_paid_minor FROM billing_invoices "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, billingInvoiceId);
        Map<String, Object> attemptBefore = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT state FROM subscription_billing_payment_attempts "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, paymentAttemptId));

        reconciliationService.reconcileReadOnly(
                tenantId, "g07-disabled-mutation-" + compact(UUID.randomUUID()), null);

        assertThat(scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?", tenantId))
                .isEqualTo(financePaymentsBefore);
        assertThat(jdbc.queryForMap(
                "SELECT status, billing_state FROM tenant_subscriptions WHERE id = ?",
                subscriptionId)).isEqualTo(subscriptionBefore);
        assertThat(jdbc.queryForMap(
                "SELECT status, amount_paid_minor FROM billing_invoices "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, billingInvoiceId)).isEqualTo(invoiceBefore);
        assertThat(inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT state FROM subscription_billing_payment_attempts "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, paymentAttemptId))).isEqualTo(attemptBefore);
    }

    // ------------------------------------------------------------------
    // helpers (raw SQL seeds; the DISABLED provider bean must not be invoked)
    // ------------------------------------------------------------------

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G07.0 DIS " + compact(id), "r13-g070d-" + compact(id),
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
                        + "VALUES (?, ?, ?, 'R13 G07.0 disabled test', 'ACTIVE', 'SAR', "
                        + "10350, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G070D-" + compact(id), "G07.0 Disabled Plan " + compact(id),
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
                        + "'R13 G07.0 disabled reconciliation invoice', ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G070D-" + compact(id), currency,
                subtotal, totalMinor,
                Timestamp.from(now.minus(Duration.ofDays(30))),
                Timestamp.from(now),
                Timestamp.from(now.plus(dueOffset)),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedDisabledAttempt(
            UUID tenant,
            UUID subscription,
            UUID invoice,
            long amountMinor,
            String currency
    ) {
        String suffix = compact(UUID.randomUUID());
        UUID customerId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        inTenant(tenant, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_provider_customers "
                            + "(id, tenant_id, provider, provider_customer_ref) "
                            + "VALUES (?, ?, 'DISABLED', ?)",
                    customerId, tenant, "g070d-customer-" + suffix);
            jdbc.update(
                    "INSERT INTO subscription_billing_payment_attempts "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, provider_customer_id, "
                            + "provider, provider_payment_ref, idempotency_key, state, amount_minor, currency_code) "
                            + "VALUES (?, ?, ?, ?, ?, 'DISABLED', ?, ?, 'SUCCEEDED', ?, ?)",
                    attemptId, tenant, subscription, invoice, customerId,
                    "ref-disabled-" + suffix, "g070d-attempt-" + suffix,
                    amountMinor, currency);
            return null;
        });
        return attemptId;
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

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
}
