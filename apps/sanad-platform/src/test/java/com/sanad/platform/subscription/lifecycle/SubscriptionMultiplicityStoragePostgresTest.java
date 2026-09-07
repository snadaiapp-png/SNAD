package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-10 — MODEL_B SUBSCRIPTION MULTIPLICITY STORAGE INVARIANT (PostgreSQL Direct).
 *
 * <p>Proves the storage contract after migration
 * {@code V20260906_1__scp_subscription_multiplicity_model_b}:</p>
 *
 * <ul>
 *   <li>DB-01 — migration inventory: legacy {@code uk_tenant_subscriptions_tenant}
 *       UNIQUE(tenant_id) is REMOVED; the partial unique index
 *       {@code uk_tenant_subscriptions_effective} (tenant_id WHERE status NOT IN
 *       terminal) and the {@code idx_tenant_subscriptions_tenant_status} lookup
 *       index EXIST.</li>
 *   <li>DB-02 — tenant with EXPIRED + ACTIVE = allowed.</li>
 *   <li>DB-03 — tenant with EXPIRED + EXPIRED + ACTIVE = allowed.</li>
 *   <li>DB-04 — tenant with CANCELLED + EXPIRED + ACTIVE = allowed.</li>
 *   <li>DB-05 — tenant with ACTIVE + ACTIVE = rejected.</li>
 *   <li>DB-06 — tenant with TRIAL + ACTIVE = rejected (both non-terminal).</li>
 *   <li>DB-07 — multiple terminal historical rows = allowed.</li>
 *   <li>DB-08 — different tenants remain isolated.</li>
 *   <li>DB-09 — uniqueness is concurrency-safe: two simultaneous effective-row
 *       inserts → exactly one row survives.</li>
 *   <li>DB-10 — RLS state on {@code tenant_subscriptions} is untouched by the
 *       migration (pre-existing relrowsecurity state preserved, no policy added).</li>
 * </ul>
 *
 * <p>Storage facts are exercised with direct INSERTs (the established
 * repository convention for storage-level proofs, cf. R0C-9 PG-06).</p>
 */
class SubscriptionMultiplicityStoragePostgresTest {

    private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SubscriptionMultiplicityStoragePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping SubscriptionMultiplicityStoragePostgresTest.");
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
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource ds = new DriverManagerDataSource(isolatedUrl, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
    }

    // ---------------------------------------------------------------
    // DB-01 — migration inventory
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-01: legacy UNIQUE(tenant_id) removed; partial effective unique + (tenant_id,status) index present")
    void db01_migrationInventory() {
        Long legacyConstraint = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint "
                        + "WHERE conname = 'uk_tenant_subscriptions_tenant' "
                        + "AND conrelid = 'tenant_subscriptions'::regclass", Long.class);
        assertThat(legacyConstraint).as("legacy full-tenant UNIQUE must be dropped").isZero();

        String partialDef = jdbc.queryForObject(
                "SELECT pg_get_indexdef(indexrelid) FROM pg_index "
                        + "WHERE indexrelid = 'uk_tenant_subscriptions_effective'::regclass", String.class);
        assertThat(partialDef)
                .contains("UNIQUE")
                .contains("tenant_id")
                .contains("status")
                .contains("CANCELLED")
                .contains("EXPIRED")
                .contains("TERMINATED");

