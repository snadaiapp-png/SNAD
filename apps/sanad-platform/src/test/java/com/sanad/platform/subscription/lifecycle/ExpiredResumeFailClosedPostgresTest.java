package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * R0C-10 Task G — resume(EXPIRED) FALSE-SUCCESS FIX (P1, PostgreSQL Direct).
 *
 * <p>The R0C-9 forensic battery (PG-03b at 13c144e2) proved the defect: the
 * legacy resume endpoint's guard only special-cases CANCELLED, so
 * {@code resume(EXPIRED)} fell into the non-CANCELLED branch — the status
 * stayed EXPIRED but a misleading {@code SUBSCRIPTION.RESUMED} change event,
 * a {@code SUBSCRIPTION.RESUME} platform audit row and an entitlement
 * recalculation were emitted anyway (false success).</p>
 *
 * <p>R0C-10 required behavior (frozen contract §8):</p>
 * <ul>
 *   <li>QG-01 — resume(EXPIRED) FAIL_CLOSED: 409 CONFLICT, status mutation 0,
 *       RESUMED change event 0, misleading audit 0, entitlement
 *       recalculation 0, billing side effect 0, provisioning side effect 0.</li>
 *   <li>QG-02 — resume(TERMINATED) FAIL_CLOSED (terminal contract).</li>
 *   <li>QG-03 — resume(CANCELLED) with NO effective successor keeps the
 *       R0C-7 legacy revival (regression guard).</li>
 * </ul>
 */
class ExpiredResumeFailClosedPostgresTest {

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private SaasAdministrationService legacy;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;

    private UUID tenant;
    private UUID planA;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExpiredResumeFailClosedPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ExpiredResumeFailClosedPostgresTest.");
        isolatedUrl = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        MigrationTestSchemaSupport.ensureDatabase(
                isolatedUrl,
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @AfterAll
    static void releaseUrl() {
        isolatedUrl = null;
    }

    @BeforeEach
    void migrateAndSeed() {
        String url = MigrationTestSchemaSupport.getIsolatedJdbcUrl(isolatedUrl);
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));

        audit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        seedTenant(tenant);
        seedPlan(planA, "R0C10-G");
    }

    // ---------------------------------------------------------------
    // QG-01 — resume(EXPIRED) fail closed, zero side effects
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QG-01: resume(EXPIRED) -> 409 CONFLICT, zero status mutation, zero events/audit/recalc")
    void qg01_resumeExpiredFailsClosed() {
        UUID sub = expiredSubscription();
        publishedEvents.clear();
        Mockito.clearInvocations(audit);
        long eventsBefore = changeEventCount(sub);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                legacy.resumeSubscription(sub, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        // STATUS_MUTATION=0
        assertThat(field(sub, "status")).isEqualTo("EXPIRED");
        // RESUMED_CHANGE_EVENT=0 (no SUBSCRIPTION.RESUMED change event, and no
        // change-event of any kind beyond the seeded creation record)
        assertThat(changeEventCount(sub)).isEqualTo(eventsBefore);
        assertThat(resumedChangeEventCount(sub)).isZero();
        // MISLEADING_AUDIT=0 — the platform audit writer was never invoked
        verifyNoInteractions(audit);
        // ENTITLEMENT_RECALCULATION=0 — no entitlement event of any kind
        assertThat(publishedEvents).isEmpty();
        // BILLING_SIDE_EFFECT=0 — no invoice, no billing_state change
        Long invoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, sub);
        assertThat(invoices).isZero();
        assertThat(field(sub, "billing_state")).isEqualTo("CURRENT");
        // PROVISIONING_SIDE_EFFECT=0 — no provisioning job for the subscription
        Long jobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM provisioning_jobs WHERE subscription_id = ?", Long.class, sub);
        assertThat(jobs).isZero();
        // No lifecycle RESUME ledger row exists either.
        assertThat(ledgerCount(sub, "RESUME")).isZero();
    }

    // ---------------------------------------------------------------
    // QG-02 — resume(TERMINATED) fail closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QG-02: resume(TERMINATED) -> 409 CONFLICT with zero side effects")
    void qg02_resumeTerminatedFailsClosed() {
        UUID sub = terminatedSubscription();
        publishedEvents.clear();
        Mockito.clearInvocations(audit);
        long eventsBefore = changeEventCount(sub);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                legacy.resumeSubscription(sub, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(field(sub, "status")).isEqualTo("TERMINATED");
        assertThat(changeEventCount(sub)).isEqualTo(eventsBefore);
        assertThat(resumedChangeEventCount(sub)).isZero();
        verifyNoInteractions(audit);
        assertThat(publishedEvents).isEmpty();
        assertThat(ledgerCount(sub, "RESUME")).isZero();
    }

    // ---------------------------------------------------------------
    // QG-03 — CANCELLED revival (no effective successor) preserved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QG-03: resume(CANCELLED) without effective successor keeps the R0C-7 revival contract")
    void qg03_cancelledResumePreserved() {
        UUID sub = cancelledSubscription();

        transactions.executeWithoutResult(status -> legacy.resumeSubscription(sub, null));

        assertThat(field(sub, "status")).isEqualTo("ACTIVE");
        // The legacy contract owns its event/audit/entitlement publishing.
        assertThat(publishedEvents.stream()
                .anyMatch(e -> e instanceof SubscriptionEntitlementListener.SubscriptionResumedEvent))
                .isTrue();
        long resumedEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                        + "AND action = 'SUBSCRIPTION.RESUMED'", Long.class, sub);
        assertThat(resumedEvents).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private UUID expiredSubscription() {
        UUID sub = createTrial(14);
        // Backdate the trial end (R0C-9 createDueTrial convention) so the
        // R0C-8 runtime driver actually expires it.
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), sub);
        driver().runTrialExpiryCycleOnce();
        assertThat(field(sub, "status")).isEqualTo("EXPIRED");
        return sub;
    }

    private UUID terminatedSubscription() {
        UUID sub = createTrial(0);
        transactions.executeWithoutResult(status ->
                commands().execute(sub, "TERMINATE", "R0C-10 G seed"));
        return sub;
    }

    private UUID cancelledSubscription() {
        UUID sub = createTrial(0);
        transactions.executeWithoutResult(status ->
                commands().execute(sub, "CANCEL", "R0C-10 G seed"));
        return sub;
    }

    /** Canonical public command authority (real production path). */
    private SubscriptionCommandService commands() {
        return new SubscriptionCommandService(jdbc, audit, publishedEvents::add);
    }

    private TrialExpirationService driver() {
        return new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                new TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                new DriverManagerDataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(isolatedUrl),
                                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "")))),
                Clock.systemUTC());
    }

    private UUID createTrial(int trialDays) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, trialDays), null).id());
    }

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
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 1, 'ACTIVE', NOW(), 'SAR', 30000, 300000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                UUID.randomUUID(), id);
    }

    private Object field(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long changeEventCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ?",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private long resumedChangeEventCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                        + "AND action = 'SUBSCRIPTION.RESUMED'", Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private long ledgerCount(UUID subscriptionId, String command) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = ?",
                Long.class, subscriptionId, command);
        return count == null ? 0L : count;
    }
}
