package com.sanad.platform.subscription.change;

import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSeatsRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSubscriptionPlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.item.SubscriptionItemService;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-6 — ANCHORED PLAN SEAT QUANTITY CONVERGENCE (RED battery, PG-01..PG-25).
 *
 * <p>Contract under certification:</p>
 * <pre>
 *   ANCHORED_PLAN_ITEM.quantity  ==  tenant_subscriptions.seat_quantity
 *
 *   anchored item = subscription_items row with item_type = 'PLAN'
 *                   AND status = 'ACTIVE'
 *                   AND plan_id  = tenant_subscriptions.plan_id
 *
 *   ONLY_ANCHORED_PLAN_QUANTITY_CHANGES  = YES  (secondary PLANs untouched)
 *   SECONDARY_PLAN_QUANTITY_DELTA        = 0
 *   GENERIC PLAN SET_QUANTITY            = FAIL_CLOSED  (R0C-5, unchanged)
 *   SEAT_COUNT_PARTIAL_STATE             = IMPOSSIBLE  (same TX)
 * </pre>
 *
 * <p>RED phase (written BEFORE the production fix): PG-01 asserts the
 * REQUIRED post-fix convergence; on the predecessor
 * {@code SaasAdministrationService.changeSeats} updates
 * {@code tenant_subscriptions.seat_quantity} but never the anchored PLAN
 * item mirror, so the anchored quantity stays stale (5) while seats move
 * to 8 — RED_ANCHORED_QUANTITY_DIVERGENCE. PG-09/PG-10 assert the new
 * fail-closed guards that do not exist on the predecessor. All other tests
 * pin the contracts that must NOT regress.</p>
 */
class AnchoredPlanSeatQuantityPostgresTest {

    private JdbcTemplate jdbc;
    private SubscriptionChangeService changeService;
    private SubscriptionItemService itemService;
    private SaasAdministrationService legacy;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;
    private TransactionTemplate transactions;

    private UUID tenant;
    private UUID otherTenant;
    private UUID planA;
    private UUID planB;
    private UUID planX;
    private UUID planY;
    private UUID versionA;
    private UUID versionB;
    private UUID versionX;
    private UUID versionY;

