package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-10 — EFFECTIVE / HISTORY QUERY CONTRACT (Task A, PostgreSQL Direct).
 *
 * <p>Proves the {@link SubscriptionResolutionService} semantics required by
 * the frozen MODEL_B contract:</p>
 *
 * <ul>
 *   <li>QA-01 (A) — ALL_HISTORY returns EVERY subscription belonging to the
 *       tenant (terminal rows included), never fewer.</li>
 *   <li>QA-02 (B) — EFFECTIVE returns only rows with status NOT IN
 *       (CANCELLED, EXPIRED, TERMINATED).</li>
 *   <li>QA-03 (C) — EFFECTIVE returns at most one row.</li>
 *   <li>QA-04 (D) — the historical lookup never arbitrarily supplies the
 *       current subscription: a terminal-only tenant has history but NO
 *       effective subscription.</li>
 *   <li>QA-05 (E) — historical ordering is deterministic:
 *       (created_at DESC, id DESC).</li>
 *   <li>QA-06 — the latest-historical contract (deterministic LIMIT 1 with
 *       explicit ordering) is exposed for the continuation guard.</li>
 * </ul>
 */
class SubscriptionResolutionPostgresTest {

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private SubscriptionResolutionService resolution;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SubscriptionResolutionPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping SubscriptionResolutionPostgresTest.");
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
        resolution = new SubscriptionResolutionService(jdbc);
    }

    // ---------------------------------------------------------------
    // QA-01 — ALL_HISTORY completeness
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QA-01: ALL_HISTORY returns every subscription of the tenant")
    void qa01_allHistoryComplete() {
        UUID tenant = newTenant();
        UUID cancelled = insertSubscription(tenant, "CANCELLED", minutesAgo(90));
        UUID expired1 = insertSubscription(tenant, "EXPIRED", minutesAgo(60));
        UUID expired2 = insertSubscription(tenant, "EXPIRED", minutesAgo(30));
        UUID active = insertSubscription(tenant, "ACTIVE", minutesAgo(10));

        List<SubscriptionResolutionService.HistoricalSubscription> history =
                resolution.findAllHistory(tenant);

        assertThat(history).extracting(SubscriptionResolutionService.HistoricalSubscription::id)
                .containsExactlyInAnyOrder(cancelled, expired1, expired2, active);
        // ALL_HISTORY for a tenant with no rows is empty — never fabricated.
        assertThat(resolution.findAllHistory(UUID.randomUUID())).isEmpty();
    }

    // ---------------------------------------------------------------
    // QA-02/03 — EFFECTIVE semantics and cardinality
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QA-02: EFFECTIVE returns only non-terminal rows")
    void qa02_effectiveOnlyNonTerminal() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "CANCELLED", minutesAgo(90));
        insertSubscription(tenant, "EXPIRED", minutesAgo(60));
        UUID active = insertSubscription(tenant, "ACTIVE", minutesAgo(10));

        var effective = resolution.findEffectiveSubscription(tenant);
        assertThat(effective).isPresent();
        assertThat(effective.get().id()).isEqualTo(active);
        assertThat(effective.get().status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("QA-03: EFFECTIVE returns at most one row")
    void qa03_effectiveAtMostOne() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED", minutesAgo(60));
        insertSubscription(tenant, "ACTIVE", minutesAgo(10));

        // The storage invariant bounds it; the query contract must honor it.
        List<SubscriptionResolutionService.HistoricalSubscription> effectiveRows =
                resolution.findAllHistory(tenant).stream()
                        .filter(h -> !SubscriptionLifecycle.TERMINAL_STATUSES.contains(h.status()))
                        .toList();
        assertThat(effectiveRows.size()).isLessThanOrEqualTo(1);
        assertThat(resolution.findEffectiveSubscription(tenant)).isPresent();
    }

    // ---------------------------------------------------------------
    // QA-04 — history never supplies the current subscription
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QA-04: terminal-only tenant has history but NO effective subscription")
    void qa04_terminalHistoryIsNotEffective() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED", minutesAgo(60));
        insertSubscription(tenant, "CANCELLED", minutesAgo(30));

        assertThat(resolution.findAllHistory(tenant)).hasSize(2);
        assertThat(resolution.findEffectiveSubscription(tenant)).isEmpty();
        assertThat(resolution.hasEffectiveSubscription(tenant)).isFalse();
    }

    // ---------------------------------------------------------------
    // QA-05/06 — deterministic chronology
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QA-05: historical ordering is deterministic (created_at DESC, id DESC)")
    void qa05_deterministicOrdering() {
        UUID tenant = newTenant();
        Instant base = minutesAgo(60);
        UUID oldest = insertSubscription(tenant, "EXPIRED", base);
        UUID newest = insertSubscription(tenant, "ACTIVE", base.plusSeconds(120));

        List<SubscriptionResolutionService.HistoricalSubscription> history =
                resolution.findAllHistory(tenant);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(newest);
        assertThat(history.get(1).id()).isEqualTo(oldest);
    }

    @Test
    @DisplayName("QA-06: latest-historical contract is deterministic and status-bearing")
    void qa06_latestHistoricalContract() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED", minutesAgo(60));
        UUID latest = insertSubscription(tenant, "EXPIRED", minutesAgo(10));

        var latestRow = resolution.findLatestHistorical(tenant);
        assertThat(latestRow).isPresent();
        assertThat(latestRow.get().id()).isEqualTo(latest);
        assertThat(latestRow.get().status()).isEqualTo("EXPIRED");

        // A first-time tenant has no history at all.
        assertThat(resolution.findLatestHistorical(UUID.randomUUID())).isEmpty();
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

    private Instant minutesAgo(long minutes) {
        return Instant.now().minusSeconds(minutes * 60);
    }

    private UUID insertSubscription(UUID tenantId, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, (SELECT id FROM saas_plans LIMIT 1), NULL, ?, 'MONTHLY', 1, 0, "
                        + "?, NULL, ?, NOW() + INTERVAL '30 days', FALSE, 'CURRENT', ?, ?)",
                id, tenantId, status, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt), Timestamp.from(createdAt));
        return id;
    }
}
