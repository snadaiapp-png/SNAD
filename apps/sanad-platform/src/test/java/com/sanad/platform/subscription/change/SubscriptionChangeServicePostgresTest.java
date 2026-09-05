package com.sanad.platform.subscription.change;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-2R STAGE-1 re-certification — PostgreSQL Direct evidence for the three
 * subscription change-path P0 defects, re-proven on the current repository
 * (old R0C-2R branch was lost; its report is hypothesis-only).
 *
 * <p>P0-A (invalid tenant subscription country source): the pricing country
 * must be sourced server-side from {@code tenants.country_code} — never from
 * a client-supplied value and never from a {@code tenant_subscriptions}
 * column (which does not exist). {@code CLIENT_COUNTRY_AUTHORITY = NONE}.</p>
 *
 * <p>P0-B (multi-column scalar queryForObject): preview/execute must run
 * against real PostgreSQL — single-column scalar mappings cannot receive
 * multi-column SELECTs.</p>
 *
 * <p>P0-C (subscription_commands.to_status schema overflow): the command
 * ledger columns are VARCHAR(24); written values must fit without any new
 * migration (NEW_MIGRATIONS = 0).</p>
 */
class SubscriptionChangeServicePostgresTest {

    private JdbcTemplate jdbc;
    private SubscriptionChangeService service;

    private UUID tenantId;
    private UUID planAId;
    private UUID planBId;
    private UUID versionAId;
    private UUID versionBId;
    private UUID subscriptionId;

    private static final Instant NOW = Instant.now();

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SubscriptionChangeServicePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping SubscriptionChangeServicePostgresTest.");
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

        tenantId = UUID.randomUUID();
        planAId = UUID.randomUUID();
        planBId = UUID.randomUUID();
        versionAId = UUID.randomUUID();
        versionBId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();

        seedTenant(tenantId, "SA");
        seedPlan(planAId, "R0C2R-A");
        seedPlan(planBId, "R0C2R-B");
        seedPlanVersion(versionAId, planAId, 1, "SAR", 30000L);
        seedPlanVersion(versionBId, planBId, 2, "SAR", 90000L);
        seedSubscription(subscriptionId, tenantId, planAId, "ACTIVE");
        seedPlanItem(subscriptionId, tenantId, planAId, versionAId, 30000L, "SAR");

