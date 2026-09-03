package com.sanad.platform.subscription.change;

import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSubscriptionPlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.subscription.item.SubscriptionItemEntity;
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
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-5 — MULTI-PLAN COMPATIBILITY ANCHOR + PLAN ITEM AUTHORITY (RED battery).
 *
 * <p>Architectural contract (proven by V20260829_3 and the SCP design doc):
 * a subscription carries 1..N items and DIFFERENT ACTIVE PLAN items may
 * coexist (ERP + HRM + CRM). The unique index {@code
 * uk_subscription_items_active_plan} rejects only the same-plan duplicate.
 * Therefore the compatibility-phase invariant is:</p>
 *
 * <pre>
 *   MULTIPLE_DISTINCT_ACTIVE_PLAN_ITEMS = VALID
 *   DUPLICATE_ACTIVE_SAME_PLAN          = INVALID
 *   LEGACY_ANCHORED_PLAN_ITEM_COUNT     = 1  (the item matching
 *       tenant_subscriptions.plan_id, pinned to plan_version_id when present)
 * </pre>
 *
 * <p>RED phase: this battery is written BEFORE the production fix and asserts
 * the REQUIRED post-fix behavior against the current (pre-fix) code. The
 * failing tests prove the single-plan defects:</p>
 * <ul>
 *   <li>§5 — {@code SubscriptionItemRepository.findActiveBySubscriptionIdAndType}
 *       is a singular {@code queryForObject}: with two ACTIVE PLAN rows it
 *       throws {@code IncorrectResultSetColumnCountException}
 *       (SINGULAR_PLAN_TYPE_LOOKUP_RED).</li>
 *   <li>§8 — {@code preview} picks {@code findFirst()} over the ACTIVE PLAN
 *       stream: an older-created SECONDARY plan wins instead of the anchored
 *       plan.</li>
 *   <li>§8 — {@code execute} / {@code applyCanonicalPlanCompositionChange}
 *       crash on the singular lookup and would cancel an arbitrary plan.</li>
 *   <li>§7 — {@code effectivePlanVersionId} prefers an arbitrary ACTIVE PLAN
 *       item (or crashes) instead of the anchored item's pinned version.</li>
 *   <li>§9 — the generic item API cancels the compatibility-anchored PLAN,
 *       mutates PLAN quantity, and reports duplicate same-plan adds only as a
 *       raw constraint violation.</li>
 *   <li>§8/§17/§19 — the legacy IMMEDIATE change and the renewal pending
 *       application (which route through the canonical authority) inherit the
 *       same crash; SECONDARY plans must be preserved by a plan change.</li>
 * </ul>
 */
class MultiPlanAnchorAuthorityPostgresTest {

    private JdbcTemplate jdbc;
    private SubscriptionChangeService changeService;
    private SubscriptionItemService itemService;
    private SaasAdministrationService legacy;
    private List<Object> publishedEvents;

    private UUID tenant;
    private UUID otherTenant;
    private UUID legacyTenant;
    private UUID planA;
    private UUID planB;
    private UUID planX;
    private UUID versionA;
    private UUID versionB;
    private UUID versionX;
    private UUID subscription;

