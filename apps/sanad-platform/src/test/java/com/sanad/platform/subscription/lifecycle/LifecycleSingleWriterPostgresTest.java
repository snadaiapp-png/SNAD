package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CancelSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-7 — Lifecycle Status Single-Writer Convergence (RED battery).
 *
 * <p>Proves, on PostgreSQL Direct, that every production lifecycle-status
 * writer on the pristine predecessor bypasses the canonical
 * {@code SubscriptionCommandService} authority — no
 * {@code subscription_commands} ledger row, no {@code SubscriptionLifecycle}
 * validation, and (for billing) a swallowed best-effort mirror that can leave
 * {@code billing_state} and {@code status} divergent (partial state):</p>
 *
 * <ul>
 *   <li>RED-01 legacy immediate cancel writes status directly (no CANCEL ledger).</li>
 *   <li>RED-02 legacy resume writes ACTIVE directly (no RESUME ledger).</li>
 *   <li>RED-03 legacy renew writes ACTIVE directly (no RENEW ledger).</li>
 *   <li>RED-04a scheduled cancel records no SCHEDULE_CANCELLATION ledger row.</li>
 *   <li>RED-04b scheduled-cancel application writes CANCELLED directly (no CANCEL ledger).</li>
 *   <li>RED-05 billing CURRENT→PAST_DUE writes status directly (no MARK_PAST_DUE ledger).</li>
 *   <li>RED-06 billing →SUSPENDED writes status directly (no SUSPEND ledger).</li>
 *   <li>RED-07 billing payment recovery writes ACTIVE directly (no PAYMENT_RECEIVED ledger).</li>
 *   <li>RED-08 provisioning validation writes ACTIVE directly (no ACTIVATE ledger).</li>
 *   <li>RED-09 billing recovery resurrects a CANCELLED subscription to ACTIVE.</li>
 *   <li>RED-10 billing/lifecycle partial state: billing_state commits while the
 *       status write fails silently (swallowed exception).</li>
 *   <li>RED-11 legacy renew from SUSPENDED bypasses the canonical RENEW
 *       rejection (SubscriptionLifecycle declares RENEW-from-SUSPENDED illegal).</li>
 *   <li>RED-12 provisioning from PAUSED bypasses the canonical ACTIVATE
 *       rejection (ACTIVATE only accepts DRAFT, the PENDING states and the
 *       TRIAL states).</li>
 * </ul>
 *
 * <p>Contract guards (pass on pristine AND post-fix — canonical authority
 * itself must not regress):</p>
 * <ul>
 *   <li>GUARD-01 canonical command normal transition (status + ledger + audit + event).</li>
 *   <li>GUARD-02 canonical illegal transition rejected with no writes.</li>
 * </ul>
 */
class LifecycleSingleWriterPostgresTest {

    private JdbcTemplate jdbc;
    private SaasAdministrationService legacy;
    private BillingStateService billing;
    private ProvisioningJobRunner provisioning;
    private PlatformAuditService audit;
    private PlatformAuditService billingAudit;
    private List<Object> publishedEvents;
    private TransactionTemplate transactions;