        // Distinct prices per country for target version B so the winning
        // source is unambiguous: SA=90000, AE=50000, GLOBAL=70000.
        seedPrice(versionBId, "SA", "SAR", 90000L);
        seedPrice(versionBId, "AE", "AED", 50000L);
        seedPrice(versionBId, "GLOBAL", "USD", 70000L);
    }

    // ---------------------------------------------------------------
    // Seeding helpers (direct JDBC, minimal required columns)
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

    private void seedSubscription(UUID id, UUID tenantId, UUID planId, String status) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'MONTHLY', 1, 0, NOW(), NOW(), NOW() + INTERVAL '30 days',
                                false, NOW(), NOW())
                        """,
                id, tenantId, planId, status);
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

    private long activePlanItemCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    // ---------------------------------------------------------------
    // P0-B — preview/execute must survive real PostgreSQL
    // ---------------------------------------------------------------

    @Test
    @DisplayName("P0-A/P0-B GREEN: preview prices the target from tenants.country_code — client country is ignored (authority NONE)")
    void previewPricesTargetUsingTenantCountryNotClientCountry() {
        // Client-supplied rogue country "AE" (AE price is 50000; GLOBAL is 70000;
        // the tenant's authoritative country SA prices at 90000).
        SubscriptionChangeService.ChangePreview preview =
                service.preview(subscriptionId, versionBId, "AE", NOW);

        assertThat(preview.targetMonthlyMinor()).isEqualTo(90000L);
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    @DisplayName("P0-A GREEN: tenant without country falls back to the GLOBAL price server-side")
    void previewFallsBackToGlobalWhenTenantCountryMissing() {
        UUID tenantNoCountry = UUID.randomUUID();
        UUID subNoCountry = UUID.randomUUID();
        seedTenant(tenantNoCountry, null);
        seedSubscription(subNoCountry, tenantNoCountry, planAId, "ACTIVE");
        seedPlanItem(subNoCountry, tenantNoCountry, planAId, versionAId, 30000L, "SAR");

        SubscriptionChangeService.ChangePreview preview =
                service.preview(subNoCountry, versionBId, "AE", NOW);

        // GLOBAL price 70000 — again ignoring the client-supplied "AE".
        assertThat(preview.targetMonthlyMinor()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("P0-B GREEN: preview reports the subscription's actual status as fromStatus")
    void previewFromStatusReflectsSubscriptionStatus() {
        SubscriptionChangeService.ChangePreview preview =
                service.preview(subscriptionId, versionBId, "SA", NOW);

        assertThat(preview.fromStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("unknown subscription id is rejected with IllegalArgumentException on both phases")
    void previewRejectsUnknownSubscription() {
        assertThatThrownBy(() -> service.preview(UUID.randomUUID(), versionBId, "SA", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown subscription");
    }

    // ---------------------------------------------------------------
    // P0-C — execute must fit the command-ledger schema (no migration)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("P0-B/P0-C GREEN: execute swaps the PLAN item and writes a schema-compliant ledger row")
    void executeSwapsPlanItemAndWritesSchemaCompliantLedger() {
        SubscriptionChangeService.ChangeResult result =
                service.execute(subscriptionId, versionBId, "AE", "upgrade", null, null);

        assertThat(result.status()).isEqualTo("EXECUTED");

        // Exactly one ACTIVE PLAN item remains, pinned to the target version B
        // at the tenant-country price.
        assertThat(activePlanItemCount(subscriptionId)).isEqualTo(1L);
        Map<String, Object> activeItem = jdbc.queryForMap(
                "SELECT plan_version_id, unit_amount_minor, currency_code FROM subscription_items "
                        + "WHERE subscription_id = ? AND item_type = 'PLAN' AND status = 'ACTIVE'",
                subscriptionId);
        assertThat(activeItem.get("plan_version_id")).isEqualTo(versionBId);
        assertThat(((Number) activeItem.get("unit_amount_minor")).longValue()).isEqualTo(90000L);
        assertThat(activeItem.get("currency_code")).isEqualTo("SAR");

        // Old plan item is CANCELLED.
        Long cancelled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'CANCELLED'",
                Long.class, subscriptionId);
        assertThat(cancelled).isEqualTo(1L);

        // Ledger row: command from the fixed vocabulary, from/to status are the
        // subscription's actual status (length <= 24), TARGET_VERSION detail
        // preserved in reason (VARCHAR(500)).
        Map<String, Object> ledger = jdbc.queryForMap(
                "SELECT command, from_status, to_status, reason FROM subscription_commands "
                        + "WHERE subscription_id = ? ORDER BY created_at DESC LIMIT 1",
                subscriptionId);
        assertThat(ledger.get("command")).isEqualTo("PLAN_CHANGE");
        assertThat(ledger.get("from_status")).isEqualTo("ACTIVE");
        assertThat(((String) ledger.get("to_status"))).hasSizeLessThanOrEqualTo(24);
        assertThat(ledger.get("to_status")).isEqualTo("ACTIVE");
        assertThat((String) ledger.get("reason")).contains("TARGET_VERSION=" + versionBId);
    }

    @Test
    @DisplayName("P0-C contract: to_status VARCHAR(24) rejects the historical oversized value (TARGET_VERSION=<uuid>, 51 chars)")
    void ledgerToStatusColumnRejectsHistoricalOversizedValue() {
        String historicalValue = "TARGET_VERSION=" + versionBId; // 15 + 36 = 51 chars
        assertThat(historicalValue).hasSize(51);

        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO subscription_commands (
                            id, subscription_id, tenant_id, command, from_status, to_status,
                            reason, actor_tenant_id, actor_user_id, correlation_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        """,
                UUID.randomUUID(), subscriptionId, tenantId, "PLAN_CHANGE",
                "CURRENT", historicalValue, "reason", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long");
    }

    // ---------------------------------------------------------------
    // P0-A — the historical country source never existed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("P0-A contract: tenant_subscriptions has no country_code column; only tenants carries it")
    void subscriptionTableHasNoCountryColumn() {
        Long subColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'tenant_subscriptions' AND column_name = 'country_code'",
                Long.class);
        Long tenantColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'tenants' AND column_name = 'country_code'",
                Long.class);
        assertThat(subColumns).isZero();
        assertThat(tenantColumns).isEqualTo(1L);
    }

    @Test
    @DisplayName("P0-A contract: the historical billingInterval SQL fails on the real schema (column does not exist)")
    void historicalBillingCountrySqlTargetsNonexistentColumn() {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT billing_cycle, country_code FROM tenant_subscriptions WHERE id = ?",
                String.class, subscriptionId))
                .isInstanceOf(BadSqlGrammarException.class)
                .hasStackTraceContaining("column \"country_code\" does not exist");
    }
}