    private static final Instant NOW = Instant.now();

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "MultiPlanAnchorAuthorityPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping MultiPlanAnchorAuthorityPostgresTest.");
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

        SubscriptionItemRepository itemRepository = new SubscriptionItemRepository(jdbc);
        changeService = new SubscriptionChangeService(jdbc, itemRepository,
                new PriceResolver(new PriceRepository(jdbc)));
        itemService = new SubscriptionItemService(jdbc, itemRepository,
                new PlanVersionRepository(jdbc));
        publishedEvents = new ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, Mockito.mock(PlatformAuditService.class),
                publishedEvents::add, null);

        tenant = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        legacyTenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        planB = UUID.randomUUID();
        planX = UUID.randomUUID();
        versionA = UUID.randomUUID();
        versionB = UUID.randomUUID();
        versionX = UUID.randomUUID();
        subscription = UUID.randomUUID();

        seedTenant(tenant, "SA");
        seedTenant(otherTenant, "SA");
        seedTenant(legacyTenant, "SA");
        seedPlan(planA, "R0C5-A", 30000L);
        seedPlan(planB, "R0C5-B", 90000L);
        seedPlan(planX, "R0C5-X", 50000L);
        seedPlanVersion(versionA, planA, 1, 30000L);
        seedPlanVersion(versionB, planB, 2, 90000L);
        seedPlanVersion(versionX, planX, 1, 50000L);
        seedPrice(versionB, "SA", 90000L);

        // The compatibility-anchored subscription: anchor = A / version A.
        seedSubscription(subscription, tenant, planA, versionA, "ACTIVE", 1);
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
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', ?, ?, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code, monthlyMinor, monthlyMinor * 10);
    }

    private void seedPlanVersion(UUID id, UUID planId, int number, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', NOW(), 'SAR', ?, ?, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, planId, number, monthlyMinor, monthlyMinor * 10);
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

    private void seedSubscription(UUID id, UUID tenantId, UUID planId, UUID versionId,
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
    }

    private UUID seedPlanItem(UUID subscriptionId, UUID tenantId, UUID planId, UUID versionId,
                              long unitAmountMinor, Instant createdAt) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        plan_id, plan_version_id, name_snapshot, quantity,
                                                        unit_amount_minor, currency_code, status,
                                                        created_at, updated_at)
                        VALUES (?, ?, ?, 'PLAN', ?, ?, ?, 1, ?, 'SAR', 'ACTIVE', ?, ?)
                        """,
                itemId, tenantId, subscriptionId, planId, versionId, "PLAN " + planId,
                unitAmountMinor, java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
        return itemId;
    }

    /** Seeds the canonical anchored pair; X is created EARLIER than A so any
     *  findFirst()/ordering picks X — proving anchored selection is required. */
    private record AnchorSeed(UUID anchoredItemId, UUID secondaryItemId) {
    }

    private AnchorSeed seedAnchorPlusSecondary() {
        UUID secondary = seedPlanItem(subscription, tenant, planX, versionX, 50000L, NOW.minusSeconds(7200));
        UUID anchored = seedPlanItem(subscription, tenant, planA, versionA, 30000L, NOW.minusSeconds(3600));
        return new AnchorSeed(anchored, secondary);
    }

    private long activePlanItemCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private long activeItemCountForPlan(UUID subscriptionId, UUID planId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE' AND plan_id = ?",
                Long.class, subscriptionId, planId);
        return count == null ? 0L : count;
    }

    private Map<String, Object> anchors(UUID subscriptionId) {
        return jdbc.queryForMap(
                "SELECT plan_id, plan_version_id FROM tenant_subscriptions WHERE id = ?",
                subscriptionId);
    }

    // ---------------------------------------------------------------
    // PG-01 / PG-02 — the multi-plan MODEL itself (DB contract)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-01: ERP+HRM model — distinct ACTIVE PLAN items coexist on one subscription")
    void pg01_distinctActivePlansCoexist() {
        seedAnchorPlusSecondary();

        assertThat(activePlanItemCount(subscription)).isEqualTo(2L);
        assertThat(activeItemCountForPlan(subscription, planA)).isEqualTo(1L);
        assertThat(activeItemCountForPlan(subscription, planX)).isEqualTo(1L);
    }

    @Test
    @DisplayName("PG-02: a second ACTIVE item of the SAME plan is rejected by the unique index")
    void pg02_duplicateActiveSamePlanRejectedByUniqueIndex() {
        seedPlanItem(subscription, tenant, planA, versionA, 30000L, NOW.minusSeconds(3600));

        // Same subscription + same plan + ACTIVE -> must violate
        // uk_subscription_items_active_plan (a DIFFERENT plan is fine; the same
        // plan twice is not).
        assertThatThrownBy(() ->
                seedPlanItem(subscription, tenant, planA, versionA, 30000L, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PG-02B: duplicate same-plan add through the SERVICE API is rejected fail-closed")
    void pg02b_duplicateSamePlanAddViaServiceRejected() {
        seedPlanItem(subscription, tenant, planA, versionA, 30000L, NOW.minusSeconds(3600));

        // The generic item API must reject an ACTIVE duplicate of the SAME plan
        // with a defined IllegalStateException — not surface a raw constraint
        // violation.
        assertThatThrownBy(() -> itemService.addItem(subscription, "PLAN",
                null, null, planA, versionA, 1, 30000L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    // ---------------------------------------------------------------
    // §5 RED — the singular PLAN-type lookup breaks with two ACTIVE rows
    // ---------------------------------------------------------------

    @Test
    @DisplayName("§5 RED: singular PLAN-type lookup fails with two ACTIVE PLAN rows (SINGULAR_PLAN_TYPE_LOOKUP_RED)")
    void red05_singularPlanTypeLookupFailsWithTwoActiveRows() {
        seedAnchorPlusSecondary();
        SubscriptionItemRepository repository = new SubscriptionItemRepository(jdbc);

        // The domain allows N distinct ACTIVE PLAN items, so a lookup that
        // means "give me THE active PLAN" cannot exist. queryForObject with 2
        // rows throws IncorrectResultSizeDataAccessException — the defect.
        org.springframework.dao.IncorrectResultSizeDataAccessException thrown =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> repository.findActiveBySubscriptionIdAndType(subscription, "PLAN"),
                        org.springframework.dao.IncorrectResultSizeDataAccessException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("expected 1, actual 2");
    }

    // ---------------------------------------------------------------
    // PG-05 / PG-06 — change + preview engines must target the ANCHORED plan
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-05: preview prices the ANCHORED plan (A), not the older-created secondary (X)")
    void pg05_previewTargetsAnchoredPlanNotFirstSecondary() {
        seedAnchorPlusSecondary();

        SubscriptionChangeService.ChangePreview preview =
                changeService.preview(subscription, versionB, "SA", NOW);

        // currentMonthly must come from the anchored A item (30000), never
        // from the secondary X (50000) even though X was created earlier and
        // wins any findFirst() ordering.
        assertThat(preview.currentMonthlyMinor()).isEqualTo(30000L);
        assertThat(preview.currencyCode()).isEqualTo("SAR");
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    @DisplayName("PG-06: execute A->B cancels A, activates B, PRESERVES the secondary X, moves anchors")
    void pg06_executeChangePreservesSecondaryPlan() {
        seedAnchorPlusSecondary();

        SubscriptionChangeService.ChangeResult result =
                changeService.execute(subscription, versionB, "SA", "upgrade to B", tenant, null);

        assertThat(result.status()).isEqualTo("EXECUTED");
        // A cancelled, B active, X STILL ACTIVE — total 2 active PLAN items.
        assertThat(activeItemCountForPlan(subscription, planA)).isZero();
        assertThat(activeItemCountForPlan(subscription, planB)).isEqualTo(1L);
        assertThat(activeItemCountForPlan(subscription, planX)).isEqualTo(1L);
        assertThat(activePlanItemCount(subscription)).isEqualTo(2L);
        // Anchors follow the change.
        assertThat(anchors(subscription).get("plan_id")).isEqualTo(planB);
        assertThat(anchors(subscription).get("plan_version_id")).isEqualTo(versionB);
    }

    // ---------------------------------------------------------------
    // PG-04 — effectivePlanVersionId must be deterministic (anchored)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-04: effectivePlanVersionId returns the ANCHORED item's pinned version, not an arbitrary PLAN")
    void pg04_effectiveVersionIsAnchoredItemVersion() {
        seedAnchorPlusSecondary();

        Optional<UUID> effective = itemService.effectivePlanVersionId(subscription);

        // The anchor is planA -> its item pins versionA. The secondary X pins
        // versionX and must never win.
        assertThat(effective).contains(versionA);
    }

    // ---------------------------------------------------------------
    // PG-12 / PG-13 — item admin authority guards
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-12: cancelling the compatibility-ANCHORED plan item through the generic API is rejected")
    void pg12_anchoredPlanCancelViaGenericApiRejected() {
        UUID anchoredItemId = seedAnchorPlusSecondary().anchoredItemId();

        assertThatThrownBy(() -> itemService.cancelItem(anchoredItemId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anchor");

        // The anchored item must still be ACTIVE (fail-closed, no mutation).
        assertThat(jdbc.queryForObject(
                "SELECT status FROM subscription_items WHERE id = ?", String.class, anchoredItemId))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PG-13: PLAN item quantity (seats) mutation through the generic API is rejected")
    void pg13_planQuantityMutationRejected() {
        UUID anchoredItemId = seedAnchorPlusSecondary().anchoredItemId();

        // PLAN quantity mirrors the subscription seat count (billing reads
        // seat_quantity); generic mutation desyncs the billing mirror.
        assertThatThrownBy(() -> itemService.updateQuantity(anchoredItemId, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seat");
    }

    // ---------------------------------------------------------------
    // PG-10 / PG-11 — secondary PLAN administration stays ALLOWED
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-10: adding a SECONDARY distinct PLAN through the generic API is allowed; anchor unchanged")
    void pg10_secondaryPlanAddAllowedAnchorUnchanged() {
        seedPlanItem(subscription, tenant, planA, versionA, 30000L, NOW.minusSeconds(3600));

        SubscriptionItemEntity created = itemService.addItem(subscription, "PLAN",
                null, null, planX, versionX, 1, 50000L, "SAR");

        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(activeItemCountForPlan(subscription, planX)).isEqualTo(1L);
        // The compatibility anchor is untouched.
        assertThat(anchors(subscription).get("plan_id")).isEqualTo(planA);
        assertThat(anchors(subscription).get("plan_version_id")).isEqualTo(versionA);
    }

    @Test
    @DisplayName("PG-11: cancelling a SECONDARY plan item is allowed; anchor unchanged")
    void pg11_secondaryPlanCancelAllowedAnchorUnchanged() {
        AnchorSeed seed = seedAnchorPlusSecondary();

        itemService.cancelItem(seed.secondaryItemId());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM subscription_items WHERE id = ?", String.class, seed.secondaryItemId()))
                .isEqualTo("CANCELLED");
        // The anchored A item stays ACTIVE.
        assertThat(activeItemCountForPlan(subscription, planA)).isEqualTo(1L);
        assertThat(anchors(subscription).get("plan_id")).isEqualTo(planA);
    }

    // ---------------------------------------------------------------
    // PG-17 / PG-19 — legacy engine paths preserve secondary plans
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-17: legacy IMMEDIATE plan change converges the anchor and PRESERVES secondary plans")
    void pg17_immediateLegacyChangePreservesSecondaryPlans() {
        legacy.createSubscription(new CreateSubscriptionRequest(legacyTenant, planA, "MONTHLY", 1, 0), null);
        UUID createdId = jdbc.queryForObject(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ?", UUID.class, legacyTenant);
        itemService.addItem(createdId, "PLAN", null, null, planX, versionX, 1, 50000L, "SAR");

        legacy.changePlan(createdId, new ChangeSubscriptionPlanRequest(planB, "MONTHLY", "IMMEDIATE", "r0c5"), null);

        // Anchor converged to B; A cancelled; X preserved; exactly one B.
        Map<String, Object> anchorRow = anchors(createdId);
        assertThat(anchorRow.get("plan_id")).isEqualTo(planB);
        assertThat(anchorRow.get("plan_version_id")).isEqualTo(versionB);
        assertThat(activeItemCountForPlan(createdId, planA)).isZero();
        assertThat(activeItemCountForPlan(createdId, planB)).isEqualTo(1L);
        assertThat(activeItemCountForPlan(createdId, planX)).isEqualTo(1L);
    }

    @Test
    @DisplayName("PG-19: renewal applying a pending plan change PRESERVES secondary plans")
    void pg19_renewalAppliesPendingAndPreservesSecondaryPlans() {
        legacy.createSubscription(new CreateSubscriptionRequest(legacyTenant, planA, "MONTHLY", 1, 0), null);
        UUID createdId = jdbc.queryForObject(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ?", UUID.class, legacyTenant);
        itemService.addItem(createdId, "PLAN", null, null, planX, versionX, 1, 50000L, "SAR");

        // Schedule the change; renewal applies it through the canonical path.
        legacy.changePlan(createdId, new ChangeSubscriptionPlanRequest(planB, "MONTHLY", "NEXT_CYCLE", "r0c5"), null);
        legacy.renewSubscription(createdId, null);

        Map<String, Object> anchorRow = anchors(createdId);
        assertThat(anchorRow.get("plan_id")).isEqualTo(planB);
        assertThat(anchorRow.get("plan_version_id")).isEqualTo(versionB);
        assertThat(anchorRow.get("pending_plan_id")).isNull();
        assertThat(activeItemCountForPlan(createdId, planA)).isZero();
        assertThat(activeItemCountForPlan(createdId, planB)).isEqualTo(1L);
        assertThat(activeItemCountForPlan(createdId, planX)).isEqualTo(1L);
    }
}