        Long statusIndex = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE tablename = 'tenant_subscriptions' "
                        + "AND indexname = 'idx_tenant_subscriptions_tenant_status'", Long.class);
        assertThat(statusIndex).as("(tenant_id, status) lookup index must exist").isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // DB-02..DB-04 — terminal history + one effective row = allowed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-02: tenant with EXPIRED + ACTIVE is allowed")
    void db02_expiredPlusActiveAllowed() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "ACTIVE");

        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(2L);
    }

    @Test
    @DisplayName("DB-03: tenant with EXPIRED + EXPIRED + ACTIVE is allowed")
    void db03_twoExpiredPlusActiveAllowed() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "ACTIVE");

        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(3L);
    }

    @Test
    @DisplayName("DB-04: tenant with CANCELLED + EXPIRED + ACTIVE is allowed")
    void db04_cancelledExpiredActiveAllowed() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "CANCELLED");
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "ACTIVE");

        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(3L);
    }

    // ---------------------------------------------------------------
    // DB-05/DB-06 — two non-terminal rows = rejected
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-05: tenant with ACTIVE + ACTIVE is rejected by the partial unique index")
    void db05_twoActiveRejected() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "ACTIVE");

        assertThatThrownBy(() -> insertSubscription(tenant, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tenant_subscriptions_effective");

        assertThat(effectiveCount(tenant)).isEqualTo(1L);
    }

    @Test
    @DisplayName("DB-06: tenant with TRIAL + ACTIVE is rejected (both non-terminal)")
    void db06_trialPlusActiveRejected() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "TRIAL");

        assertThatThrownBy(() -> insertSubscription(tenant, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tenant_subscriptions_effective");

        assertThat(effectiveCount(tenant)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // DB-07/DB-08 — terminal multiplicity + tenant isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-07: multiple terminal historical rows are allowed")
    void db07_multipleTerminalRowsAllowed() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "CANCELLED");
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "TERMINATED");
        insertSubscription(tenant, "EXPIRED");

        assertThat(effectiveCount(tenant)).isZero();
        assertThat(historyCount(tenant)).isEqualTo(4L);
    }

    @Test
    @DisplayName("DB-08: different tenants remain isolated under the partial unique index")
    void db08_tenantIsolation() {
        UUID tenantA = newTenant();
        UUID tenantB = newTenant();
        insertSubscription(tenantA, "ACTIVE");
        insertSubscription(tenantB, "ACTIVE");
        insertSubscription(tenantB, "EXPIRED");

        assertThat(effectiveCount(tenantA)).isEqualTo(1L);
        assertThat(effectiveCount(tenantB)).isEqualTo(1L);
        assertThat(historyCount(tenantA)).isEqualTo(1L);
        assertThat(historyCount(tenantB)).isEqualTo(2L);
    }

    // ---------------------------------------------------------------
    // DB-09 — concurrency safety of the partial unique index
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-09: concurrent effective-row inserts — exactly one survives (real PostgreSQL race)")
    void db09_concurrentEffectiveInserts() throws Exception {
        final UUID tenant = newTenant();
        int racers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < racers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    insertSubscription(tenant, "ACTIVE");
                    winners.incrementAndGet();
                } catch (DataIntegrityViolationException expected) {
                    // loser of the race — translated by the service layer
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

        assertThat(winners.get()).isEqualTo(1);
        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // DB-10 — RLS state untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-10: tenant_subscriptions RLS state is unchanged by the migration (no weakening, no addition)")
    void db10_rlsStateUntouched() {
        Boolean relRowSecurity = jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class "
                        + "WHERE relname = 'tenant_subscriptions' "
                        + "AND relnamespace = 'public'::regnamespace", Boolean.class);
        // Pre-existing repository state: tenant_subscriptions is NOT RLS-enforced
        // (SCP core isolates at the application layer). The migration must not
        // have changed that in either direction.
        assertThat(relRowSecurity).isFalse();

        Long policies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_policies WHERE tablename = 'tenant_subscriptions'", Long.class);
        assertThat(policies).isZero();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

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

    /** Storage-layer INSERT (established convention for storage-level proofs). */
    private void insertSubscription(UUID tenantId, String status) {
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'MONTHLY', 1, 0, NOW(), NULL, "
                        + "NOW(), NOW() + INTERVAL '30 days', FALSE, 'CURRENT', NOW(), NOW())",
                UUID.randomUUID(), tenantId, anyExistingPlanId(), null, status);
    }

    private UUID anyExistingPlanId() {
        return jdbc.queryForObject("SELECT id FROM saas_plans LIMIT 1", UUID.class);
    }

    private long effectiveCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')",
                Long.class, tenantId);
        return count == null ? 0L : count;
    }

    private long historyCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?",
                Long.class, tenantId);
        return count == null ? 0L : count;
    }
}
