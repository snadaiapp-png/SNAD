package com.sanad.platform.subscription.change;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.read.SubscriptionDetailService;
import com.sanad.platform.subscription.read.SubscriptionGridQueryService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-RECOVERY-CHAIN STAGE-2 (R0C-3 re-certification) — canonical plan anchor
 * convergence on PostgreSQL Direct.
 *
 * <p>Contract: the ACTIVE PLAN subscription_item is the effective plan
 * composition; {@code tenant_subscriptions.plan_id} and
 * {@code tenant_subscriptions.plan_version_id} are compatibility mirrors
 * (anchors) that must never diverge from it. A plan change performed through
 * the canonical change path must, inside ONE transaction: cancel the old PLAN
 * item, insert the new PLAN item, update BOTH anchors, and write the command
 * ledger.</p>
 *
 * <p>Re-discovered on the recovery branch after the original R0C-3 branch was
 * lost: {@code SubscriptionChangeService.execute()} swapped the PLAN item
 * without touching the anchors, leaving grid/detail read models (which source
 * plan data from the anchors) showing the OLD plan while the effective item
 * pointed at the NEW plan.</p>
 */
class SubscriptionAnchorPostgresTest {

    private JdbcTemplate jdbc;
    private SubscriptionChangeService service;
    private SubscriptionGridQueryService grid;
    private SubscriptionDetailService detail;
    private TransactionTemplate transactions;

    private UUID tenantA;
    private UUID planA;
    private UUID planB;
    private UUID versionA;
    private UUID versionB;
    private UUID subscriptionA;
    private UUID subscriptionB; // tenant B subscription (isolation control)

