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

/** PostgreSQL Direct convergence contracts for effective-subscription dunning. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class BillingStateConvergencePostgresTest {

    @Autowired BillingStateService billingStateService;
    @Autowired JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID planId;
    private UUID effectiveSubscriptionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planId = UUID.randomUUID();
        effectiveSubscriptionId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, 'Dunning Convergence', ?, 'ACTIVE', ?, ?)",
                tenantId, "dun-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO saas_plans (id,code,name,status,currency_code,monthly_price_minor,annual_price_minor,trial_days,max_users,max_organizations,storage_mb,created_at,updated_at) "
                        + "VALUES (?, ?, 'Dunning Plan', 'ACTIVE', 'SAR', 10000, 100000, 0, 10, 2, 1024, ?, ?)",
                planId, "DUN-" + planId.toString().substring(0, 8), now, now);
        insertSubscription(effectiveSubscriptionId, "ACTIVE", "CURRENT", now);
    }

    @Test
    void currentSubscriptionSuspendsDirectlyWhenSuspendGraceExceeded() {
        // Main's canonical dunning doctrine: the dunning evaluation is
        // convergent on the OVERDUE AGE, not on evaluation count — a CURRENT
        // subscription whose invoice already exceeds the suspend grace
        // transitions directly to SUSPENDED in a single evaluation.
        insertOverdueInvoice(effectiveSubscriptionId, Instant.now().minus(10, ChronoUnit.DAYS));

        String first = billingStateService.evaluateAndTransition(tenantId);

        assertThat(first).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                "SELECT billing_state FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("SUSPENDED");

        String second = billingStateService.evaluateAndTransition(tenantId);

        assertThat(second).isEqualTo("SUSPENDED");
    }

    @Test
    void historicalTerminalInvoiceCannotDunnEffectiveSuccessor() {
        UUID historicalId = UUID.randomUUID();
        Timestamp historicalCreated = Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS));
        insertSubscription(historicalId, "EXPIRED", "CANCELLED", historicalCreated);
        insertOverdueInvoice(historicalId, Instant.now().minus(20, ChronoUnit.DAYS));

        String state = billingStateService.evaluateAndTransition(tenantId);

        assertThat(state).isEqualTo("CURRENT");
        assertThat(jdbc.queryForObject(
                "SELECT billing_state FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("CURRENT");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("ACTIVE");
    }

    @Test
    void repeatedDunningEvaluationIsIdempotent() {
        insertOverdueInvoice(effectiveSubscriptionId, Instant.now().minus(5, ChronoUnit.DAYS));

        String first = billingStateService.evaluateAndTransition(tenantId);
        String second = billingStateService.evaluateAndTransition(tenantId);

        assertThat(first).isEqualTo("PAST_DUE");
        assertThat(second).isEqualTo("PAST_DUE");
        assertThat(jdbc.queryForObject(
                "SELECT billing_state FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("PAST_DUE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM tenant_subscriptions WHERE id = ?",
                String.class, effectiveSubscriptionId)).isEqualTo("PAST_DUE");
    }

    private void insertSubscription(UUID id, String status, String billingState, Timestamp createdAt) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenant_subscriptions "
                        + "(id,tenant_id,plan_id,status,billing_cycle,seat_quantity,credit_balance_minor,started_at,current_period_start,current_period_end,cancel_at_period_end,billing_state,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'MONTHLY', 1, 0, ?, ?, ?, FALSE, ?, ?, ?)",
                id, tenantId, planId, status, createdAt, createdAt,
                Timestamp.from(createdAt.toInstant().plus(30, ChronoUnit.DAYS)), billingState, createdAt, now);
    }

    private void insertOverdueInvoice(UUID subscriptionId, Instant dueAt) {
        UUID invoiceId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO billing_invoices "
                        + "(id,tenant_id,subscription_id,invoice_number,status,currency_code,subtotal_minor,credit_applied_minor,tax_minor,total_minor,amount_paid_minor,period_start,period_end,due_at,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 10000, 0, 1500, 11500, 0, ?, ?, ?, ?, ?)",
                invoiceId, tenantId, subscriptionId, "INV-DUN-" + invoiceId.toString().substring(0, 8),
                now, Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)), Timestamp.from(dueAt), now, now);
    }
}