    private UUID tenant;
    private UUID planA;
    private UUID versionA;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "LifecycleSingleWriterPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping LifecycleSingleWriterPostgresTest.");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @BeforeEach
    void migrateAndSeed() {
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"));
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl, user, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));

        audit = Mockito.mock(PlatformAuditService.class);
        billingAudit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);
        billing = new BillingStateService(jdbc, billingAudit);
        provisioning = new ProvisioningJobRunner(jdbc);

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        versionA = UUID.randomUUID();
        seedTenant(tenant);
        seedPlan(planA, "R0C7-A");
        seedPlanVersion(versionA, planA, 1, "ACTIVE", 30000L);
    }

    // ---------------------------------------------------------------
    // RED-01 — legacy immediate cancel bypasses the canonical ledger
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-01: legacy immediate cancel writes status directly — no CANCEL ledger row")
    void red01_legacyImmediateCancelBypassesCanonicalLedger() {
        UUID sub = createSubscription(tenant, planA);

        transactions.executeWithoutResult(status ->
                legacy.cancelSubscription(sub, new CancelSubscriptionRequest(true, "r0c7 red"), null));

        // Observable legacy behavior (must be preserved by the convergence).
        assertThat(subscriptionField(sub, "status")).isEqualTo("CANCELLED");
        assertThat(subscriptionField(sub, "cancelled_at")).isNotNull();
        assertThat(subscriptionField(sub, "cancel_at_period_end")).isEqualTo(Boolean.FALSE);
        // Desired converged state: the transition is ledgered canonically.
        assertThat(ledgerCount(sub, "CANCEL")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-02 — legacy resume writes ACTIVE directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-02: legacy resume of a CANCELLED subscription writes ACTIVE directly — no RESUME ledger row")
    void red02_legacyResumeWritesActiveDirectly() {
        UUID sub = createSubscription(tenant, planA);
        transactions.executeWithoutResult(status ->
                legacy.cancelSubscription(sub, new CancelSubscriptionRequest(true, "r0c7 red"), null));

        transactions.executeWithoutResult(status -> legacy.resumeSubscription(sub, null));

        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(subscriptionField(sub, "cancelled_at")).isNull();
        assertThat(invoiceCount(sub, "Subscription resumed")).isEqualTo(1L);
        assertThat(ledgerCount(sub, "RESUME")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-03 — legacy renew writes ACTIVE directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-03: legacy renew writes ACTIVE directly — no RENEW ledger row")
    void red03_legacyRenewWritesActiveDirectly() {
        UUID sub = createSubscription(tenant, planA);

        transactions.executeWithoutResult(status -> legacy.renewSubscription(sub, null));

        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(invoiceCount(sub, "Subscription renewal")).isEqualTo(1L);
        assertThat(ledgerCount(sub, "RENEW")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-04a — scheduled cancel records no lifecycle ledger row
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-04a: scheduling a cancellation records no SCHEDULE_CANCELLATION ledger row")
    void red04a_scheduledCancelRecordsNoLedgerRow() {
        UUID sub = createSubscription(tenant, planA);

        transactions.executeWithoutResult(status ->
                legacy.cancelSubscription(sub, new CancelSubscriptionRequest(false, "r0c7 red"), null));

        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(subscriptionField(sub, "cancel_at_period_end")).isEqualTo(Boolean.TRUE);
        assertThat(ledgerCount(sub, "SCHEDULE_CANCELLATION")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-04b — scheduled-cancel application writes CANCELLED directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-04b: scheduled-cancel application at renewal writes CANCELLED directly — no CANCEL ledger row")
    void red04b_scheduledCancelApplicationWritesDirectly() {
        UUID sub = createSubscription(tenant, planA);
        transactions.executeWithoutResult(status ->
                legacy.cancelSubscription(sub, new CancelSubscriptionRequest(false, "r0c7 red"), null));

        transactions.executeWithoutResult(status -> legacy.renewSubscription(sub, null));

        assertThat(subscriptionField(sub, "status")).isEqualTo("CANCELLED");
        assertThat(subscriptionField(sub, "cancelled_at")).isNotNull();
        assertThat(subscriptionField(sub, "cancel_at_period_end")).isEqualTo(Boolean.FALSE);
        assertThat(ledgerCount(sub, "CANCEL")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-05 — billing CURRENT→PAST_DUE writes status directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-05: billing CURRENT→PAST_DUE writes status directly — no MARK_PAST_DUE ledger row")
    void red05_billingPastDueWritesStatusDirectly() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "ACTIVE", "CURRENT");
        insertOverdueInvoice(sub, Instant.now().minus(5, ChronoUnit.DAYS));

        String newState = billing.evaluateAndTransition(tenant);

        assertThat(newState).isEqualTo("PAST_DUE");
        assertThat(subscriptionField(sub, "status")).isEqualTo("PAST_DUE");
        assertThat(ledgerCount(sub, "MARK_PAST_DUE")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-06 — billing →SUSPENDED writes status directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-06: billing →SUSPENDED writes status directly — no SUSPEND ledger row")
    void red06_billingSuspendedWritesStatusDirectly() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "ACTIVE", "CURRENT");
        insertOverdueInvoice(sub, Instant.now().minus(10, ChronoUnit.DAYS));

        String newState = billing.evaluateAndTransition(tenant);

        assertThat(newState).isEqualTo("SUSPENDED");
        assertThat(subscriptionField(sub, "status")).isEqualTo("SUSPENDED");
        assertThat(ledgerCount(sub, "SUSPEND")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-07 — billing payment recovery writes ACTIVE directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-07: billing payment recovery (SUSPENDED→CURRENT) writes ACTIVE directly — no PAYMENT_RECEIVED ledger row")
    void red07_billingRecoveryWritesActiveDirectly() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "SUSPENDED", "SUSPENDED");

        String newState = billing.evaluateAndTransition(tenant);

        assertThat(newState).isEqualTo("CURRENT");
        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sub, "PAYMENT_RECEIVED")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-08 — provisioning validation writes ACTIVE directly
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-08: provisioning validation writes ACTIVE directly — no ACTIVATE ledger row")
    void red08_provisioningWritesActiveDirectly() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "PENDING_ACTIVATION", "CURRENT");
        seedPlanItem(sub, tenant, planA, versionA);
        UUID job = enqueueProvisioningJob(sub);

        ProvisioningJobRunner.JobOutcome outcome = provisioning.run(job);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sub, "ACTIVATE")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-09 — billing recovery resurrects a CANCELLED subscription
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-09: billing recovery resurrects a CANCELLED subscription to ACTIVE (contract-conflicting transition)")
    void red09_billingRecoveryResurrectsCancelledSubscription() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "CANCELLED", "PAST_DUE");
        insertOverdueInvoice(sub, Instant.now().minus(10, ChronoUnit.DAYS));
        jdbc.update("UPDATE billing_invoices SET status = 'PAID', amount_paid_minor = total_minor, "
                + "paid_at = NOW() WHERE tenant_id = ?", tenant);

        billing.evaluateAndTransition(tenant);

        // A cancelled subscription must stay cancelled: the lifecycle authority
        // declares PAYMENT_RECEIVED-from-CANCELLED illegal (CANCELLED is terminal).
        assertThat(subscriptionField(sub, "status")).isEqualTo("CANCELLED");
        // The billing pair must not diverge either: without a legal lifecycle
        // transition the billing_state change is skipped atomically.
        assertThat(subscriptionField(sub, "billing_state")).isEqualTo("PAST_DUE");
    }

    // ---------------------------------------------------------------
    // RED-10 — billing/lifecycle partial state (swallowed mirror failure)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-10: billing_state commits while the status write fails silently — partial state")
    void red10_billingLifecyclePartialState() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "ACTIVE", "CURRENT");
        insertOverdueInvoice(sub, Instant.now().minus(10, ChronoUnit.DAYS));

        jdbc.execute("""
                        CREATE OR REPLACE FUNCTION r0c7_block_status() RETURNS trigger AS $$
                        BEGIN
                            RAISE EXCEPTION 'r0c7: status write blocked';
                        END;
                        $$ LANGUAGE plpgsql
                        """);
        jdbc.execute("""
                        CREATE TRIGGER r0c7_status_guard BEFORE UPDATE OF status ON tenant_subscriptions
                        FOR EACH ROW EXECUTE FUNCTION r0c7_block_status()
                        """);
        try {
            // The lifecycle status write fails; the billing_state write must
            // roll back with it — one transaction, no partial state.
            assertThatThrownBy(() -> billing.evaluateAndTransition(tenant))
                    .isInstanceOf(Exception.class);
            assertThat(subscriptionField(sub, "billing_state")).isEqualTo("CURRENT");
            assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS r0c7_status_guard ON tenant_subscriptions");
            jdbc.execute("DROP FUNCTION IF EXISTS r0c7_block_status()");
        }
    }

    // ---------------------------------------------------------------
    // RED-11 — legacy renew from SUSPENDED bypasses canonical rejection
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-11: legacy renew from SUSPENDED bypasses the canonical RENEW rejection")
    void red11_legacyRenewFromSuspendedBypassesCanonicalRejection() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "SUSPENDED", "CURRENT");

        // SubscriptionLifecycle declares RENEW-from-SUSPENDED ILLEGAL
        // (unit-tested in SubscriptionLifecycleTest). The legacy engine must
        // fail closed on the canonical rejection instead of force-writing ACTIVE.
        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> legacy.renewSubscription(sub, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(subscriptionField(sub, "status")).isEqualTo("SUSPENDED");
        assertThat(ledgerCount(sub, "RENEW")).isZero();
    }

    // ---------------------------------------------------------------
    // RED-12 — provisioning from PAUSED bypasses canonical ACTIVATE rejection
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-12: provisioning from PAUSED bypasses the canonical ACTIVATE rejection")
    void red12_provisioningFromPausedBypassesCanonicalRejection() {
        UUID sub = seedSubscription(UUID.randomUUID(), tenant, planA, versionA, "PAUSED", "CURRENT");
        seedPlanItem(sub, tenant, planA, versionA);
        UUID job = enqueueProvisioningJob(sub);

        ProvisioningJobRunner.JobOutcome outcome = provisioning.run(job);

        // ACTIVATE only accepts DRAFT/PENDING_ACTIVATION/PENDING_PAYMENT/TRIAL/
        // TRIALING — a PAUSED subscription must fail closed, not force-ACTIVE.
        assertThat(outcome.status()).isEqualTo("FAILED");
        assertThat(subscriptionField(sub, "status")).isEqualTo("PAUSED");
        assertThat(ledgerCount(sub, "ACTIVATE")).isZero();
    }

    // ---------------------------------------------------------------
    // GUARD-01 — canonical command normal transition (contract guard)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GUARD-01: canonical lifecycle command performs a normal transition with full side effects")
    void guard01_canonicalCommandNormalTransition() {
        UUID sub = createSubscription(tenant, planA);
        SubscriptionCommandService canonical =
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add);

        SubscriptionCommandService.CommandResult result =
                transactions.execute(status -> canonical.execute(sub, "SUSPEND", "r0c7 guard", null, null));

        assertThat(result.fromStatus()).isEqualTo("ACTIVE");
        assertThat(result.toStatus()).isEqualTo("SUSPENDED");
        assertThat(subscriptionField(sub, "status")).isEqualTo("SUSPENDED");
        assertThat(ledgerCount(sub, "SUSPEND")).isEqualTo(1L);
        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_SUSPEND"), Mockito.eq("subscription"),
                Mockito.eq(sub.toString()), Mockito.eq("r0c7 guard"),
                Mockito.eq("ACTIVE"), Mockito.eq("SUSPENDED"));
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionSuspendedEvent)
                .count()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // GUARD-02 — canonical illegal transition rejected with no writes
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GUARD-02: canonical illegal transition is rejected with no writes")
    void guard02_canonicalIllegalTransitionRejectedNoWrites() {
        UUID sub = createSubscription(tenant, planA);
        SubscriptionCommandService canonical =
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add);
        int eventsBefore = publishedEvents.size();

        assertThatThrownBy(() ->
                transactions.execute(status -> canonical.execute(sub, "RESUME", "r0c7 guard", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");

        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sub, "RESUME")).isZero();
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore);
    }

    // ---------------------------------------------------------------
    // Seeding helpers
    // ---------------------------------------------------------------

    private void seedTenant(UUID id) {
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8));
    }

    private void seedPlan(UUID id, String code) {
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb,
                                                created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', 30000, 300000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code);
    }

    private void seedPlanVersion(UUID id, UUID planId, int number, String status, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NOW(), 'SAR', ?, ?, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, planId, number, status, monthlyMinor, monthlyMinor * 10);
    }

    private UUID seedSubscription(UUID id, UUID tenantId, UUID planId, UUID versionId,
                                  String status, String billingState) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, billing_state, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'MONTHLY', 2, 0, NOW(), NOW(), NOW() + INTERVAL '30 days',
                                FALSE, ?, NOW(), NOW())
                        """,
                id, tenantId, planId, versionId, status, billingState);
        return id;
    }

    private void seedPlanItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID versionId) {
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        plan_id, plan_version_id, name_snapshot, quantity,
                                                        unit_amount_minor, currency_code, status,
                                                        created_at, updated_at)
                        VALUES (?, ?, ?, 'PLAN', ?, ?, ?, 1, 30000, 'SAR', 'ACTIVE', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, subscriptionId, planId, versionId, "PLAN " + planId);
    }

    private UUID enqueueProvisioningJob(UUID subscriptionId) {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO provisioning_jobs (id, tenant_id, subscription_id, action, status,
                                                       attempts, created_at, updated_at)
                        VALUES (?, ?, ?, 'PROVISION_SUBSCRIPTION', 'PENDING', 0, NOW(), NOW())
                        """,
                jobId, tenant, subscriptionId);
        return jobId;
    }

    private void insertOverdueInvoice(UUID subscriptionId, Instant dueAt) {
        UUID invoiceId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update("""
                        INSERT INTO billing_invoices (id, tenant_id, subscription_id, invoice_number, status,
                                                      currency_code, subtotal_minor, credit_applied_minor,
                                                      tax_minor, total_minor, amount_paid_minor,
                                                      period_start, period_end, due_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 30000, 0, 4500, 34500, 0, NOW(),
                                NOW() + INTERVAL '30 days', ?, NOW(), NOW())
                        """,
                invoiceId, tenant, subscriptionId, "INV-R0C7-" + invoiceId.toString().substring(0, 8),
                Timestamp.from(dueAt));
    }

    private UUID createSubscription(UUID tenantId, UUID planId) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, 0), null).id());
    }

    // ---------------------------------------------------------------
    // Observation helpers
    // ---------------------------------------------------------------

    private Object subscriptionField(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long ledgerCount(UUID subscriptionId, String command) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = ?",
                Long.class, subscriptionId, command);
        return count == null ? 0L : count;
    }

    private long invoiceCount(UUID subscriptionId, String description) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ? AND description = ?",
                Long.class, subscriptionId, description);
        return count == null ? 0L : count;
    }
}