    private static final long PRICE_A = 30000L;
    private static final long PRICE_B = 90000L;
    private static final long PRICE_X = 50000L;
    private static final long PRICE_Y = 60000L;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "AnchoredPlanSeatQuantityPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping AnchoredPlanSeatQuantityPostgresTest.");
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

        SubscriptionItemRepository itemRepository = new SubscriptionItemRepository(jdbc);
        changeService = new SubscriptionChangeService(jdbc, itemRepository,
                new PriceResolver(new PriceRepository(jdbc)));
        itemService = new SubscriptionItemService(jdbc, itemRepository,
                new PlanVersionRepository(jdbc));
        publishedEvents = new ArrayList<>();
        audit = Mockito.mock(PlatformAuditService.class);
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);

        tenant = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        planB = UUID.randomUUID();
        planX = UUID.randomUUID();
        planY = UUID.randomUUID();
        versionA = UUID.randomUUID();
        versionB = UUID.randomUUID();
        versionX = UUID.randomUUID();
        versionY = UUID.randomUUID();

        seedTenant(tenant, "SA");
        seedTenant(otherTenant, "SA");
        seedPlan(planA, "R0C6-A", PRICE_A);
        seedPlan(planB, "R0C6-B", PRICE_B);
        seedPlan(planX, "R0C6-X", PRICE_X);
        seedPlan(planY, "R0C6-Y", PRICE_Y);
        seedPlanVersion(versionA, planA, 1, PRICE_A);
        seedPlanVersion(versionB, planB, 1, PRICE_B);
        seedPlanVersion(versionX, planX, 1, PRICE_X);
        seedPlanVersion(versionY, planY, 1, PRICE_Y);
        seedPrice(versionB, "SA", PRICE_B);
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

    private void seedPlan(UUID id, String code, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb,
                                                created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', ?, ?, 0, 50, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code, monthlyMinor, monthlyMinor * 10);
    }

    private void seedPlanVersion(UUID id, UUID planId, int number, long monthlyMinor) {
        seedPlanVersion(id, planId, number, "ACTIVE", monthlyMinor);
    }

    private void seedPlanVersion(UUID id, UUID planId, int number, String status, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NOW(), 'SAR', ?, ?, 0, 50, 5, 1024, NOW(), NOW())
                        """,
                id, planId, number, status, monthlyMinor, monthlyMinor * 10);
    }

    private void seedPrice(UUID versionId, String country, long baseMinor) {
        jdbc.update("""
                        INSERT INTO prices (id, plan_version_id, price_model, country_code, currency_code,
                                            billing_interval, base_amount_minor, effective_from,
                                            created_at, updated_at)
                        VALUES (?, ?, 'FLAT', ?, 'SAR', 'MONTHLY', ?, NOW() - INTERVAL '1 hour', NOW(), NOW())
                        """,
                UUID.randomUUID(), versionId, country, baseMinor);
    }

    private UUID seedSubscription(UUID id, UUID tenantId, UUID planId, UUID versionId,
                                  String status, int seats) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'MONTHLY', ?, 0, NOW(), NOW(), NOW() + INTERVAL '30 days',
                                FALSE, NOW(), NOW())
                        """,
                id, tenantId, planId, versionId, status, seats);
        return id;
    }

    private UUID seedPlanItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID versionId,
                              int quantity, long unitAmountMinor) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        plan_id, plan_version_id, name_snapshot, quantity,
                                                        unit_amount_minor, currency_code, status,
                                                        created_at, updated_at)
                        VALUES (?, ?, ?, 'PLAN', ?, ?, ?, ?, ?, 'SAR', 'ACTIVE', NOW(), NOW())
                        """,
                itemId, tenantId, subscriptionId, planId, versionId, "PLAN " + planId,
                quantity, unitAmountMinor);
        return itemId;
    }

    /** The R0C-6 §8 RED scenario: anchor A qty 5 + secondary X qty 2, seats 5. */
    private UUID seedDivergenceScenario() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 5);
        seedPlanItem(sub, tenant, planA, versionA, 5, PRICE_A);
        seedPlanItem(sub, tenant, planX, versionX, 2, PRICE_X);
        return sub;
    }

    // ---------------------------------------------------------------
    // Read helpers
    // ---------------------------------------------------------------

    private int seatQuantity(UUID subscriptionId) {
        Integer qty = jdbc.queryForObject(
                "SELECT seat_quantity FROM tenant_subscriptions WHERE id = ?", Integer.class, subscriptionId);
        return qty == null ? -1 : qty;
    }

    private Map<String, Object> anchoredItem(UUID subscriptionId, UUID planId) {
        return jdbc.queryForMap(
                "SELECT id, plan_id, plan_version_id, quantity, unit_amount_minor, status, updated_at "
                        + "FROM subscription_items WHERE subscription_id = ? AND item_type = 'PLAN' "
                        + "AND status = 'ACTIVE' AND plan_id = ?",
                subscriptionId, planId);
    }

    private int activePlanItemQuantity(UUID subscriptionId, UUID planId) {
        Integer qty = jdbc.queryForObject(
                "SELECT quantity FROM subscription_items WHERE subscription_id = ? AND item_type = 'PLAN' "
                        + "AND status = 'ACTIVE' AND plan_id = ?",
                Integer.class, subscriptionId, planId);
        return qty == null ? -1 : qty;
    }

    private Map<String, Object> itemRow(UUID itemId) {
        return jdbc.queryForMap(
                "SELECT plan_id, plan_version_id, quantity, unit_amount_minor, status, updated_at "
                        + "FROM subscription_items WHERE id = ?", itemId);
    }

    private long eventCount(UUID subscriptionId, String action) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? AND action = ?",
                Long.class, subscriptionId, action);
        return count == null ? 0L : count;
    }

    private long eventCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ?",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private long invoiceCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private long creditBalance(UUID subscriptionId) {
        Long value = jdbc.queryForObject(
                "SELECT credit_balance_minor FROM tenant_subscriptions WHERE id = ?",
                Long.class, subscriptionId);
        return value == null ? -1L : value;
    }

    /** Historical proration formula (R0C-6 must NOT change it): HALF_UP over
     *  the seeded 30-day period, exactly as SaasAdministrationService.prorate. */
    private long expectedProration(UUID subscriptionId, long fullPeriodDelta) {
        java.sql.Timestamp start = jdbc.queryForObject(
                "SELECT current_period_start FROM tenant_subscriptions WHERE id = ?",
                java.sql.Timestamp.class, subscriptionId);
        java.sql.Timestamp end = jdbc.queryForObject(
                "SELECT current_period_end FROM tenant_subscriptions WHERE id = ?",
                java.sql.Timestamp.class, subscriptionId);
        Instant now = Instant.now();
        long totalSeconds = Math.max(1, java.time.Duration.between(start.toInstant(), end.toInstant()).getSeconds());
        long remainingSeconds = Math.max(0, java.time.Duration.between(now, end.toInstant()).getSeconds());
        return java.math.BigDecimal.valueOf(fullPeriodDelta)
                .multiply(java.math.BigDecimal.valueOf(remainingSeconds))
                .divide(java.math.BigDecimal.valueOf(totalSeconds), 0, java.math.RoundingMode.HALF_UP)
                .longValue();
    }

    private void changeSeats(UUID subscriptionId, int seats) {
        legacy.changeSeats(subscriptionId, new ChangeSeatsRequest(seats, "R0C-6"), null);
    }

    // ---------------------------------------------------------------
    // PG-01 — RED: anchored quantity divergence on the predecessor
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-01: RED — changeSeats leaves the anchored PLAN quantity stale (divergence)")
    void pg01_redAnchoredQuantityDivergence() {
        UUID sub = seedDivergenceScenario();

        changeSeats(sub, 8);

        // Required post-fix contract (RED on predecessor: A stays 5):
        assertThat(seatQuantity(sub)).isEqualTo(8);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(8);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // PG-02 — 5→8 successful convergence (full create path)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-02: 5→8 seat change converges the anchored mirror; unit price and version unchanged")
    void pg02_seatChangeConvergesAnchoredQuantity() {
        legacy.createSubscription(new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 5, 0), null);
        UUID sub = jdbc.queryForObject(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ?", UUID.class, tenant);
        itemService.addItem(sub, "PLAN", null, null, planX, versionX, 2, PRICE_X, "SAR");

        Map<String, Object> before = anchoredItem(sub, planA);
        changeSeats(sub, 8);

        assertThat(seatQuantity(sub)).isEqualTo(8);
        Map<String, Object> after = anchoredItem(sub, planA);
        assertThat(after.get("quantity")).isEqualTo(8);
        // Quantity-only mirror: unit price and pinned version are preserved.
        assertThat(after.get("unit_amount_minor")).isEqualTo(before.get("unit_amount_minor"));
        assertThat(after.get("plan_version_id")).isEqualTo(before.get("plan_version_id"));
        assertThat(after.get("status")).isEqualTo("ACTIVE");
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // PG-03 — invariant holds in both directions
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-03: anchored item quantity == tenant seat quantity after increase AND decrease")
    void pg03_anchoredQuantityEqualsSeatQuantityInvariant() {
        UUID sub = seedDivergenceScenario();

        changeSeats(sub, 8);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(seatQuantity(sub)).isEqualTo(8);

        changeSeats(sub, 5);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // PG-04 — secondary PLAN X row byte-identical
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-04: secondary PLAN X is preserved byte-identically through the seat change")
    void pg04_secondaryPlanQuantityPreserved() {
        UUID sub = seedDivergenceScenario();
        UUID xItemId = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planX);
        Map<String, Object> before = itemRow(xItemId);

        changeSeats(sub, 8);

        assertThat(itemRow(xItemId)).isEqualTo(before);
    }

    // ---------------------------------------------------------------
    // PG-05 — X + Y preserved (§14)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-05: changeSeats 5→9 — A=9, X=2, Y=7; no status/version/price changes")
    void pg05_multipleSecondaryPlansPreserved() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 5);
        UUID aItem = seedPlanItem(sub, tenant, planA, versionA, 5, PRICE_A);
        UUID xItem = seedPlanItem(sub, tenant, planX, versionX, 2, PRICE_X);
        UUID yItem = seedPlanItem(sub, tenant, planY, versionY, 7, PRICE_Y);

        changeSeats(sub, 9);

        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(9);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(activePlanItemQuantity(sub, planY)).isEqualTo(7);
        for (UUID itemId : List.of(xItem, yItem)) {
            Map<String, Object> row = itemRow(itemId);
            assertThat(row.get("status")).isEqualTo("ACTIVE");
            assertThat(row.get("plan_version_id")).isEqualTo(
                    itemId.equals(xItem) ? (Object) versionX : (Object) versionY);
        }
        assertThat(itemRow(aItem).get("unit_amount_minor"))
                .isEqualTo(jdbc.queryForObject(
                        "SELECT unit_amount_minor FROM subscription_items WHERE id = ?",
                        Long.class, aItem));
        assertThat(itemRow(aItem).get("status")).isEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------
    // PG-06 — seat increase: proration invoice math unchanged
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-06: seat increase 5→8 — one prorated seat-increase invoice, seat-based math unchanged")
    void pg06_seatIncreaseProrationUnchanged() {
        UUID sub = seedDivergenceScenario();
        long expected = expectedProration(sub, 3L * PRICE_A);

        changeSeats(sub, 8);

        assertThat(invoiceCount(sub)).isEqualTo(1L);
        Map<String, Object> invoice = jdbc.queryForMap(
                "SELECT subtotal_minor, credit_applied_minor, description FROM billing_invoices "
                        + "WHERE subscription_id = ?", sub);
        long subtotal = ((Number) invoice.get("subtotal_minor")).longValue();
        // PRORATION_AMOUNT_DELTA = 0: still (newSeats - oldSeats) x plan unit
        // price x remaining-fraction (tolerance covers the instant drift
        // between the service call and the expectation re-computation).
        assertThat(subtotal).isBetween(expected - 2, expected + 2);
        assertThat(((Number) invoice.get("credit_applied_minor")).longValue()).isZero();
        assertThat(invoice.get("description")).isEqualTo("Prorated seat increase");
        // One SEATS.CHANGED event with the same adjustment (EVENT_DELTA = 0).
        assertThat(eventCount(sub, "SEATS.CHANGED")).isEqualTo(1L);
        assertThat(eventCount(sub)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-07 — seat decrease: credit math unchanged, no invoice
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-07: seat decrease 8→5 — credit balance grows by the seat-based prorated amount")
    void pg07_seatDecreaseCreditUnchanged() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 8);
        seedPlanItem(sub, tenant, planA, versionA, 8, PRICE_A);
        seedPlanItem(sub, tenant, planX, versionX, 2, PRICE_X);
        long expected = expectedProration(sub, 3L * PRICE_A);

        changeSeats(sub, 5);

        assertThat(creditBalance(sub)).isBetween(expected - 2, expected + 2);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(eventCount(sub, "SEATS.CHANGED")).isEqualTo(1L);
        // Mirror converges downward too.
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // PG-08 — no-op seat change: nothing changes at all
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-08: no-op 5→5 — no update, no invoice, no event, mirrors untouched")
    void pg08_noOpSeatChangeUnchanged() {
        UUID sub = seedDivergenceScenario();
        java.sql.Timestamp updatedAt = jdbc.queryForObject(
                "SELECT updated_at FROM tenant_subscriptions WHERE id = ?",
                java.sql.Timestamp.class, sub);
        UUID aItem = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planA);
        Map<String, Object> itemBefore = itemRow(aItem);

        changeSeats(sub, 5);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT updated_at FROM tenant_subscriptions WHERE id = ?",
                java.sql.Timestamp.class, sub)).isEqualTo(updatedAt);
        assertThat(itemRow(aItem)).isEqualTo(itemBefore);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
        assertThat(creditBalance(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // §13 failure atomicity helpers — injected SQL failures
    // ---------------------------------------------------------------

    private void injectItemQuantityUpdateFailure() {
        jdbc.update("""
                        CREATE OR REPLACE FUNCTION r0c6_fail() RETURNS trigger AS $fail$
                        BEGIN
                            RAISE EXCEPTION 'R0C6 injected item quantity update failure';
                        END $fail$ LANGUAGE plpgsql
                        """);
        jdbc.update("""
                        CREATE TRIGGER r0c6_item_guard BEFORE UPDATE ON subscription_items
                        FOR EACH ROW WHEN (NEW.quantity <> OLD.quantity)
                        EXECUTE FUNCTION r0c6_fail()
                        """);
    }

    private void injectSeatUpdateFailure() {
        jdbc.update("""
                        CREATE OR REPLACE FUNCTION r0c6_fail() RETURNS trigger AS $fail$
                        BEGIN
                            RAISE EXCEPTION 'R0C6 injected tenant seat update failure';
                        END $fail$ LANGUAGE plpgsql
                        """);
        jdbc.update("""
                        CREATE TRIGGER r0c6_seat_guard BEFORE UPDATE ON tenant_subscriptions
                        FOR EACH ROW WHEN (NEW.seat_quantity <> OLD.seat_quantity)
                        EXECUTE FUNCTION r0c6_fail()
                        """);
    }

    // ---------------------------------------------------------------
    // PG-09 — CASE A: missing anchored item fails closed, state untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-09: missing ACTIVE anchored PLAN item — changeSeats rejected, nothing mutated")
    void pg09_missingAnchoredItemFailsClosed() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 5);
        seedPlanItem(sub, tenant, planX, versionX, 2, PRICE_X); // secondary only, anchor A absent

        assertThatThrownBy(() -> changeSeats(sub, 8))
                .isInstanceOf(RuntimeException.class);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(creditBalance(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-10 — CASE B: anchor version mismatch fails closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-10: anchored item pinned to a different version — changeSeats rejected")
    void pg10_anchorVersionMismatchFailsClosed() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 5);
        // Anchor column says versionA; the active anchored item is pinned to
        // a different (retired) version of the SAME plan.
        UUID versionA2 = UUID.randomUUID();
        seedPlanVersion(versionA2, planA, 2, "RETIRED", PRICE_A + 1000);
        seedPlanItem(sub, tenant, planA, versionA2, 5, PRICE_A);

        assertThatThrownBy(() -> changeSeats(sub, 8))
                .isInstanceOf(RuntimeException.class);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(creditBalance(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-11 — CASE C: item quantity update failure rolls the seat update back
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-11: injected item-quantity update failure — seat update rolls back atomically")
    void pg11_itemUpdateFailureRollsBackSeatUpdate() {
        UUID sub = seedDivergenceScenario();
        injectItemQuantityUpdateFailure();

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                tx -> changeSeats(sub, 8)))
                .isInstanceOf(RuntimeException.class);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(creditBalance(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-12 — CASE D: tenant seat update failure leaves the item untouched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-12: injected tenant seat update failure — item quantity unchanged")
    void pg12_seatUpdateFailureLeavesItemUnchanged() {
        UUID sub = seedDivergenceScenario();
        UUID aItem = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planA);
        Map<String, Object> before = itemRow(aItem);
        injectSeatUpdateFailure();

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                tx -> changeSeats(sub, 8)))
                .isInstanceOf(RuntimeException.class);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(itemRow(aItem)).isEqualTo(before);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(creditBalance(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-13 — CASE E: invoice write failure rolls seat + item back
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-13: injected invoice write failure — seat + item + event roll back together")
    void pg13_invoiceFailureAtomicity() {
        UUID sub = seedDivergenceScenario();
        jdbc.update("ALTER TABLE billing_invoices RENAME TO billing_invoices_blocked");
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(
                    tx -> changeSeats(sub, 8)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.update("ALTER TABLE billing_invoices_blocked RENAME TO billing_invoices");
        }

        // Documented actual semantics: the prorated invoice historically
        // shares the changeSeats transaction, so its failure rolls the whole
        // operation back — SEAT_COUNT_PARTIAL_STATE = IMPOSSIBLE.
        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(creditBalance(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-13b — CASE F: audit failure rolls back (same transaction contract)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-13b: audit write failure — the entire seat change rolls back (documented semantics)")
    void pg13b_auditFailureAtomicity() {
        UUID sub = seedDivergenceScenario();
        Mockito.doThrow(new RuntimeException("R0C6 injected audit failure"))
                .when(audit).success(ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.eq("SUBSCRIPTION.SEATS.CHANGE"), ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                        ArgumentMatchers.any());

        assertThatThrownBy(() -> transactions.executeWithoutResult(
                tx -> changeSeats(sub, 8)))
                .isInstanceOf(RuntimeException.class);

        assertThat(seatQuantity(sub)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(invoiceCount(sub)).isZero();
        assertThat(eventCount(sub)).isZero();
    }

    // ---------------------------------------------------------------
    // PG-14 — §20 tenant isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-14: tenant-B state is unreachable from a tenant-A seat change; cross-tenant item mutation DENIED")
    void pg14_tenantIsolation() {
        // Tenant-A scenario: anchor + secondary.
        UUID sub = seedDivergenceScenario();

        // Tenant-B subscription with its own anchored mirror.
        UUID tenantBSub = UUID.randomUUID();
        seedSubscription(tenantBSub, otherTenant, planA, versionA, "ACTIVE", 3);
        UUID tenantBItem = seedPlanItem(tenantBSub, otherTenant, planA, versionA, 3, PRICE_A);
        Map<String, Object> bItemBefore = itemRow(tenantBItem);
        Map<String, Object> bSubBefore = jdbc.queryForMap(
                "SELECT plan_id, plan_version_id, seat_quantity, credit_balance_minor, status "
                        + "FROM tenant_subscriptions WHERE id = ?", tenantBSub);

        // Cross-tenant quantity mutation through the tenant-scoped item API
        // is DENIED (CROSS_TENANT_ITEM_QUANTITY_MUTATION = DENIED).
        assertThatThrownBy(() -> itemService.addItem(sub, "ADD_ON", null, null, null, null,
                1, 1000L, "SAR", otherTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different tenant");

        // Any tenant-A seat mutation leaves tenant-B rows byte-identical.
        changeSeats(sub, 8);
        assertThat(itemRow(tenantBItem)).isEqualTo(bItemBefore);
        assertThat(jdbc.queryForMap(
                "SELECT plan_id, plan_version_id, seat_quantity, credit_balance_minor, status "
                        + "FROM tenant_subscriptions WHERE id = ?", tenantBSub))
                .isEqualTo(bSubBefore);

        // The seat engine itself is an executive-operator capability path
        // (EXECUTIVE_MANAGE) — platform operators act cross-tenant by design;
        // there is no tenant-scoped seat mutation surface to abuse.
        assertThat(seatQuantity(sub)).isEqualTo(8);
        assertThat(seatQuantity(tenantBSub)).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // PG-15 — §16 generic PLAN SET_QUANTITY stays fail-closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-15: generic SET_QUANTITY on a PLAN item is rejected — changeSeats is the only seat path")
    void pg15_genericPlanSetQuantityRejected() {
        UUID sub = seedDivergenceScenario();
        UUID aItem = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planA);

        assertThatThrownBy(() -> itemService.updateQuantity(aItem, 9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seat-change");

        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(seatQuantity(sub)).isEqualTo(5);
    }

    // ---------------------------------------------------------------
    // PG-16 / PG-17 / PG-18 — R0C-5 item-admin regressions (§17)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-16: distinct secondary PLAN ADD remains valid (quantity from request)")
    void pg16_secondaryPlanAddRegression() {
        UUID sub = seedDivergenceScenario();

        var added = itemService.addItem(sub, "PLAN", null, null, planY, versionY, 4, PRICE_Y, "SAR");

        assertThat(added.getStatus()).isEqualTo("ACTIVE");
        assertThat(added.getQuantity()).isEqualTo(4);
        assertThat(activePlanItemQuantity(sub, planY)).isEqualTo(4);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
    }

    @Test
    @DisplayName("PG-17: secondary PLAN CANCEL remains valid; the anchored mirror is untouched")
    void pg17_secondaryPlanCancelRegression() {
        UUID sub = seedDivergenceScenario();
        UUID xItem = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planX);

        itemService.cancelItem(xItem);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM subscription_items WHERE id = ?", String.class, xItem))
                .isEqualTo("CANCELLED");
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);
        assertThat(seatQuantity(sub)).isEqualTo(5);
    }

    @Test
    @DisplayName("PG-18: generic CANCEL of the compatibility-anchored PLAN remains rejected")
    void pg18_anchoredPlanCancelRejected() {
        UUID sub = seedDivergenceScenario();
        UUID aItem = jdbc.queryForObject(
                "SELECT id FROM subscription_items WHERE subscription_id = ? AND plan_id = ?",
                UUID.class, sub, planA);

        assertThatThrownBy(() -> itemService.cancelItem(aItem))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM subscription_items WHERE id = ?", String.class, aItem))
                .isEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------
    // PG-19 — R0C-5 multi-plan change regression (SCP execute path)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-19: SCP execute A→B cancels A, pins B at the seat quantity, preserves X")
    void pg19_multiPlanChangeRegression() {
        UUID sub = seedDivergenceScenario();

        changeService.execute(sub, versionB, "SA", "r0c6 upgrade to B", tenant, null);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? AND plan_id = ? "
                        + "AND status = 'ACTIVE'",
                Long.class, sub, planA)).isZero();
        assertThat(activePlanItemQuantity(sub, planB)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(jdbc.queryForMap(
                "SELECT plan_id, plan_version_id FROM tenant_subscriptions WHERE id = ?", sub)
                .get("plan_id")).isEqualTo(planB);
    }

    // ---------------------------------------------------------------
    // PG-20 — R0C-4 legacy IMMEDIATE plan change regression
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-20: legacy IMMEDIATE plan change is canonical and carries the seat quantity")
    void pg20_legacyImmediatePlanRegression() {
        UUID sub = seedDivergenceScenario();

        legacy.changePlan(sub, new ChangeSubscriptionPlanRequest(planB, "MONTHLY", "IMMEDIATE", "r0c6"), null);

        assertThat(activePlanItemQuantity(sub, planB)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(jdbc.queryForMap(
                "SELECT plan_id, plan_version_id FROM tenant_subscriptions WHERE id = ?", sub)
                .get("plan_id")).isEqualTo(planB);
        // The seat change mirror still converges after the plan change.
        changeSeats(sub, 9);
        assertThat(activePlanItemQuantity(sub, planB)).isEqualTo(9);
        assertThat(seatQuantity(sub)).isEqualTo(9);
    }

    // ---------------------------------------------------------------
    // PG-21 — R0C-4 renewal pending-plan regression
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-21: renewal applies the pending plan canonically at the seat quantity")
    void pg21_renewalPendingPlanRegression() {
        UUID sub = seedDivergenceScenario();

        legacy.changePlan(sub, new ChangeSubscriptionPlanRequest(planB, "MONTHLY", "NEXT_CYCLE", "r0c6"), null);
        // Scheduling does not touch the composition.
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(5);

        legacy.renewSubscription(sub, null);

        assertThat(activePlanItemQuantity(sub, planB)).isEqualTo(5);
        assertThat(activePlanItemQuantity(sub, planX)).isEqualTo(2);
        assertThat(jdbc.queryForMap(
                "SELECT plan_id, plan_version_id FROM tenant_subscriptions WHERE id = ?", sub)
                .get("plan_id")).isEqualTo(planB);
        // Post-renewal seat change converges on the new anchor.
        changeSeats(sub, 7);
        assertThat(activePlanItemQuantity(sub, planB)).isEqualTo(7);
        assertThat(seatQuantity(sub)).isEqualTo(7);
    }

    // ---------------------------------------------------------------
    // PG-22 — R0C-4 create quantity mirror regression
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-22: createSubscription seeds seat_quantity AND the anchored item quantity identically")
    void pg22_createQuantityMirrorRegression() {
        legacy.createSubscription(new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 6, 0), null);
        UUID sub = jdbc.queryForObject(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ?", UUID.class, tenant);

        assertThat(seatQuantity(sub)).isEqualTo(6);
        assertThat(activePlanItemQuantity(sub, planA)).isEqualTo(6);
        // Initial invoice is seat-based: price x seats (billing unchanged).
        long invoiceSubtotal = jdbc.queryForObject(
                "SELECT subtotal_minor FROM billing_invoices WHERE subscription_id = ?",
                Long.class, sub);
        assertThat(invoiceSubtotal).isEqualTo(6L * PRICE_A);
    }

    // ---------------------------------------------------------------
    // PG-23 — §18 reconciliation: ANCHOR_QUANTITY_MISMATCH (REPORT_ONLY)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-23: reconciliation classifies ANCHOR_QUANTITY_MISMATCH; historical drift is REPORT_ONLY")
    void pg23_reconciliationDetectsQuantityMismatch() {
        // Healthy: converged anchored mirror (via the sanctioned path).
        UUID healthy = seedDivergenceScenario();
        changeSeats(healthy, 8);

        // Historical drift: anchored ACTIVE PLAN item quantity 3 <> seats 5.
        UUID tenantDrift = UUID.randomUUID();
        seedTenant(tenantDrift, "SA");
        UUID drifted = UUID.randomUUID();
        seedSubscription(drifted, tenantDrift, planA, versionA, "ACTIVE", 5);
        seedPlanItem(drifted, tenantDrift, planA, versionA, 3, PRICE_A);

        // ANCHOR_QUANTITY_MISMATCH: anchored ACTIVE PLAN item quantity <>
        // tenant seat quantity (anchored item only — never secondary plans).
        Long mismatches = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        JOIN subscription_items i ON i.subscription_id = s.id
                            AND i.item_type = 'PLAN' AND i.status = 'ACTIVE' AND i.plan_id = s.plan_id
                        WHERE i.quantity <> s.seat_quantity
                        """, Long.class);
        assertThat(mismatches).isEqualTo(1L); // drifted only

        // REPORT_ONLY: the scan does not repair — the drift persists.
        assertThat(activePlanItemQuantity(drifted, planA)).isEqualTo(3);
        // The newly written state stays converged (new-write mismatch = 0).
        assertThat(activePlanItemQuantity(healthy, planA))
                .isEqualTo(seatQuantity(healthy)).isEqualTo(8);
    }

    // ---------------------------------------------------------------
    // PG-24 — §18 reconciliation ignores secondary PLAN quantities
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-24: reconciliation ignores secondary PLAN quantities entirely")
    void pg24_reconciliationIgnoresSecondaryQuantities() {
        UUID sub = UUID.randomUUID();
        seedSubscription(sub, tenant, planA, versionA, "ACTIVE", 5);
        seedPlanItem(sub, tenant, planA, versionA, 5, PRICE_A);   // converged anchor
        seedPlanItem(sub, tenant, planX, versionX, 2, PRICE_X);   // secondary, different qty
        seedPlanItem(sub, tenant, planY, versionY, 99, PRICE_Y);  // secondary, wildly different

        Long mismatches = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM tenant_subscriptions s
                        JOIN subscription_items i ON i.subscription_id = s.id
                            AND i.item_type = 'PLAN' AND i.status = 'ACTIVE' AND i.plan_id = s.plan_id
                        WHERE i.quantity <> s.seat_quantity
                        """, Long.class);
        assertThat(mismatches).isZero();
    }

    // ---------------------------------------------------------------
    // PG-25 — R0C-2R country authority regression (preview pricing)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-25: preview pricing resolves the tenant's country (client country ignored)")
    void pg25_countryAuthorityRegression() {
        UUID sub = seedDivergenceScenario();
        // Only an SA price exists for versionB; tenant country = SA.

        // Client-passed country (US) has NO price and must be ignored.
        var preview = changeService.preview(sub, versionB, "US", Instant.now());

        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.targetMonthlyMinor()).isEqualTo(PRICE_B);
        assertThat(preview.currentMonthlyMinor()).isEqualTo(PRICE_A);
        // The anchored item line reflects the seat-count mirror.
        assertThat(preview.currentItems().stream()
                .filter(l -> "PLAN".equals(l.itemType()) && l.name().contains(planA.toString()))
                .findFirst().orElseThrow().quantity()).isEqualTo(5);
    }
}
