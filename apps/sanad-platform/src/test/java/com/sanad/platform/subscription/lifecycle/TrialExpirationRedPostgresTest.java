package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-8 — Trial Expiration Runtime Driver Closure (RED battery).
 *
 * <p><b>Semantic resolution (order §6-§8) — TRIAL_EXPIRY_SEMANTICS_MATRIX:</b></p>
 *
 * <p>The repository defines two candidate contracts for "trial_ends_at
 * elapses":</p>
 * <ul>
 *   <li><b>Canonical (authoritative):</b> {@code SubscriptionLifecycle.EXPIRE}
 *       maps {@code TRIAL/TRIALING/GRACE_PERIOD → EXPIRED}. The design doc
 *       (2026-08-29 subscription-control-plane-design §3) designates the
 *       transition table as the single source of transition legality and
 *       lists {@code expire} among the domain commands;
 *       {@code SubscriptionCommandService.execute} already wires the full
 *       EXPIRE contract (ledger + {@code SUBSCRIPTION_EXPIRE} audit +
 *       {@code SubscriptionCancelledEvent}); {@code SubscriptionLifecycleTest}
 *       unit-tests the row; R0C-7 plan §20.4 classified the gap as
 *       "EXPIRE has no runtime driver".</li>
 *   <li><b>Stale comment (not authoritative):</b>
 *       {@code BillingStateService} javadoc claims billing_state
 *       {@code TRIALING → CURRENT "when trial_ends_at elapses; handled
 *       elsewhere"} — but {@code evaluateAndTransition} early-returns TRIALING
 *       ("no automatic transitions out of these"), the dunning scan selects
 *       only CURRENT/PAST_DUE/SUSPENDED, and an exhaustive repository search
 *       finds no "elsewhere" (no scheduler, no job, no code path reads
 *       trial_ends_at to transition anything). Additionally, no invoice
 *       scheduler exists (issueRecurringInvoice is called only from
 *       operator-initiated create/resume/renew), so auto-converting a trial
 *       to ACTIVE would produce an ACTIVE subscription with no invoice —
 *       contradicting the billing contract. The sanctioned trial→paid
 *       conversion is the operator-initiated RENEW (which issues the
 *       invoice and clears trial_ends_at).</li>
 * </ul>
 *
 * <p><b>TRIAL_EXPIRATION_CONTRACT = MODEL_A (TRIAL/TRIALING → EXPIRED via
 * canonical EXPIRE).</b> TRIAL_EXPIRY_BILLING_EFFECT = NONE (billing_state
 * authority stays with BillingStateService; the stale comment is corrected
 * in the GREEN commit). INVOICE/CREDIT/PRORATION effect = 0.</p>
 *
 * <p><b>The RED mechanism.</b> This battery must compile on the pristine
 * R0C-7 predecessor, which contains no trial-expiration driver at all — so
 * the driver is located reflectively. On pristine R0C-7 every RED test
 * fails with "no runtime trial-expiration driver exists"; post-R0C-8 the
 * same tests pass and stay as permanent gap-regression guards:</p>
 *
 * <ul>
 *   <li>RED-01 an expired trial remains TRIALING indefinitely without a
 *       driver (the driver must transition it to EXPIRED).</li>
 *   <li>RED-02 no automatic lifecycle ledger (the driver must produce
 *       exactly one canonical EXPIRE ledger row + one SUBSCRIPTION_EXPIRE
 *       audit entry).</li>
 *   <li>RED-03 no runtime entitlement expiration event (the driver must
 *       publish exactly one SubscriptionCancelledEvent).</li>
 *   <li>RED-04 stale comment/contract mismatch captured: the dunning
 *       scheduler does NOT perform the comment's claimed billing
 *       TRIALING→CURRENT transition, while the runtime driver resolves the
 *       lifecycle to EXPIRED and leaves billing_state untouched.</li>
 * </ul>
 *
 * <p><b>GUARD-01 (passes on pristine AND post-fix):</b> the canonical
 * manual EXPIRE command already works through
 * {@code SubscriptionCommandService.execute} — proving the gap is the
 * missing RUNTIME DRIVER, not the command contract.</p>
 */
class TrialExpirationRedPostgresTest {

    private static final String DRIVER_CLASS =
            "com.sanad.platform.subscription.lifecycle.TrialExpirationService";

    private JdbcTemplate jdbc;
    private SaasAdministrationService legacy;
    private BillingStateService billing;
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
                    "TrialExpirationRedPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping TrialExpirationRedPostgresTest.");
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
        billing = new BillingStateService(jdbc, billingAudit,
                new SubscriptionCommandService(jdbc, billingAudit, publishedEvents::add));

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        versionA = UUID.randomUUID();
        seedTenant(tenant);
        seedPlan(planA, "R0C8-RED");
        seedPlanVersion(versionA, planA, 1, "ACTIVE", 30000L);
    }

    // ---------------------------------------------------------------
    // RED-01 — expired trial remains TRIALING indefinitely without driver
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-01: due trial is expired to EXPIRED by the runtime driver (no driver exists on pristine R0C-7)")
    void red01_dueTrialExpiredByRuntimeDriver() {
        UUID sub = createDueTrial(tenant, planA);

        runTrialExpiryCycle();

        assertThat(subscriptionField(sub, "status"))
                .as("a due trial (trial_ends_at elapsed, status TRIALING) must be expired by the runtime driver")
                .isEqualTo("EXPIRED");
    }

    // ---------------------------------------------------------------
    // RED-02 — no automatic lifecycle ledger
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-02: driver produces exactly one canonical EXPIRE ledger row and one SUBSCRIPTION_EXPIRE audit entry")
    void red02_canonicalLedgerAndAuditExactlyOnce() {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);

        runTrialExpiryCycle();

        assertThat(ledgerCount(sub, "EXPIRE"))
                .as("the runtime driver must ledger the expiration through the canonical command authority")
                .isEqualTo(1L);
        assertThat(ledgerFromStatus(sub, "EXPIRE")).isEqualTo("TRIALING");
        assertThat(ledgerToStatus(sub, "EXPIRE")).isEqualTo("EXPIRED");
        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.eq("subscription"),
                Mockito.eq(sub.toString()), Mockito.contains("Trial expired"),
                Mockito.eq("TRIALING"), Mockito.eq("EXPIRED"));
    }

    // ---------------------------------------------------------------
    // RED-03 — no runtime entitlement expiration event
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-03: driver publishes exactly one entitlement expiration event (SubscriptionCancelledEvent)")
    void red03_entitlementExpirationEventExactlyOnce() {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        runTrialExpiryCycle();

        long cancellationEvents = publishedEvents.stream()
                .skip(eventsBefore)
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionCancelledEvent)
                .count();
        assertThat(cancellationEvents)
                .as("trial expiration must disable entitlements through the existing canonical "
                        + "cancellation event path — exactly one event, no second event type")
                .isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // RED-04 — stale comment/contract mismatch captured
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RED-04: stale billing comment captured — dunning never transitions billing TRIALING→CURRENT; driver resolves EXPIRED with billing_state untouched")
    void red04_staleCommentMismatchCaptured() {
        UUID sub = createDueTrial(tenant, planA);
        // Simulate a migration-backfilled row: billing_state = 'TRIALING'.
        jdbc.update("UPDATE tenant_subscriptions SET billing_state = 'TRIALING' WHERE id = ?", sub);

        // The BillingStateService javadoc claims "TRIALING → CURRENT when
        // trial_ends_at elapses; handled elsewhere". Prove the claim has no
        // owner: the dunning scheduler (the only scheduled subscription-state
        // driver on the predecessor) leaves billing_state = 'TRIALING'.
        transactions.executeWithoutResult(status -> billing.runDunningCycleOnce());
        assertThat(subscriptionField(sub, "billing_state"))
                .as("the stale comment's TRIALING→CURRENT claim has no implementation — "
                        + "'handled elsewhere' is nowhere")
                .isEqualTo("TRIALING");

        // The canonical lifecycle table remains the authority for the status
        // dimension: EXPIRE from TRIAL/TRIALING is the legal trial-expiry
        // representation.
        assertThat(SubscriptionLifecycle.isLegal("EXPIRE", "TRIAL")).isTrue();
        assertThat(SubscriptionLifecycle.isLegal("EXPIRE", "TRIALING")).isTrue();

        // The runtime driver resolves the status contract (MODEL_A) while the
        // billing dimension stays untouched (TRIAL_EXPIRY_BILLING_EFFECT =
        // NONE — BillingStateService remains the sole billing_state writer).
        runTrialExpiryCycle();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(sub, "billing_state"))
                .as("trial expiration must not write billing_state")
                .isEqualTo("TRIALING");
    }

    // ---------------------------------------------------------------
    // GUARD-01 — the canonical manual EXPIRE command already works
    // (contract guard: passes on pristine AND post-fix)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GUARD-01: canonical manual EXPIRE works on the pristine predecessor — the gap is the missing runtime driver, not the command")
    void guard01_canonicalManualExpireWorks() {
        UUID sub = createDueTrial(tenant, planA);
        SubscriptionCommandService canonical =
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        transactions.executeWithoutResult(status ->
                canonical.execute(sub, "EXPIRE", "r0c8 guard manual expire", null, null));

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.eq("subscription"),
                Mockito.eq(sub.toString()), Mockito.eq("r0c8 guard manual expire"),
                Mockito.eq("TRIALING"), Mockito.eq("EXPIRED"));
        assertThat(publishedEvents.stream()
                .skip(eventsBefore)
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionCancelledEvent)
                .count()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // Reflective driver discovery — the RED mechanism
    // ---------------------------------------------------------------

    /**
     * Locates and constructs the runtime trial-expiration driver. On the
     * pristine R0C-7 predecessor the class does not exist, and every RED
     * test fails here with the gap itself — "no runtime driver" — instead of
     * a compile error, so the battery runs unmodified on both sides.
     */
    private Object newDriver() {
        Class<?> clazz;
        try {
            clazz = Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(
                    "R0C-8 RED: no runtime trial-expiration driver exists on this code base ("
                            + DRIVER_CLASS + " not found) — an expired trial remains TRIALING "
                            + "indefinitely: no scheduler applies trial expiry", e);
        }
        try {
            Constructor<?> ctor = clazz.getConstructor(
                    JdbcTemplate.class, SubscriptionCommandService.class,
                    TransactionTemplate.class, java.time.Clock.class);
            return ctor.newInstance(jdbc,
                    new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                    transactions, java.time.Clock.systemUTC());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "R0C-8 RED: driver signature mismatch — expected constructor "
                            + "(JdbcTemplate, SubscriptionCommandService, TransactionTemplate, Clock): "
                            + e.getMessage(), e);
        }
    }

    private void runTrialExpiryCycle() {
        Object driver = newDriver();
        try {
            Method cycle = driver.getClass().getMethod("runTrialExpiryCycleOnce");
            cycle.invoke(driver);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "R0C-8 RED: driver must expose the testable entry point "
                            + "runTrialExpiryCycleOnce(): " + e.getMessage(), e);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("R0C-8 RED: driver cycle invocation failed: "
                    + e.getCause(), e);
        }
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

    /**
     * Creates a real trial subscription through the production create path
     * (status TRIALING, trial_ends_at = now + 14 days, no invoice) and then
     * backdates trial_ends_at to simulate the passage of time — the exact
     * production state the runtime driver must pick up.
     */
    private UUID createDueTrial(UUID tenantId, UUID planId) {
        UUID sub = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, 14), null).id());
        jdbc.update(
                "UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), sub);
        return sub;
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

    private String ledgerFromStatus(UUID subscriptionId, String command) {
        return jdbc.queryForObject(
                "SELECT from_status FROM subscription_commands WHERE subscription_id = ? AND command = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                String.class, subscriptionId, command);
    }

    private String ledgerToStatus(UUID subscriptionId, String command) {
        return jdbc.queryForObject(
                "SELECT to_status FROM subscription_commands WHERE subscription_id = ? AND command = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                String.class, subscriptionId, command);
    }
}
