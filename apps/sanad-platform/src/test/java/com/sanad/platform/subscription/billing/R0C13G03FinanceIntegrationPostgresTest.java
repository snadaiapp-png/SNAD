package com.sanad.platform.subscription.billing;

import com.sanad.platform.subscription.billing.domain.SubscriptionFinancePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R13-G03 PostgreSQL Direct acceptance for the subscription -> Finance boundary.
 *
 * <p>These tests deliberately exercise the real Flyway schema and RLS-backed
 * linkage table. No H2/Testcontainers acceptance is permitted.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class R0C13G03FinanceIntegrationPostgresTest {

    @Autowired private SubscriptionFinancePort financePort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final Set<UUID> tenantIds = new LinkedHashSet<>();
    private final Set<UUID> planIds = new LinkedHashSet<>();

    private UUID tenantId;
    private UUID subscriptionId;
    private UUID billingInvoiceId;

    @BeforeEach
    void setUp() {
        tenantId = seedTenant();
        UUID planId = seedPlan("SAR");
        subscriptionId = seedSubscription(tenantId, planId);
        billingInvoiceId = seedBillingInvoice(
                tenantId, subscriptionId, "SAR",
                10_000L, 1_000L, 1_350L, 10_350L);
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
                // Best-effort cleanup; the disposable CI database is authoritative.
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
    void replayCreatesExactlyOneFinanceInvoiceAndOneLine() {
        var first = financePort.ensureInvoice(tenantId, billingInvoiceId);
        var second = financePort.ensureInvoice(tenantId, billingInvoiceId);

        assertThat(second.financeInvoiceId()).isEqualTo(first.financeInvoiceId());
        assertThat(second.externalReference()).isEqualTo("SCP_INVOICE:" + billingInvoiceId);

        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_invoices WHERE tenant_id = ? AND external_reference = ?",
                tenantId, "SCP_INVOICE:" + billingInvoiceId))).isEqualTo(1L);
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_invoice_lines WHERE tenant_id = ? AND invoice_id = ?",
                tenantId, first.financeInvoiceId()))).isEqualTo(1L);
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_finance_links WHERE tenant_id = ? AND billing_invoice_id = ?",
                tenantId, billingInvoiceId))).isEqualTo(1L);

        var amounts = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT subtotal, tax_amount, total_amount, currency FROM finance_invoices "
                        + "WHERE tenant_id = ? AND id = ?",
                tenantId, first.financeInvoiceId()));
        assertThat((BigDecimal) amounts.get("subtotal")).isEqualByComparingTo("90.00");
        assertThat((BigDecimal) amounts.get("tax_amount")).isEqualByComparingTo("13.50");
        assertThat((BigDecimal) amounts.get("total_amount")).isEqualByComparingTo("103.50");
        assertThat(amounts.get("currency")).isEqualTo("SAR");
    }

    @Test
    void settlementReplayCreatesExactlyOneCompletedFinancePayment() {
        UUID settlementId = UUID.randomUUID();

        var first = financePort.recordSettlement(
                tenantId, billingInvoiceId, settlementId, 10_350L, "SAR");
        var second = financePort.recordSettlement(
                tenantId, billingInvoiceId, settlementId, 10_350L, "SAR");

        assertThat(second.financePaymentId()).isEqualTo(first.financePaymentId());
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ? AND payment_number = ?",
                tenantId, first.paymentNumber()))).isEqualTo(1L);

        var payment = inTenant(tenantId, () -> jdbc.queryForMap(
                "SELECT amount, currency, status, invoice_id, reference_type, reference_id "
                        + "FROM finance_payments WHERE tenant_id = ? AND id = ?",
                tenantId, first.financePaymentId()));
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo("103.50");
        assertThat(payment.get("currency")).isEqualTo("SAR");
        assertThat(payment.get("status")).isEqualTo("COMPLETED");
        assertThat(payment.get("invoice_id")).isEqualTo(first.financeInvoiceId());
        assertThat(payment.get("reference_type")).isEqualTo("SCP_SETTLEMENT");
        assertThat(payment.get("reference_id")).isEqualTo(settlementId);

        assertThat(inTenant(tenantId, () -> jdbc.queryForObject(
                "SELECT status FROM finance_invoices WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, first.financeInvoiceId()))).isEqualTo("PAID");
    }

    @Test
    void settlementAmountAndCurrencyMismatchFailClosedWithoutDuplicatePayment() {
        UUID settlementId = UUID.randomUUID();
        financePort.recordSettlement(
                tenantId, billingInvoiceId, settlementId, 10_350L, "SAR");

        assertThatThrownBy(() -> financePort.recordSettlement(
                tenantId, billingInvoiceId, settlementId, 10_349L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("amount mismatch");

        assertThatThrownBy(() -> financePort.recordSettlement(
                tenantId, billingInvoiceId, settlementId, 10_350L, "USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currency mismatch");

        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_payments WHERE tenant_id = ?",
                tenantId))).isEqualTo(1L);
    }

    @Test
    void crossTenantBillingInvoiceCannotBeLinked() {
        UUID otherTenant = seedTenant();

        assertThatThrownBy(() -> financePort.ensureInvoice(otherTenant, billingInvoiceId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Billing invoice not found");

        assertThat(inTenant(otherTenant, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_finance_links WHERE tenant_id = ?",
                otherTenant))).isZero();
    }

    @Test
    void sameTenantForeignKeyRejectsCrossTenantFinanceInvoice() {
        UUID otherTenant = seedTenant();
        UUID otherFinanceInvoice = UUID.randomUUID();
        inTenant(otherTenant, () -> {
            seedFinanceInvoice(otherTenant, otherFinanceInvoice, "OTHER:" + otherFinanceInvoice);
            return null;
        });

        assertThatThrownBy(() -> inTenant(tenantId, () -> {
            jdbc.update(
                    "INSERT INTO subscription_billing_finance_links "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, finance_invoice_id, external_reference) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId, billingInvoiceId,
                    otherFinanceInvoice, "SCP_INVOICE:" + billingInvoiceId);
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void financeFailureAfterInvoiceWriteRollsBackWholeIntegrationTransaction() {
        UUID conflictingBillingInvoice = seedBillingInvoice(
                tenantId, subscriptionId, "SAR",
                5_000L, 0L, 0L, 5_000L);
        UUID targetBillingInvoice = seedBillingInvoice(
                tenantId, subscriptionId, "SAR",
                7_000L, 0L, 0L, 7_000L);
        String targetExternalRef = "SCP_INVOICE:" + targetBillingInvoice;

        UUID dummyFinanceInvoice = UUID.randomUUID();
        inTenant(tenantId, () -> {
            seedFinanceInvoice(tenantId, dummyFinanceInvoice, "DUMMY:" + dummyFinanceInvoice);
            jdbc.update(
                    "INSERT INTO subscription_billing_finance_links "
                            + "(id, tenant_id, subscription_id, billing_invoice_id, finance_invoice_id, external_reference) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, subscriptionId, conflictingBillingInvoice,
                    dummyFinanceInvoice, targetExternalRef);
            return null;
        });

        assertThatThrownBy(() -> financePort.ensureInvoice(tenantId, targetBillingInvoice))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM finance_invoices WHERE tenant_id = ? AND external_reference = ?",
                tenantId, targetExternalRef))).isZero();
        assertThat(inTenant(tenantId, () -> scalarLong(
                "SELECT COUNT(*) FROM subscription_billing_finance_links "
                        + "WHERE tenant_id = ? AND billing_invoice_id = ?",
                tenantId, targetBillingInvoice))).isZero();
    }

    private UUID seedTenant() {
        UUID id = UUID.randomUUID();
        tenantIds.add(id);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                id, "R13 G03 " + shortId(id), "r13-g03-" + shortId(id), now, now);
        return id;
    }

    private UUID seedPlan(String currency) {
        UUID id = UUID.randomUUID();
        planIds.add(id);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO saas_plans "
                        + "(id, code, name, description, status, currency_code, monthly_price_minor, annual_price_minor, "
                        + "trial_days, max_users, max_organizations, storage_mb, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'R13 G03 test', 'ACTIVE', ?, 10000, 100000, 14, 10, 3, 1024, ?, ?)",
                id, "G03-" + shortId(id), "G03 Plan " + shortId(id), currency, now, now);
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

    private UUID seedBillingInvoice(
            UUID tenant,
            UUID subscription,
            String currency,
            long subtotalMinor,
            long creditMinor,
            long taxMinor,
            long totalMinor
    ) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, "
                        + "subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor, "
                        + "description, period_start, period_end, due_at, paid_at, payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, NULL, NULL, ?, ?)",
                id, tenant, subscription, "BILL-G03-" + shortId(id), currency,
                subtotalMinor, creditMinor, taxMinor, totalMinor,
                "R13 G03 subscription invoice " + shortId(id),
                Timestamp.from(now.minus(Duration.ofDays(1))),
                Timestamp.from(now.plus(Duration.ofDays(29))),
                Timestamp.from(now.plus(Duration.ofDays(14))),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private void seedFinanceInvoice(UUID tenant, UUID invoiceId, String externalReference) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO finance_invoices "
                        + "(id, tenant_id, invoice_number, customer_type, customer_id, customer_name, "
                        + "issue_date, due_date, currency, subtotal, tax_amount, total_amount, paid_amount, "
                        + "status, notes, version, external_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'MANUAL', NULL, 'R13 G03 dummy', ?, ?, 'SAR', "
                        + "1.00, 0.00, 1.00, 0.00, 'ISSUED', 'test', 0, ?, ?, ?)",
                invoiceId, tenant, "FIN-G03-" + shortId(invoiceId),
                java.sql.Date.valueOf(LocalDate.now()),
                java.sql.Date.valueOf(LocalDate.now().plusDays(14)),
                externalReference, Timestamp.from(now), Timestamp.from(now));
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

    private static String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    @SuppressWarnings("unused")
    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
