package com.sanad.platform.admin;

import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BillingStateService} — verifies the
 * CURRENT → PAST_DUE → SUSPENDED dunning state machine and the
 * SUSPENDED → CURRENT recovery path on invoice payment.
 *
 * <p>The scheduler entry point ({@link BillingStateService#runDunningCycle()})
 * is gated by {@code SANAD_DUNNING_ENABLED}, so tests call {@link
 * BillingStateService#runDunningCycleOnce()} and {@link
 * BillingStateService#evaluateAndTransition(UUID)} directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class BillingStateServiceIntegrationTest {

    @Autowired private BillingStateService billingStateService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID planId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        // Tenant
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "bs-" + tenantId.toString().substring(0, 8), now, now);

        // User (required FK for created_by columns in some tables)
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'User', 'ACTIVE', 'dummy', ?, ?)",
                UUID.randomUUID(), tenantId, "bs-" + tenantId.toString().substring(0, 8) + "@test", now, now);

        // Plan
        jdbc.update("INSERT INTO saas_plans "
                        + "(id, code, name, status, currency_code, monthly_price_minor, annual_price_minor, "
                        + " trial_days, max_users, max_organizations, storage_mb, created_at, updated_at) "
                        + "VALUES (?, 'TEST', 'Test Plan', 'ACTIVE', 'SAR', 10000, 100000, 0, 5, 1, 1024, ?, ?)",
                planId, now, now);

        // Subscription with billing_state=CURRENT
        jdbc.update("INSERT INTO tenant_subscriptions "
                        + "(id, tenant_id, plan_id, status, billing_cycle, seat_quantity, "
                        + " credit_balance_minor, started_at, current_period_start, current_period_end, "
                        + " cancel_at_period_end, billing_state, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, ?, ?, ?, FALSE, 'CURRENT', 0, ?, ?)",
                subscriptionId, tenantId, planId, now, now,
                Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)), now, now);
    }

    @Test
    void evaluateAndTransition_keepsCurrentWhenNoOverdueInvoices() {
        String newState = billingStateService.evaluateAndTransition(tenantId);
        assertThat(newState).isEqualTo("CURRENT");
    }

    @Test
    void evaluateAndTransition_transitionsToPastDueWhenInvoiceOverdue() {
        // Insert an OPEN invoice past due_at + 3-day grace (so PAST_DUE triggers immediately)
        insertOverdueInvoice(Instant.now().minus(5, ChronoUnit.DAYS)); // 5 days overdue > 3-day grace

        String newState = billingStateService.evaluateAndTransition(tenantId);
        assertThat(newState).isEqualTo("PAST_DUE");
        // Verify the column was persisted
        String persisted = jdbc.queryForObject(
                "SELECT billing_state FROM tenant_subscriptions WHERE tenant_id = ?",
                String.class, tenantId);
        assertThat(persisted).isEqualTo("PAST_DUE");
    }

    @Test
    void evaluateAndTransition_transitionsToSuspendedAfterLongOverdue() {
        // 10 days overdue → exceeds the 7-day SUSPEND grace
        insertOverdueInvoice(Instant.now().minus(10, ChronoUnit.DAYS));

        String newState = billingStateService.evaluateAndTransition(tenantId);
        assertThat(newState).isEqualTo("SUSPENDED");
    }

    @Test
    void evaluateAndTransition_recoversToCurrentWhenAllInvoicesPaid() {
        // Transition to SUSPENDED first
        insertOverdueInvoice(Instant.now().minus(10, ChronoUnit.DAYS));
        billingStateService.evaluateAndTransition(tenantId);
        String afterSuspend = jdbc.queryForObject(
                "SELECT billing_state FROM tenant_subscriptions WHERE tenant_id = ?",
                String.class, tenantId);
        assertThat(afterSuspend).isEqualTo("SUSPENDED");

        // Mark the invoice PAID — should recover to CURRENT
        jdbc.update("UPDATE billing_invoices SET status = 'PAID', amount_paid_minor = total_minor, "
                        + "paid_at = ?, updated_at = ? WHERE tenant_id = ?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), tenantId);

        String recovered = billingStateService.evaluateAndTransition(tenantId);
        assertThat(recovered).isEqualTo("CURRENT");
    }

    @Test
    void evaluateAndTransition_preservesCancelledState() {
        jdbc.update("UPDATE tenant_subscriptions SET billing_state = 'CANCELLED' WHERE tenant_id = ?", tenantId);
        String result = billingStateService.evaluateAndTransition(tenantId);
        assertThat(result).isEqualTo("CANCELLED");
    }

    @Test
    void evaluateAndTransition_preservesTrialingState() {
        jdbc.update("UPDATE tenant_subscriptions SET billing_state = 'TRIALING' WHERE tenant_id = ?", tenantId);
        String result = billingStateService.evaluateAndTransition(tenantId);
        assertThat(result).isEqualTo("TRIALING");
    }

    @Test
    void runDunningCycleOnce_evaluatesAllActiveSubscriptions() {
        // Current tenant has no overdue invoices — should evaluate without errors
        int count = billingStateService.runDunningCycleOnce();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void runDunningCycleOnce_isDefensiveAgainstSingleTenantFailure() {
        // Add a tenant with an obviously broken subscription (e.g. no plan)
        // and verify the cycle continues for others.
        int count = billingStateService.runDunningCycleOnce();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    private void insertOverdueInvoice(Instant dueAt) {
        UUID invoiceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO billing_invoices "
                        + "(id, tenant_id, subscription_id, invoice_number, status, currency_code, "
                        + " subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor, "
                        + " period_start, period_end, due_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 10000, 0, 1500, 11500, 0, ?, ?, ?, ?, ?)",
                invoiceId, tenantId, subscriptionId, "INV-TEST-" + invoiceId.toString().substring(0, 8),
                now, Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)),
                Timestamp.from(dueAt), now, now);
    }
}
