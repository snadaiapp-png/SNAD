package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CancelSubscriptionRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.provisioning.ProvisioningJobRunner;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-8 — Trial Expiration Runtime Driver (GREEN PostgreSQL matrix).
 *
 * <p>Proves, on PostgreSQL Direct, that {@link TrialExpirationService} closes
 * the lifecycle runtime gap proven by the RED battery: due trials
 * ({@code status IN (TRIAL, TRIALING)} + {@code trial_ends_at <=}
 * execution time) are expired exactly once through the canonical
 * {@code SubscriptionCommandService.execute} authority — ledger row,
 * {@code SUBSCRIPTION_EXPIRE} audit entry and
 * {@code SubscriptionCancelledEvent} each exactly once — while every other
 * status is untouched, no invoice/credit/proration moves, billing_state is
 * never written, the multi-plan composition and the seat mirror are
 * unchanged, operators who win a race are never resurrected, and the R0C-7
 * single-writer contracts (provisioning, billing) plus the R0C-2R
 * pricing-country authority stay regression-free.</p>
 */
class TrialExpirationRuntimePostgresTest {

    private JdbcTemplate jdbc;
    private SaasAdministrationService legacy;
    private BillingStateService billing;
    private ProvisioningJobRunner provisioning;
    private SubscriptionChangeService changeService;
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
                    "TrialExpirationRuntimePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping TrialExpirationRuntimePostgresTest.");
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
        provisioning = new ProvisioningJobRunner(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add));
        changeService = new SubscriptionChangeService(
                jdbc, new SubscriptionItemRepository(jdbc),
                new PriceResolver(new PriceRepository(jdbc)));

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        versionA = UUID.randomUUID();
        seedTenant(tenant, "SA");
        seedPlan(planA, "R0C8-A");
        seedPlanVersion(versionA, planA, 1, "ACTIVE", 30000L);
    }

    /** Driver under test, wall clock. */
    private TrialExpirationService driver() {
        return new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                transactions, Clock.systemUTC());
    }

    /** Driver under test, fixed clock (time-authority tests). */
    private TrialExpirationService driverAt(Instant fixedExecutionTime) {
        return new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                transactions, Clock.fixed(fixedExecutionTime, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------
    // PG-01 — future trial untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-01: future trial (trial_ends_at ahead) is untouched")
    void pg01_futureTrialUntouched() {
        UUID sub = createTrial(tenant, planA, 14); // trial_ends_at = now + 14d
        Timestamp endsAtBefore = timestampField(sub, "trial_ends_at");
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.dueSeen()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("TRIALING");
        assertThat(timestampField(sub, "trial_ends_at")).isEqualTo(endsAtBefore);
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
        Mockito.verifyNoInteractions(audit);
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore);
    }

    // ---------------------------------------------------------------
    // PG-02 — due TRIALING handled according to the resolved contract
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-02: due TRIALING → EXPIRED via canonical EXPIRE (real create path; cancelled_at NULL; periods/trial end preserved)")
    void pg02_dueTrialingHandledPerResolvedContract() {
        UUID sub = createDueTrial(tenant, planA);
        Timestamp endsAt = timestampField(sub, "trial_ends_at");
        Timestamp periodStart = timestampField(sub, "current_period_start");
        Timestamp periodEnd = timestampField(sub, "current_period_end");
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.dueSeen()).isEqualTo(1);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        // EXPIRE is not CANCEL: cancelled_at stays NULL (metadata contract).
        assertThat(subscriptionField(sub, "cancelled_at")).isNull();
        // The trial end is a historical fact — preserved, not recomputed.
        assertThat(timestampField(sub, "trial_ends_at")).isEqualTo(endsAt);
        assertThat(timestampField(sub, "current_period_start")).isEqualTo(periodStart);
        assertThat(timestampField(sub, "current_period_end")).isEqualTo(periodEnd);
        assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
        assertThat(ledgerFromStatus(sub, "EXPIRE")).isEqualTo("TRIALING");
        assertThat(ledgerToStatus(sub, "EXPIRE")).isEqualTo("EXPIRED");
        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.eq("subscription"),
                Mockito.eq(sub.toString()), Mockito.contains("Trial expired"),
                Mockito.eq("TRIALING"), Mockito.eq("EXPIRED"));
        assertThat(cancellationEventsSince(eventsBefore)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-03 — due TRIAL (legacy status value) handled
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-03: due TRIAL (legacy value) → EXPIRED via canonical EXPIRE")
    void pg03_dueTrialLegacyStatusHandled() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "TRIAL", "CURRENT");
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));
        Mockito.reset(audit);

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
        assertThat(ledgerFromStatus(sub, "EXPIRE")).isEqualTo("TRIAL");
        assertThat(ledgerToStatus(sub, "EXPIRE")).isEqualTo("EXPIRED");
    }

    // ---------------------------------------------------------------
    // PG-04 — null trial_ends_at untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-04: TRIALING with null trial_ends_at is untouched (no due date — never selected)")
    void pg04_nullTrialEndsAtUntouched() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "TRIALING", "CURRENT");
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = NULL WHERE id = ?", sub);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.dueSeen()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("TRIALING");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
        Mockito.verifyNoInteractions(audit);
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore);
    }

    // ---------------------------------------------------------------
    // PG-05 — ACTIVE untouched (even with stale past trial_ends_at)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-05: ACTIVE with stale past trial_ends_at is untouched (EXPIRE from ACTIVE is illegal)")
    void pg05_activeUntouched() {
        UUID sub = createSubscription(tenant, planA); // ACTIVE, no trial
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS)); // stale metadata
        Mockito.reset(audit);

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.dueSeen()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
        Mockito.verifyNoInteractions(audit);
    }

    // ---------------------------------------------------------------
    // PG-06 — PAST_DUE untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-06: PAST_DUE (past trial_ends_at) is untouched")
    void pg06_pastDueUntouched() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "PAST_DUE", "PAST_DUE");
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("PAST_DUE");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
    }

    // ---------------------------------------------------------------
    // PG-07 — SUSPENDED untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-07: SUSPENDED (past trial_ends_at) is untouched")
    void pg07_suspendedUntouched() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "SUSPENDED", "SUSPENDED");
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("SUSPENDED");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
    }

    // ---------------------------------------------------------------
    // PG-08 — CANCELLED untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-08: operator CANCEL before the cycle — CANCELLED stays CANCELLED, no EXPIRE attempted")
    void pg08_cancelledUntouched() {
        UUID sub = createDueTrial(tenant, planA);
        transactions.executeWithoutResult(status ->
                legacy.cancelSubscription(sub, new CancelSubscriptionRequest(true, "r0c8 pg08"), null));
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("CANCELLED");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
        assertThat(ledgerCount(sub, "CANCEL")).isEqualTo(1L);
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore);
        Mockito.verify(audit, Mockito.never()).success(Mockito.any(), Mockito.any(),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());
    }

    // ---------------------------------------------------------------
    // PG-09 — EXPIRED idempotent
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-09: already-EXPIRED trial is a no-op — not selected, no ledger/audit/event added")
    void pg09_expiredIdempotent() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "EXPIRED", "CURRENT");
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.dueSeen()).isZero();
        assertThat(result.expired()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(totalLedgerCount(sub)).isZero();
        Mockito.verifyNoInteractions(audit);
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore);
    }

    // ---------------------------------------------------------------
    // PG-10 — TERMINATED untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-10: TERMINATED (past trial_ends_at) is untouched")
    void pg10_terminatedUntouched() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "TERMINATED", "CURRENT");
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("TERMINATED");
        assertThat(ledgerCount(sub, "EXPIRE")).isZero();
    }

    // ---------------------------------------------------------------
    // PG-11 — canonical ledger exactly once
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-11: exactly one subscription_commands row — command EXPIRE, correct from/to, system actor")
    void pg11_canonicalLedgerExactlyOnce() {
        UUID sub = createDueTrial(tenant, planA);

        driver().runTrialExpiryCycleOnce();

        assertThat(totalLedgerCount(sub)).isEqualTo(1L);
        Map<String, Object> ledgerRow = jdbc.queryForMap(
                "SELECT command, from_status, to_status, reason, actor_tenant_id, actor_user_id, tenant_id "
                        + "FROM subscription_commands WHERE subscription_id = ?", sub);
        assertThat(ledgerRow.get("command")).isEqualTo("EXPIRE");
        assertThat(ledgerRow.get("from_status")).isEqualTo("TRIALING");
        assertThat(ledgerRow.get("to_status")).isEqualTo("EXPIRED");
        assertThat((String) ledgerRow.get("reason")).contains("Trial expired at ");
        // Automated system actor — no operator impersonation.
        assertThat(ledgerRow.get("actor_tenant_id")).isNull();
        assertThat(ledgerRow.get("actor_user_id")).isNull();
        assertThat(ledgerRow.get("tenant_id")).isEqualTo(tenant);
    }

    // ---------------------------------------------------------------
    // PG-12 — audit exactly once
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-12: exactly one SUBSCRIPTION_EXPIRE platform audit entry — never zero, never two")
    void pg12_auditExactlyOnce() {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);

        driver().runTrialExpiryCycleOnce();

        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.eq("subscription"),
                Mockito.eq(sub.toString()), Mockito.contains("Trial expired"),
                Mockito.eq("TRIALING"), Mockito.eq("EXPIRED"));
        Mockito.verify(audit, Mockito.never()).success(Mockito.any(), Mockito.any(),
                Mockito.eq("SUBSCRIPTION_CANCEL"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());
    }

    // ---------------------------------------------------------------
    // PG-13 — entitlement event exactly once
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-13: exactly one SubscriptionCancelledEvent — the existing canonical expiration event, no second type")
    void pg13_entitlementEventExactlyOnce() {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        driver().runTrialExpiryCycleOnce();

        assertThat(cancellationEventsSince(eventsBefore)).isEqualTo(1L);
        // No other entitlement event type fired for the expiration.
        assertThat(publishedEvents.size()).isEqualTo(eventsBefore + 1);
    }

    // ---------------------------------------------------------------
    // PG-14 — second scheduler run: no duplicate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-14: running the cycle twice is idempotent — no second ledger/audit/event")
    void pg14_secondRunNoDuplicate() {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);

        driver().runTrialExpiryCycleOnce();
        Mockito.reset(audit);
        int eventsAfterFirst = publishedEvents.size();
        TrialExpirationService.TrialExpiryResult second = driver().runTrialExpiryCycleOnce();

        assertThat(second.dueSeen()).isZero();
        assertThat(second.expired()).isZero();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(totalLedgerCount(sub)).isEqualTo(1L);
        Mockito.verifyNoInteractions(audit);
        assertThat(publishedEvents.size()).isEqualTo(eventsAfterFirst);
    }

    // ---------------------------------------------------------------
    // PG-15 — concurrent workers: no duplicate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-15: two workers racing the same due trial — exactly one effective transition (FOR UPDATE re-check)")
    void pg15_concurrentWorkersNoDuplicate() throws Exception {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService worker1 = driver();
        TrialExpirationService worker2 = driver();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TrialExpirationService.TrialExpiryResult> r1 = pool.submit(
                    () -> barrier(barrier).runTrialExpiryCycleOnce());
            Future<TrialExpirationService.TrialExpiryResult> r2 = pool.submit(
                    () -> barrier(barrier).runTrialExpiryCycleOnce());
            TrialExpirationService.TrialExpiryResult first = r1.get(60, TimeUnit.SECONDS);
            TrialExpirationService.TrialExpiryResult second = r2.get(60, TimeUnit.SECONDS);

            assertThat(first.expired() + second.expired()).isEqualTo(1);
            assertThat(first.failed() + second.failed()).isZero();
        } finally {
            pool.shutdownNow();
        }

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
        Mockito.verify(audit, Mockito.times(1)).success(Mockito.any(), Mockito.eq(tenant),
                Mockito.eq("SUBSCRIPTION_EXPIRE"), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());
        assertThat(cancellationEventsSince(eventsBefore)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-16 — operator cancel race safe
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-16: cancel-vs-expiry race — final state is terminal (CANCELLED or EXPIRED), never revived, EXPIRE ≤ 1")
    void pg16_operatorCancelRaceSafe() throws Exception {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        TrialExpirationService worker = driver();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> expiry = pool.submit(() -> barrier(barrier).runTrialExpiryCycleOnce());
            Future<Boolean> cancel = pool.submit(() -> {
                barrier(barrier);
                try {
                    transactions.executeWithoutResult(status ->
                            legacy.cancelSubscription(sub, new CancelSubscriptionRequest(true, "r0c8 race"), null));
                    return true; // cancel won or interleaved as last writer
                } catch (org.springframework.web.server.ResponseStatusException e) {
                    return false; // canonical rejection: EXPIRE already committed (409)
                }
            });
            expiry.get(60, TimeUnit.SECONDS);
            boolean cancelCommitted = cancel.get(60, TimeUnit.SECONDS);

            String status = (String) subscriptionField(sub, "status");
            // Either operator won the race or the driver did — the state is
            // ALWAYS terminal, never TRIALING/ACTIVE (no resurrection), and
            // exactly one command family ledgers (the guarded canonical
            // write rejects the loser — no stale from_status rows).
            assertThat(status).isIn("CANCELLED", "EXPIRED");
            if ("EXPIRED".equals(status)) {
                // The cancel lost: the guarded canonical write rejected the
                // overwrite of the committed EXPIRED state.
                assertThat(cancelCommitted).isFalse();
                assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
                assertThat(ledgerCount(sub, "CANCEL")).isZero();
            } else {
                // CANCELLED_AFTER_RACE = CANCELLED: the driver's locked
                // re-check saw CANCELLED and skipped — no EXPIRE attempt.
                assertThat(cancelCommitted).isTrue();
                assertThat(ledgerCount(sub, "CANCEL")).isEqualTo(1L);
                assertThat(ledgerCount(sub, "EXPIRE")).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
        // No non-terminal resurrection and no runaway event duplication.
        assertThat(cancellationEventsSince(eventsBefore)).isLessThanOrEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-17 — operator terminate race safe
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-17: terminate-vs-expiry race — final state is terminal, TERMINATED/EXPIRED, never revived")
    void pg17_operatorTerminateRaceSafe() throws Exception {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        SubscriptionCommandService canonical =
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add);

        TrialExpirationService worker = driver();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> expiry = pool.submit(() -> barrier(barrier).runTrialExpiryCycleOnce());
            Future<Boolean> terminate = pool.submit(() -> {
                barrier(barrier);
                try {
                    transactions.executeWithoutResult(status ->
                            canonical.execute(sub, "TERMINATE", "r0c8 terminate race", null, null));
                    return true;
                } catch (IllegalStateException e) {
                    return false; // TERMINATE from EXPIRED is canonically illegal
                }
            });
            expiry.get(60, TimeUnit.SECONDS);
            boolean terminateCommitted = terminate.get(60, TimeUnit.SECONDS);

            String status = (String) subscriptionField(sub, "status");
            assertThat(status).isIn("TERMINATED", "EXPIRED");
            if ("EXPIRED".equals(status)) {
                assertThat(terminateCommitted).isFalse();
                assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
                assertThat(ledgerCount(sub, "TERMINATE")).isZero();
            } else {
                assertThat(terminateCommitted).isTrue();
                assertThat(ledgerCount(sub, "TERMINATE")).isEqualTo(1L);
                assertThat(ledgerCount(sub, "EXPIRE")).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------
    // PG-18 — manual activate race respects canonical legality
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-18: activate-vs-expiry race — one legal winner; the loser is canonically rejected or skipped")
    void pg18_manualActivateRaceRespectsCanonicalLegality() throws Exception {
        UUID sub = createDueTrial(tenant, planA);
        Mockito.reset(audit);
        SubscriptionCommandService canonical =
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add);

        TrialExpirationService worker = driver();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> expiry = pool.submit(() -> barrier(barrier).runTrialExpiryCycleOnce());
            Future<Boolean> activate = pool.submit(() -> {
                barrier(barrier);
                try {
                    transactions.executeWithoutResult(status ->
                            canonical.execute(sub, "ACTIVATE", "r0c8 activate race", null, null));
                    return true; // ACTIVATE from TRIALING is legal — operator won
                } catch (IllegalStateException e) {
                    return false; // ACTIVATE from EXPIRED is canonically illegal
                }
            });
            expiry.get(60, TimeUnit.SECONDS);
            boolean activateCommitted = activate.get(60, TimeUnit.SECONDS);

            String status = (String) subscriptionField(sub, "status");
            assertThat(status).isIn("ACTIVE", "EXPIRED");
            if ("ACTIVE".equals(status)) {
                // The operator activated first; the driver's locked re-check
                // saw ACTIVE and skipped — no blind expiration of an ACTIVE
                // subscription, even with an elapsed trial_ends_at.
                assertThat(activateCommitted).isTrue();
                assertThat(ledgerCount(sub, "EXPIRE")).isZero();
                assertThat(ledgerCount(sub, "ACTIVATE")).isEqualTo(1L);
            } else {
                // The driver expired first; the manual activation was
                // rejected by the canonical table (ACTIVATE-from-EXPIRED is
                // illegal, or the guarded write refused the overwrite) —
                // never a resurrection, never a stale ACTIVATE ledger row.
                assertThat(activateCommitted).isFalse();
                assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
                assertThat(ledgerCount(sub, "ACTIVATE")).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------
    // PG-19 — billing_state behavior correct
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-19: trial expiration never writes billing_state — both backfilled TRIALING and default CURRENT are preserved")
    void pg19_billingStateBehaviorCorrect() {
        // tenant_subscriptions is UNIQUE(tenant_id): one subscription per tenant.
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantB, "SA");
        // Migration-backfilled row shape: billing_state = 'TRIALING'.
        UUID backfilled = createDueTrial(tenant, planA);
        jdbc.update("UPDATE tenant_subscriptions SET billing_state = 'TRIALING' WHERE id = ?", backfilled);
        // Post-V20260815_20 creation shape: billing_state = 'CURRENT' (column default).
        UUID modern = createDueTrial(tenantB, planA);
        jdbc.update("UPDATE tenant_subscriptions SET billing_state = 'CURRENT' WHERE id = ?", modern);

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(backfilled, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(backfilled, "billing_state")).isEqualTo("TRIALING");
        assertThat(subscriptionField(modern, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(modern, "billing_state")).isEqualTo("CURRENT");
        // BillingStateService remains the sole billing_state writer: no
        // billing audit was produced by the trial driver.
        Mockito.verifyNoInteractions(billingAudit);
    }

    // ---------------------------------------------------------------
    // PG-20 — invoice delta correct
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-20: expiration issues no invoice/credit/refund/proration — invoice delta 0, credit balance unchanged")
    void pg20_invoiceDeltaCorrect() {
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantB, "SA");
        UUID dueTrial = createDueTrial(tenant, planA);
        // A paid ACTIVE subscription with a real invoice must be untouched.
        UUID paid = createSubscription(tenantB, planA); // non-trial → initial invoice issued
        long invoicesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices", Long.class);
        Map<String, Object> paidRow = jdbc.queryForMap(
                "SELECT credit_balance_minor, seat_quantity FROM tenant_subscriptions WHERE id = ?", paid);

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(dueTrial, "status")).isEqualTo("EXPIRED");
        long invoicesAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices", Long.class);
        assertThat(invoicesAfter).as("INVOICE_DELTA must be 0").isEqualTo(invoicesBefore);
        Map<String, Object> paidRowAfter = jdbc.queryForMap(
                "SELECT credit_balance_minor, seat_quantity FROM tenant_subscriptions WHERE id = ?", paid);
        assertThat(paidRowAfter.get("credit_balance_minor"))
                .as("CREDIT_DELTA must be 0").isEqualTo(paidRow.get("credit_balance_minor"));
        assertThat(paidRowAfter.get("seat_quantity")).isEqualTo(paidRow.get("seat_quantity"));
        // The trial itself was never invoiced and stays that way.
        Long trialInvoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, dueTrial);
        assertThat(trialInvoices).isZero();
    }

    // ---------------------------------------------------------------
    // PG-21 — multi-plan composition unchanged
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-21: anchored PLAN item + secondary ADD_ON item are untouched by expiration")
    void pg21_multiPlanCompositionUnchanged() {
        UUID sub = createDueTrial(tenant, planA); // births the anchored PLAN item (qty 2)
        seedAddOnItem(sub, tenant, planA, versionA);
        List<Map<String, Object>> itemsBefore = jdbc.queryForList(
                "SELECT item_type, plan_id, quantity, unit_amount_minor, status FROM subscription_items "
                        + "WHERE subscription_id = ? ORDER BY item_type", sub);

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        List<Map<String, Object>> itemsAfter = jdbc.queryForList(
                "SELECT item_type, plan_id, quantity, unit_amount_minor, status FROM subscription_items "
                        + "WHERE subscription_id = ? ORDER BY item_type", sub);
        assertThat(itemsAfter).isEqualTo(itemsBefore);
        assertThat(itemsAfter).hasSize(2);
        assertThat(itemsAfter.stream()
                .filter(i -> "PLAN".equals(i.get("item_type")))
                .findFirst().orElseThrow().get("quantity")).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // PG-22 — seat mirror unchanged
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-22: seat_quantity and the anchored PLAN item quantity mirror are unchanged by expiration")
    void pg22_seatMirrorUnchanged() {
        UUID sub = createDueTrial(tenant, planA); // seats 2 → anchored item quantity 2 (R0C-6 invariant)
        Map<String, Object> mirrorBefore = jdbc.queryForMap(
                "SELECT s.seat_quantity, i.quantity AS item_quantity, i.unit_amount_minor "
                        + "FROM tenant_subscriptions s JOIN subscription_items i "
                        + "ON i.subscription_id = s.id AND i.item_type = 'PLAN' AND i.status = 'ACTIVE' "
                        + "WHERE s.id = ?", sub);
        assertThat(mirrorBefore.get("item_quantity")).isEqualTo(mirrorBefore.get("seat_quantity"));

        driver().runTrialExpiryCycleOnce();

        Map<String, Object> mirrorAfter = jdbc.queryForMap(
                "SELECT s.seat_quantity, i.quantity AS item_quantity, i.unit_amount_minor "
                        + "FROM tenant_subscriptions s JOIN subscription_items i "
                        + "ON i.subscription_id = s.id AND i.item_type = 'PLAN' AND i.status = 'ACTIVE' "
                        + "WHERE s.id = ?", sub);
        assertThat(mirrorAfter).isEqualTo(mirrorBefore);
    }

    // ---------------------------------------------------------------
    // PG-23 — provisioning regression (R0C-7)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-23: R0C-7 provisioning convergence regression — ACTIVATE via canonical, retry idempotent")
    void pg23_provisioningRegression() {
        UUID sub = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "PENDING_ACTIVATION", "CURRENT");
        seedPlanItem(sub, tenant, planA, versionA);
        UUID job = enqueueProvisioningJobFor(sub, tenant);

        ProvisioningJobRunner.JobOutcome first = provisioning.run(job);
        assertThat(first.status()).isEqualTo("SUCCEEDED");
        assertThat(subscriptionField(sub, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sub, "ACTIVATE")).isEqualTo(1L);

        ProvisioningJobRunner.JobOutcome retry = provisioning.run(job);
        assertThat(retry.status()).isEqualTo("SUCCEEDED");
        assertThat(ledgerCount(sub, "ACTIVATE")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-24 — billing lifecycle regression (R0C-7)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-24: R0C-7 billing convergence regression — dunning transitions stay canonical after the driver exists")
    void pg24_billingLifecycleRegression() {
        UUID sub = createSubscription(tenant, planA);
        insertOverdueInvoice(sub, Instant.now().minus(5, ChronoUnit.DAYS)); // > 3d grace, < 7d

        transactions.execute(status -> billing.evaluateAndTransition(tenant));

        assertThat(subscriptionField(sub, "status")).isEqualTo("PAST_DUE");
        assertThat(subscriptionField(sub, "billing_state")).isEqualTo("PAST_DUE");
        assertThat(ledgerCount(sub, "MARK_PAST_DUE")).isEqualTo(1L);

        insertOverdueInvoice(sub, Instant.now().minus(12, ChronoUnit.DAYS)); // > 7d suspend grace
        transactions.execute(status -> billing.evaluateAndTransition(tenant));

        assertThat(subscriptionField(sub, "status")).isEqualTo("SUSPENDED");
        assertThat(subscriptionField(sub, "billing_state")).isEqualTo("SUSPENDED");
        assertThat(ledgerCount(sub, "SUSPEND")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-25 — tenant isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-25: expiring tenant A's trial never mutates tenant B/C rows — zero cross-tenant ledger/events")
    void pg25_tenantIsolation() {
        UUID tenantB = UUID.randomUUID();
        UUID tenantC = UUID.randomUUID();
        seedTenant(tenantB, "SA");
        seedTenant(tenantC, "AE");
        UUID subA = createDueTrial(tenant, planA);
        UUID subB = createTrial(tenantB, planA, 14); // future trial
        UUID subC = createSubscription(tenantC, planA); // ACTIVE paid
        Mockito.reset(audit);
        int eventsBefore = publishedEvents.size();

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(subA, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(subB, "status")).isEqualTo("TRIALING");
        assertThat(timestampField(subB, "trial_ends_at")).isAfter(Instant.now());
        assertThat(subscriptionField(subC, "status")).isEqualTo("ACTIVE");
        assertThat(totalLedgerCount(subB)).isZero();
        assertThat(totalLedgerCount(subC)).isZero();
        // No subscription_commands row under another tenant's id.
        Long crossLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE tenant_id IN (?, ?) "
                        + "AND subscription_id <> ?", Long.class, tenantB, tenantC, subA);
        assertThat(crossLedger).isZero();
        // No entitlement event leaked to another tenant.
        long foreignEvents = publishedEvents.stream()
                .skip(eventsBefore)
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionCancelledEvent
                        && !((SubscriptionEntitlementListener.SubscriptionCancelledEvent) e).tenantId().equals(tenant))
                .count();
        assertThat(foreignEvents).isZero();
    }

    // ---------------------------------------------------------------
    // PG-26 — R0C-2R pricing-country regression
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-26: R0C-2R pricing-country regression — tenant country authority survives the driver; client country still ignored")
    void pg26_pricingCountryRegression() {
        UUID aeTenant = UUID.randomUUID();
        seedTenant(aeTenant, "AE");
        UUID planB = UUID.randomUUID();
        UUID versionB = UUID.randomUUID();
        seedPlan(planB, "R0C8-B");
        seedPlanVersion(versionB, planB, 2, "ACTIVE", 90000L);
        seedPrice(versionB, "SA", "SAR", 90000L);
        seedPrice(versionB, "AE", "AED", 50000L);
        seedPrice(versionB, "GLOBAL", "USD", 70000L);
        UUID aeSub = createSubscription(aeTenant, planA);
        UUID saDueTrial = createDueTrial(tenant, planA);

        // Client-supplied rogue country "SA" (SA price is 90000; the AE
        // tenant's authoritative country prices at 50000) — R0C-2R P0-A.
        SubscriptionChangeService.ChangePreview before =
                changeService.preview(aeSub, versionB, "SA", Instant.now());
        assertThat(before.targetMonthlyMinor()).isEqualTo(50000L);

        driver().runTrialExpiryCycleOnce();

        assertThat(subscriptionField(saDueTrial, "status")).isEqualTo("EXPIRED");
        SubscriptionChangeService.ChangePreview after =
                changeService.preview(aeSub, versionB, "SA", Instant.now());
        assertThat(after.targetMonthlyMinor()).isEqualTo(50000L);
    }

    // ---------------------------------------------------------------
    // DETERM-01 — time authority: single execution timestamp (§12)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DETERM-01: due-ness is decided by the injectable clock, not wall time — one timestamp per cycle, embedded in the ledger reason")
    void determ01_timeAuthoritySingleExecutionTimestamp() {
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantB, "SA");
        Instant fixedNow = Instant.now().minus(1, ChronoUnit.HOURS);
        // Truncate to microseconds: PostgreSQL TIMESTAMPTZ precision — the
        // round trip through the driver's ledger reason is then exact.
        Instant seededEnd = fixedNow.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        UUID pastForClock = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "TRIALING", "CURRENT");
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(seededEnd), pastForClock);
        // Wall-clock-past but clock-future: must NOT be expired under the
        // fixed clock (proves the clock — not wall time — is the authority).
        UUID wallPastClockFuture = seedTrialRow(UUID.randomUUID(), tenantB, planA, versionA, "TRIALING", "CURRENT");
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)), wallPastClockFuture);

        TrialExpirationService.TrialExpiryResult result =
                driverAt(fixedNow).runTrialExpiryCycleOnce();

        assertThat(result.executedAt()).isEqualTo(fixedNow);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(subscriptionField(pastForClock, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(wallPastClockFuture, "status"))
                .as("trial_ends_at after the cycle's execution timestamp must stay untouched — "
                        + "the injectable clock is the time authority")
                .isEqualTo("TRIALING");
        // The ledger reason embeds the row's own trial end — deterministic
        // evidence, recomputed from the row, never from wall time.
        String reason = jdbc.queryForObject(
                "SELECT reason FROM subscription_commands WHERE subscription_id = ? AND command = 'EXPIRE'",
                String.class, pastForClock);
        assertThat(reason).contains("Trial expired at " + seededEnd);
    }

    // ---------------------------------------------------------------
    // PG-27 — §28 reconciliation: OVERDUE_TRIAL_NOT_TRANSITIONED (REPORT_ONLY)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-27: reconciliation classifies OVERDUE_TRIAL_NOT_TRANSITIONED; the scan repairs nothing")
    void pg27_reconciliationClassifiesOverdueTrialNotTransitioned() {
        // Historical overdue trial (pre-R0C-8 production shape: trial elapsed
        // long ago, no driver existed).
        UUID historical = seedTrialRow(UUID.randomUUID(), tenant, planA, versionA, "TRIALING", "CURRENT");
        backdate(historical, Instant.now().minus(30, ChronoUnit.DAYS));
        UUID tenantB = UUID.randomUUID();
        seedTenant(tenantB, "SA");
        UUID future = createTrial(tenantB, planA, 14);
        UUID tenantC = UUID.randomUUID();
        seedTenant(tenantC, "SA");
        UUID active = createSubscription(tenantC, planA);
        Mockito.reset(audit); // the real create paths audit; the scan must not

        // OVERDUE_TRIAL_NOT_TRANSITIONED: a trial whose trial_ends_at has
        // elapsed while the status is still TRIAL/TRIALING — i.e. rows the
        // (disabled-by-default) runtime driver has not transitioned yet.
        Long overdue = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions
                        WHERE status IN ('TRIAL', 'TRIALING')
                          AND trial_ends_at IS NOT NULL
                          AND trial_ends_at <= NOW()
                        """, Long.class);
        assertThat(overdue).isEqualTo(1L); // the historical row only

        // REPORT_ONLY: the classification is a pure read — no repair, no
        // ledger, no audit, no status change. Historical correction stays an
        // operator-approved decision (enable the driver or repair later).
        assertThat(subscriptionField(historical, "status")).isEqualTo("TRIALING");
        assertThat(totalLedgerCount(historical)).isZero();
        Mockito.verifyNoInteractions(audit);

        // Non-eligible rows are never classified: future trial and the
        // ACTIVE subscription (even with stale trial metadata) are clean.
        assertThat(subscriptionField(future, "status")).isEqualTo("TRIALING");
        assertThat(subscriptionField(active, "status")).isEqualTo("ACTIVE");

        // The driver is the ONLY sanctioned transition path: enabling it
        // (runTrialExpiryCycleOnce) resolves exactly the classified rows.
        driver().runTrialExpiryCycleOnce();
        assertThat(subscriptionField(historical, "status")).isEqualTo("EXPIRED");
        assertThat(subscriptionField(future, "status")).isEqualTo("TRIALING");
        Long overdueAfter = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions
                        WHERE status IN ('TRIAL', 'TRIALING')
                          AND trial_ends_at IS NOT NULL
                          AND trial_ends_at <= NOW()
                        """, Long.class);
        assertThat(overdueAfter).isZero();
    }

    // ---------------------------------------------------------------
    // Seeding helpers
    // ---------------------------------------------------------------

    private void seedTenant(UUID id, String countryCode) {
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8), countryCode);
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

    private void seedPrice(UUID versionId, String country, String currency, long baseMinor) {
        jdbc.update("""
                        INSERT INTO prices (id, plan_version_id, price_model, country_code, currency_code,
                                            billing_interval, base_amount_minor, effective_from,
                                            created_at, updated_at)
                        VALUES (?, ?, 'FLAT', ?, ?, 'MONTHLY', ?, NOW() - INTERVAL '1 hour', NOW(), NOW())
                        """,
                UUID.randomUUID(), versionId, country, currency, baseMinor);
    }

    private UUID seedTrialRow(UUID id, UUID tenantId, UUID planId, UUID versionId,
                              String status, String billingState) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, trial_ends_at, current_period_start,
                                                          current_period_end, cancel_at_period_end,
                                                          billing_state, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'MONTHLY', 2, 0, NOW(), NOW() + INTERVAL '14 days',
                                NOW(), NOW() + INTERVAL '14 days', FALSE, ?, NOW(), NOW())
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

    private void seedAddOnItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID versionId) {
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        plan_id, plan_version_id, name_snapshot, quantity,
                                                        unit_amount_minor, currency_code, status,
                                                        created_at, updated_at)
                        VALUES (?, ?, ?, 'ADD_ON', ?, ?, ?, 1, 10000, 'SAR', 'ACTIVE', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, subscriptionId, planId, versionId, "ADD_ON " + planId);
    }

    private UUID enqueueProvisioningJobFor(UUID subscriptionId, UUID tenantId) {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO provisioning_jobs (id, tenant_id, subscription_id, action, status,
                                                       attempts, created_at, updated_at)
                        VALUES (?, ?, ?, 'PROVISION_SUBSCRIPTION', 'PENDING', 0, NOW(), NOW())
                        """,
                jobId, tenantId, subscriptionId);
        return jobId;
    }

    private void insertOverdueInvoice(UUID subscriptionId, Instant dueAt) {
        UUID invoiceId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO billing_invoices (id, tenant_id, subscription_id, invoice_number, status,
                                                      currency_code, subtotal_minor, credit_applied_minor,
                                                      tax_minor, total_minor, amount_paid_minor,
                                                      period_start, period_end, due_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 30000, 0, 4500, 34500, 0, NOW(),
                                NOW() + INTERVAL '30 days', ?, NOW(), NOW())
                        """,
                invoiceId, tenant, subscriptionId, "INV-R0C8-" + invoiceId.toString().substring(0, 8),
                Timestamp.from(dueAt));
    }

    /** Real production create path: trial subscription, TRIALING + trial_ends_at = now + trialDays. */
    private UUID createTrial(UUID tenantId, UUID planId, int trialDays) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, trialDays), null).id());
    }

    /** Real production create path: non-trial ACTIVE subscription (initial invoice issued). */
    private UUID createSubscription(UUID tenantId, UUID planId) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, 0), null).id());
    }

    /** Trial created through the real path, then backdated to simulate elapsed time. */
    private UUID createDueTrial(UUID tenantId, UUID planId) {
        UUID sub = createTrial(tenantId, planId, 14);
        backdate(sub, Instant.now().minus(2, ChronoUnit.HOURS));
        return sub;
    }

    private void backdate(UUID subscriptionId, Instant trialEndsAt) {
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(trialEndsAt), subscriptionId);
    }

    // ---------------------------------------------------------------
    // Concurrency + observation helpers
    // ---------------------------------------------------------------

    private TrialExpirationService barrier(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("race barrier broken", e);
        }
        return driver();
    }

    private Object subscriptionField(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private Timestamp timestampField(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                java.sql.Timestamp.class, subscriptionId);
    }

    private long ledgerCount(UUID subscriptionId, String command) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = ?",
                Long.class, subscriptionId, command);
        return count == null ? 0L : count;
    }

    private long totalLedgerCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                Long.class, subscriptionId);
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

    private long cancellationEventsSince(int eventsBefore) {
        return publishedEvents.stream()
                .skip(eventsBefore)
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionCancelledEvent)
                .count();
    }
}
