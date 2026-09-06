package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-10 Task J — ATOMIC / CONCURRENT SUCCESSOR CREATION (PostgreSQL Direct).
 *
 * <p>Starting state: a tenant whose only history is terminal EXPIRED, feature
 * gate ON, no effective row. Two (or more) simultaneous successor-creation
 * requests are issued through the REAL production path against REAL
 * PostgreSQL concurrency.</p>
 *
 * <ul>
 *   <li>QJ-01 — exactly ONE request wins; every loser receives the
 *       repository-standard deterministic 409 CONFLICT (no raw constraint
 *       details leak); effective row count stays 1; history stays intact
 *       (EXPIRED + winner); the winner went through the no-trial successor
 *       contract.</li>
 *   <li>QJ-02 — the same race on a first-time tenant (no history): exactly
 *       one effective row survives; losers get the deterministic CONFLICT.
 *       (The partial unique index is the final backstop even where the
 *       service guard sees an empty history.)</li>
 * </ul>
 */
class ConcurrentSuccessorCreationPostgresTest {

    private static final String GATE_PROPERTY = "sanad.scp.subscription.expired-successor.enabled";
    private static final int RACERS = 6;

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;

    private UUID planA;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ConcurrentSuccessorCreationPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ConcurrentSuccessorCreationPostgresTest.");
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
        System.clearProperty(GATE_PROPERTY);
    }

    @BeforeEach
    void migrateAndSeed() {
        String url = MigrationTestSchemaSupport.getIsolatedJdbcUrl(isolatedUrl);
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
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
        planA = seedPlan("R0C10-J");
        System.clearProperty(GATE_PROPERTY);
    }

    @AfterEach
    void clearGate() {
        System.clearProperty(GATE_PROPERTY);
    }

    // ---------------------------------------------------------------
    // QJ-01 — concurrent successor creation on an EXPIRED-only tenant
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QJ-01: concurrent successor creations — one winner, deterministic CONFLICT losers, effective rows = 1")
    void qj01_concurrentSuccessorCreation() throws Exception {
        UUID tenant = newTenant();
        UUID expired = expiredSubscription(tenant);
        System.setProperty(GATE_PROPERTY, "true");

        RaceResult result = race(tenant);

        assertThat(result.winners).as("exactly one creation wins").isEqualTo(1);
        assertThat(result.conflicts).as("all losers receive the deterministic domain CONFLICT")
                .isEqualTo(RACERS - 1);
        assertThat(result.other).as("no unexpected outcomes").isZero();

        // Database invariant: EFFECTIVE_ROW_COUNT <= 1; history intact.
        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(2L);
        assertThat(field(expired, "status")).isEqualTo("EXPIRED");

        // The winner is a no-trial successor (Task E contract).
        UUID winner = jdbc.queryForObject(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')", UUID.class, tenant);
        assertThat(field(winner, "status")).isEqualTo("ACTIVE");
        assertThat(field(winner, "trial_ends_at")).isNull();
    }

    // ---------------------------------------------------------------
    // QJ-02 — concurrent first creations on a history-less tenant
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QJ-02: concurrent first creations — the partial unique index backstops the guard race")
    void qj02_concurrentFirstCreation() throws Exception {
        UUID tenant = newTenant();

        RaceResult result = race(tenant);

        assertThat(result.winners).isEqualTo(1);
        assertThat(result.conflicts).isEqualTo(RACERS - 1);
        assertThat(result.other).isZero();
        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // race harness
    // ---------------------------------------------------------------

    private record RaceResult(int winners, int conflicts, int other) {
    }

    private RaceResult race(UUID tenant) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch ready = new CountDownLatch(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            futures.add(pool.submit(() -> {
                SaasAdministrationService service = service();
                ready.countDown();
                start.await();
                try {
                    service.createSubscription(
                            new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null);
                    winners.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        return new RaceResult(winners.get(), conflicts.get(), other.get());
    }

    /** Each racer gets its own service + connection plumbing (independent transactions). */
    private SaasAdministrationService service() {
        String url = MigrationTestSchemaSupport.getIsolatedJdbcUrl(isolatedUrl);
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        JdbcTemplate racerJdbc = new JdbcTemplate(ds);
        return new SaasAdministrationService(racerJdbc, audit, publishedEvents::add, null);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private UUID expiredSubscription(UUID tenant) {
        UUID sub = transactions.execute(status -> new SaasAdministrationService(jdbc, audit, publishedEvents::add, null)
                .createSubscription(new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null).id());
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), sub);
        new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                transactions, Clock.systemUTC()).runTrialExpiryCycleOnce();
        assertThat(field(sub, "status")).isEqualTo("EXPIRED");
        return sub;
    }

    private UUID newTenant() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID seedPlan(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb,
                                                created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', 30000, 300000, 14, 10, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code);
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 1, 'ACTIVE', NOW(), 'SAR', 30000, 300000, 14, 10, 5, 1024, NOW(), NOW())
                        """,
                UUID.randomUUID(), id);
        return id;
    }

    private Object field(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long historyCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", Long.class, tenantId);
        return count == null ? 0L : count;
    }

    private long effectiveCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')", Long.class, tenantId);
        return count == null ? 0L : count;
    }
}