    private static final Instant NOW = Instant.now();

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SubscriptionAnchorPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping SubscriptionAnchorPostgresTest.");
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
        service = new SubscriptionChangeService(
                jdbc, new SubscriptionItemRepository(jdbc),
                new PriceResolver(new PriceRepository(jdbc)));
        grid = new SubscriptionGridQueryService(jdbc);
        detail = new SubscriptionDetailService(jdbc);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));

        tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        planA = UUID.randomUUID();
        planB = UUID.randomUUID();
        versionA = UUID.randomUUID();
        versionB = UUID.randomUUID();
        subscriptionA = UUID.randomUUID();
        subscriptionB = UUID.randomUUID();

        seedTenant(tenantA, "SA");
        seedTenant(tenantB, "SA");
        seedPlan(planA, "R0C3-A");
        seedPlan(planB, "R0C3-B");
        seedPlanVersion(versionA, planA, 1, "SAR", 30000L);
        seedPlanVersion(versionB, planB, 2, "SAR", 90000L);
        seedSubscription(subscriptionA, tenantA, planA, versionA, "ACTIVE");
        seedSubscription(subscriptionB, tenantB, planA, versionA, "ACTIVE");
        seedPlanItem(subscriptionA, tenantA, planA, versionA, 30000L, "SAR");
        seedPlanItem(subscriptionB, tenantB, planA, versionA, 30000L, "SAR");
        seedPrice(versionB, "SA", "SAR", 90000L);
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

    private void seedPlanVersion(UUID id, UUID planId, int number, String currency, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', NOW(), ?, ?, ?, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, planId, number, currency, monthlyMinor, monthlyMinor * 10);
    }

    private void seedSubscription(UUID id, UUID tenantId, UUID planId, UUID versionId, String status) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'MONTHLY', 1, 0, NOW(), NOW(), NOW() + INTERVAL '30 days',
                                false, NOW(), NOW())
                        """,
                id, tenantId, planId, versionId, status);
    }

    private void seedPlanItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID versionId,
                              long unitAmountMinor, String currency) {
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        plan_id, plan_version_id, name_snapshot, quantity,
                                                        unit_amount_minor, currency_code, status,
                                                        created_at, updated_at)
                        VALUES (?, ?, ?, 'PLAN', ?, ?, 'PLAN-ITEM', 1, ?, ?, 'ACTIVE', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, subscriptionId, planId, versionId, unitAmountMinor, currency);
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

    private UUID anchorPlanId(UUID subscriptionId) {
        return jdbc.queryForObject(
                "SELECT plan_id FROM tenant_subscriptions WHERE id = ?", UUID.class, subscriptionId);
    }

    private UUID anchorVersionId(UUID subscriptionId) {
        return jdbc.queryForObject(
                "SELECT plan_version_id FROM tenant_subscriptions WHERE id = ?",
                UUID.class, subscriptionId);
    }

    private UUID activeItemVersion(UUID subscriptionId) {
        return jdbc.queryForObject(
                "SELECT plan_version_id FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                UUID.class, subscriptionId);
    }

    private long activePlanItemCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    // ---------------------------------------------------------------
    // R0C-3 core: anchors follow the effective PLAN item
    // ---------------------------------------------------------------

    @Test
    @DisplayName("plan change moves the ACTIVE PLAN item AND both compatibility anchors together")
    void planChangeUpdatesAnchorsAndItemTogether() {
        service.execute(subscriptionA, versionB, "SA", "upgrade to B", null, null);

        // Effective composition: exactly one ACTIVE PLAN item, on version B.
        assertThat(activePlanItemCount(subscriptionA)).isEqualTo(1L);
        assertThat(activeItemVersion(subscriptionA)).isEqualTo(versionB);

        // Compatibility mirrors must agree — never diverge.
        assertThat(anchorPlanId(subscriptionA)).isEqualTo(planB);
        assertThat(anchorVersionId(subscriptionA)).isEqualTo(versionB);
    }

    @Test
    @DisplayName("grid read model (anchor-sourced) reflects the effective plan after a change")
    void gridReadModelReflectsEffectivePlanAfterChange() {
        service.execute(subscriptionA, versionB, "SA", "upgrade to B", null, null);

        var page = grid.search(tenantA, null, null, null, false, 0, 10, "created_at", "ASC");
        assertThat(page.content()).hasSize(1);
        var row = page.content().get(0);
        assertThat(row.planId()).isEqualTo(planB);
        assertThat(row.planCode()).isEqualTo("R0C3-B");
        assertThat(row.planVersion()).isEqualTo("v2");
    }

    @Test
    @DisplayName("detail read model (anchor-sourced) reflects the effective plan after a change")
    void detailReadModelReflectsEffectivePlanAfterChange() {
        service.execute(subscriptionA, versionB, "SA", "upgrade to B", null, null);

        var result = detail.detail(subscriptionA);
        assertThat(result.overview().get("planId")).isEqualTo(planB);
        assertThat(result.overview().get("planCode")).isEqualTo("R0C3-B");
        assertThat(((Number) result.overview().get("planVersion")).intValue()).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // Atomicity: CANCEL + INSERT + UPDATE ANCHOR + LEDGER = one transaction
    // ---------------------------------------------------------------

    @Test
    @DisplayName("execute() is transactional (annotation contract)")
    void executeIsTransactional() throws NoSuchMethodException {
        assertThat(SubscriptionChangeService.class
                .getMethod("execute", UUID.class, UUID.class, String.class, String.class,
                        UUID.class, UUID.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("ledger failure rolls back the whole change — no partial state survives")
    void ledgerFailureRollsBackEntireChange() {
        // Inject a deterministic failure at the LAST step of the change
        // (ledger write) by making subscription_commands unavailable.
        jdbc.execute("ALTER TABLE subscription_commands RENAME TO subscription_commands_away");
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(
                            status -> service.execute(subscriptionA, versionB, "SA", "upgrade", null, null)))
                    .isInstanceOf(BadSqlGrammarException.class);

            // Nothing may survive: old item still ACTIVE on version A,
            // exactly one ACTIVE PLAN item, anchors untouched, no new item.
            assertThat(activePlanItemCount(subscriptionA)).isEqualTo(1L);
            assertThat(activeItemVersion(subscriptionA)).isEqualTo(versionA);
            assertThat(anchorPlanId(subscriptionA)).isEqualTo(planA);
            assertThat(anchorVersionId(subscriptionA)).isEqualTo(versionA);
            Long ledger = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_commands_away WHERE subscription_id = ?",
                    Long.class, subscriptionA);
            assertThat(ledger).isZero();
        } finally {
            jdbc.execute("ALTER TABLE subscription_commands_away RENAME TO subscription_commands");
        }
    }

    @Test
    @DisplayName("plan-version lookup failure rolls back cancellation — no partial state survives")
    void itemInsertFailureRollsBackEntireChange() {
        // Deterministic mid-change failure: preview prices from the prices table
        // only, the old item cancels, and the very next statement (plan_versions
        // lookup) detonates. Renaming keeps all FKs intact.
        jdbc.execute("ALTER TABLE plan_versions RENAME TO plan_versions_away");
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(
                            status -> service.execute(subscriptionA, versionB, "SA", "upgrade", null, null)))
                    .isInstanceOf(BadSqlGrammarException.class);

            // Rollback must restore the pre-change state completely: the old
            // item is ACTIVE again on version A, exactly one ACTIVE PLAN item,
            // anchors untouched, nothing cancelled, no ledger row.
            assertThat(activePlanItemCount(subscriptionA)).isEqualTo(1L);
            assertThat(activeItemVersion(subscriptionA)).isEqualTo(versionA);
            assertThat(anchorPlanId(subscriptionA)).isEqualTo(planA);
            assertThat(anchorVersionId(subscriptionA)).isEqualTo(versionA);
            Long cancelled = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? AND status = 'CANCELLED'",
                    Long.class, subscriptionA);
            assertThat(cancelled).isZero();
        } finally {
            jdbc.execute("ALTER TABLE plan_versions_away RENAME TO plan_versions");
        }
    }

    // ---------------------------------------------------------------
    // Tenant isolation: all writes scoped to the owning tenant
    // ---------------------------------------------------------------

    @Test
    @DisplayName("a plan change on tenant A leaves tenant B's subscription untouched")
    void tenantIsolationScopesAllWrites() {
        service.execute(subscriptionA, versionB, "SA", "upgrade to B", null, null);

        // Tenant B: no item swap, no anchor change, no ledger row, no cancellations.
        assertThat(activePlanItemCount(subscriptionB)).isEqualTo(1L);
        assertThat(activeItemVersion(subscriptionB)).isEqualTo(versionA);
        assertThat(anchorPlanId(subscriptionB)).isEqualTo(planA);
        Long bLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                Long.class, subscriptionB);
        assertThat(bLedger).isZero();
        Long bCancelled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? AND status = 'CANCELLED'",
                Long.class, subscriptionB);
        assertThat(bCancelled).isZero();
    }

    // ---------------------------------------------------------------
    // Reconciliation — REPORT_ONLY
    // ---------------------------------------------------------------

    @Test
    @DisplayName("reconciliation classifies historical divergence without repairing it; new writes stay clean")
    void reconciliationIsReportOnlyAndNewWritesAreClean() {
        // A "historical" divergent subscription: anchor plan A but active item
        // pinned to version B (plan-version mismatch), plus a subscription with
        // no ACTIVE PLAN item at all (missing). Each needs its own tenant
        // (uk_tenant_subscriptions_tenant enforces one subscription per tenant).
        UUID tenantHist = UUID.randomUUID();
        UUID tenantMissing = UUID.randomUUID();
        seedTenant(tenantHist, "SA");
        seedTenant(tenantMissing, "SA");
        UUID historicalSub = UUID.randomUUID();
        seedSubscription(historicalSub, tenantHist, planA, null, "ACTIVE");
        seedPlanItem(historicalSub, tenantHist, planA, versionB, 30000L, "SAR"); // item on B, anchor has no version
        UUID missingSub = UUID.randomUUID();
        seedSubscription(missingSub, tenantMissing, planA, versionA, "ACTIVE"); // no PLAN item at all

        // Canonical new write (post-fix: converges anchors + item).
        service.execute(subscriptionA, versionB, "SA", "upgrade to B", null, null);

        // PLAN_VERSION_ID_MISMATCH classification (report).
        Long versionMismatch = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        JOIN subscription_items i ON i.subscription_id = s.id
                            AND i.item_type = 'PLAN' AND i.status = 'ACTIVE'
                        WHERE s.plan_version_id IS DISTINCT FROM i.plan_version_id
                        """, Long.class);
        assertThat(versionMismatch).isEqualTo(1L); // the historical row only

        // MISSING_ACTIVE_PLAN_ITEM classification (report).
        Long missing = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        WHERE NOT EXISTS (
                            SELECT 1 FROM subscription_items i
                            WHERE i.subscription_id = s.id AND i.item_type = 'PLAN' AND i.status = 'ACTIVE')
                        """, Long.class);
        assertThat(missing).isEqualTo(1L);

        // MULTIPLE_ACTIVE_PLAN_ITEMS classification (report).
        Long multiple = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM (
                            SELECT i.subscription_id FROM subscription_items i
                            WHERE i.item_type = 'PLAN' AND i.status = 'ACTIVE'
                            GROUP BY i.subscription_id HAVING COUNT(*) > 1) m
                        """, Long.class);
        assertThat(multiple).isZero();

        // PLAN_ID_MISMATCH classification (report).
        Long planMismatch = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        JOIN subscription_items i ON i.subscription_id = s.id
                            AND i.item_type = 'PLAN' AND i.status = 'ACTIVE'
                        WHERE s.plan_id IS DISTINCT FROM i.plan_id
                        """, Long.class);
        // missingSub has no active item (join drops it); historicalSub item plan is A matching anchor A;
        // the newly-changed subscriptionA is converged. Old cancelled item is excluded by status.
        assertThat(planMismatch).isZero();

        // REPORT_ONLY: the historical rows are classified but NOT repaired.
        assertThat(anchorVersionId(historicalSub)).isNull(); // untouched — repair is a separately authorized task

        // New writes: zero mismatches (subscriptionA converged by the canonical path).
        Long newWriteMismatch = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        JOIN subscription_items i ON i.subscription_id = s.id
                            AND i.item_type = 'PLAN' AND i.status = 'ACTIVE'
                        WHERE s.id = ? AND (s.plan_id IS DISTINCT FROM i.plan_id
                            OR s.plan_version_id IS DISTINCT FROM i.plan_version_id)
                        """, Long.class, subscriptionA);
        assertThat(newWriteMismatch).isZero();
    }
}
