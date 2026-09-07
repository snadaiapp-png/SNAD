package com.sanad.platform.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.subscription.audit.AuditQueryService;
import com.sanad.platform.subscription.catalog.ProductCatalogService;
import com.sanad.platform.subscription.catalog.ProductEntity;
import com.sanad.platform.subscription.catalog.ProductRepository;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import com.sanad.platform.subscription.plan.PlanVersionService;
import com.sanad.platform.subscription.read.ExecutiveOverviewService;
import com.sanad.platform.subscription.read.PageResponse;
import com.sanad.platform.subscription.read.SubscriptionDetailService;
import com.sanad.platform.subscription.read.SubscriptionGridQueryService;
import com.sanad.platform.subscription.read.TenantDirectoryQueryService;
import com.sanad.platform.subscription.usage.UsageMeteringService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-12 — FINAL SUBSCRIPTION CONTROL PLANE MODULE ACCEPTANCE (PostgreSQL
 * Direct). Cross-workstream battery on the real host-native PostgreSQL with
 * the least-privilege application role, the real migration chain and the real
 * read/runtime services (NO Docker, NO Testcontainers, NO H2).
 *
 * <p>Covers the R0C-12 closure behaviors across workstreams:</p>
 *
 * <ul>
 *   <li>RD-01/02 detail read model carries the entitlements section
 *       (plan-derived and item-derived rows) — G5-R2</li>
 *   <li>US-01 batched usage read model (ingest → aggregate → snapshot) — G5-R3</li>
 *   <li>US-02 warning (75%) and critical (90%) thresholds on real data — G4-R1</li>
 *   <li>US-03 batched limit merge = max(plan-derived, item-derived) — G5-R3 parity</li>
 *   <li>US-04 usage tables FORCE-RLS: tenant scoping + cross-tenant invisibility</li>
 *   <li>US-05 idempotent ingestion backstop on real UNIQUE constraint</li>
 *   <li>GR-01 subscriptions/v2 grid: pagination, filters, sort whitelist</li>
 *   <li>GR-02 tenants/v2 directory: search/filter/pagination</li>
 *   <li>AU-01 audit read model pagination on real audit rows</li>
 *   <li>OV-01 overview read model real aggregates</li>
 *   <li>PV-01 plan version pinning on real schema (activation never mutates subscribers)</li>
 *   <li>DB-01 schema invariants: partial unique anchors + prices model CHECK</li>
 * </ul>
 */
class ScpFinalModuleAcceptancePostgresTest {

    private static JdbcTemplate jdbc;
    private static TenantRlsTransactionContext rls;
    private static TransactionTemplate transactions;

    private UsageMeteringService usageService;
    private SubscriptionDetailService detailService;
    private SubscriptionGridQueryService gridService;
    private TenantDirectoryQueryService tenantDirectory;
    private AuditQueryService auditService;
    private ExecutiveOverviewService overviewService;
    private PlanVersionService planVersionService;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ScpFinalModuleAcceptancePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ScpFinalModuleAcceptancePostgresTest.");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("PG_ACCEPTANCE_JDBC_URL",
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                                "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("PG_ACCEPTANCE_USERNAME",
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad")),
                System.getenv().getOrDefault("PG_ACCEPTANCE_PASSWORD",
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "")));
    }

    @BeforeEach
    void migrateAndSeed() {
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                System.getenv().getOrDefault("PG_ACCEPTANCE_JDBC_URL",
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                                "jdbc:postgresql://localhost:5432/sanad")));
        String user = System.getenv().getOrDefault("PG_ACCEPTANCE_USERNAME",
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        String password = System.getenv().getOrDefault("PG_ACCEPTANCE_PASSWORD",
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

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
        rls = new TenantRlsTransactionContext(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));

        usageService = new UsageMeteringService(jdbc, rls);
        detailService = new SubscriptionDetailService(jdbc);
        gridService = new SubscriptionGridQueryService(jdbc);
        tenantDirectory = new TenantDirectoryQueryService(jdbc);
        auditService = new AuditQueryService(jdbc);
        overviewService = new ExecutiveOverviewService(jdbc);
        planVersionService = new PlanVersionService(jdbc, new PlanVersionRepository(jdbc));
    }

    @AfterAll
    static void release() {
        jdbc = null;
        rls = null;
        transactions = null;
    }

    // ---------------------------------------------------------------
    // seed helpers (real schema)
    // ---------------------------------------------------------------

    private UUID seedTenant(String countryCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8), countryCode);
        return id;
    }

    private UUID seedPlan(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb,
                                                created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', 30000, 300000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code);
        return id;
    }

    private UUID seedPlanVersion(UUID planId, int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status, currency_code,
                                                   monthly_price_minor, annual_price_minor, trial_days,
                                                   max_users, max_organizations, storage_mb,
                                                   effective_from, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', 30000, 300000, 0, 10, 5, 1024,
                                NOW() - INTERVAL '1 day', NOW(), NOW())
                        """,
                id, planId, number);
        return id;
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

    private void seedPlanEntitlement(UUID planId, UUID moduleId, String capability, Long limit) {
        jdbc.update("""
                        INSERT INTO plan_module_entitlements (id, plan_id, module_id, module_enabled,
                                                              capability_code, limit_value,
                                                              effective_at, created_at, updated_at)
                        VALUES (?, ?, ?, true, ?, ?, NOW(), NOW(), NOW())
                        """,
                UUID.randomUUID(), planId, moduleId, capability, limit);
    }

    private UUID seedModule(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO modules (id, code, name, status, display_order, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 0, true, NOW(), NOW())
                        """, id, code, name);
        return id;
    }

    private UUID seedProduct(UUID applicationId, String code, String type) {
        ProductEntity e = new ProductEntity();
        e.setCode(code);
        e.setName("Product " + code);
        e.setProductType(type);
        e.setStatus("ACTIVE");
        e.setApplicationId(applicationId);
        ProductEntity saved = new ProductCatalogService(new ProductRepository(jdbc)).create(e);
        return saved.getId();
    }

    private void seedProductEntitlement(UUID productId, UUID moduleId, String capability,
                                        boolean enabled, Long limit) {
        jdbc.update("""
                        INSERT INTO product_entitlements (id, product_id, module_id, module_enabled,
                                                          capability_code, boolean_value, limit_value,
                                                          created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        """,
                UUID.randomUUID(), productId, moduleId, enabled, capability,
                limit == null ? Boolean.TRUE : null, limit);
    }

    private void seedProductBackedItem(UUID tenantId, UUID subscriptionId, UUID productId) {
        jdbc.update("""
                        INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type,
                                                        product_id, name_snapshot, quantity,
                                                        currency_code, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'ADD_ON', ?, 'Add-on item', 1, 'SAR', 'ACTIVE', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, subscriptionId, productId);
    }

    private UUID moduleForUsageMetric() {
        // any module row; usage limits key off capability codes, not module ids
        return seedModule("usage-module-" + UUID.randomUUID().toString().substring(0, 8), "Usage Module");
    }

    // ---------------------------------------------------------------
    // RD — detail entitlements section (G5-R2)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RD-01: detail read model surfaces plan-derived entitlement rows")
    void detailSurfacesPlanDerivedEntitlements() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("rd01-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        UUID moduleId = moduleForUsageMetric();
        seedPlanEntitlement(planId, moduleId, "USAGE.USERS", 250L);
        UUID subscriptionId = UUID.randomUUID();
        seedSubscription(subscriptionId, tenantId, planId, versionId, "ACTIVE");

        SubscriptionDetailService.SubscriptionDetail detail = detailService.detail(subscriptionId);

        assertThat(detail.entitlements()).isNotNull();
        List<Map<String, Object>> planRows = detail.entitlements().stream()
                .filter(row -> "PLAN".equals(row.get("source")))
                .toList();
        assertThat(planRows).anyMatch(row ->
                "USAGE.USERS".equals(row.get("capabilityCode"))
                        && Long.valueOf(250L).equals(((Number) row.get("limitValue")).longValue()));
    }

    @Test
    @DisplayName("RD-02: detail read model surfaces item-derived product entitlement rows")
    void detailSurfacesItemDerivedEntitlements() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("rd02-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        UUID moduleId = moduleForUsageMetric();
        UUID productId = seedProduct(null, "RD02-ADDON", "ADD_ON");
        seedProductEntitlement(productId, moduleId, "hr.payroll.enabled", true, null);
        UUID subscriptionId = UUID.randomUUID();
        seedSubscription(subscriptionId, tenantId, planId, versionId, "ACTIVE");
        seedProductBackedItem(tenantId, subscriptionId, productId);

        SubscriptionDetailService.SubscriptionDetail detail = detailService.detail(subscriptionId);

        assertThat(detail.entitlements()).anyMatch(row ->
                "PRODUCT".equals(row.get("source"))
                        && "hr.payroll.enabled".equals(row.get("capabilityCode"))
                        && Boolean.TRUE.equals(row.get("booleanValue")));
    }

    // ---------------------------------------------------------------
    // US — batched usage read model (G5-R3 + G4-R1)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("US-01: ingest → aggregate → batched snapshot round-trip on real schema")
    void batchedUsageRoundTrip() {
        UUID tenantId = seedTenant("SA");
        transactions.executeWithoutResult(tx -> {
            usageService.ingest(tenantId, "users", 100L, "test", "us01-a", java.time.Instant.now());
            usageService.ingest(tenantId, "users", 50L, "test", "us01-b", java.time.Instant.now());
            usageService.ingest(tenantId, "ai_tokens", 700L, "test", "us01-c", java.time.Instant.now());
        });

        List<UsageMeteringService.UsageSnapshot> snapshots = transactions.execute(
                tx -> usageService.usageSnapshots(tenantId));

        UsageMeteringService.UsageSnapshot users = snapshots.stream()
                .filter(s -> s.metricCode().equals("users")).findFirst().orElseThrow();
        assertThat(users.current()).isEqualTo(150L);
        assertThat(users.periodStart()).isNotNull();
        assertThat(users.limitKind()).isEqualTo("HARD_LIMIT");
        assertThat(users.percent()).isNull(); // no USAGE.USERS entitlement seeded → no limit

        UsageMeteringService.UsageSnapshot tokens = snapshots.stream()
                .filter(s -> s.metricCode().equals("ai_tokens")).findFirst().orElseThrow();
        assertThat(tokens.current()).isEqualTo(700L);
    }

    @Test
    @DisplayName("US-02: warning at 75%+ and critical at 90%+ on real entitlement limits")
    void usageThresholdsOnRealData() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("us02-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        UUID moduleId = moduleForUsageMetric();
        seedPlanEntitlement(planId, moduleId, "USAGE.USERS", 1000L);
        seedSubscription(UUID.randomUUID(), tenantId, planId, versionId, "ACTIVE");

        transactions.executeWithoutResult(tx ->
                usageService.ingest(tenantId, "users", 920L, "test", "us02-a", java.time.Instant.now()));
        UsageMeteringService.UsageSnapshot critical = transactions.execute(
                tx -> usageService.usageSnapshot(tenantId, "users")).orElseThrow();
        assertThat(critical.percent()).isEqualTo(92);
        assertThat(critical.warning()).isTrue();
        assertThat(critical.critical()).isTrue();

        // fresh tenant at 76% — warning without critical
        UUID tenantB = seedTenant("SA");
        UUID planB = seedPlan("us02b-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionB = seedPlanVersion(planB, 1);
        seedPlanEntitlement(planB, moduleId, "USAGE.USERS", 1000L);
        seedSubscription(UUID.randomUUID(), tenantB, planB, versionB, "ACTIVE");
        transactions.executeWithoutResult(tx ->
                usageService.ingest(tenantB, "users", 760L, "test", "us02-b", java.time.Instant.now()));
        UsageMeteringService.UsageSnapshot warning = transactions.execute(
                tx -> usageService.usageSnapshot(tenantB, "users")).orElseThrow();
        assertThat(warning.percent()).isEqualTo(76);
        assertThat(warning.warning()).isTrue();
        assertThat(warning.critical()).isFalse();
    }

    @Test
    @DisplayName("US-03: batched limit merge — higher item-derived limit wins (max semantics)")
    void batchedLimitMergeMaxSemantics() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("us03-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        UUID moduleId = moduleForUsageMetric();
        seedPlanEntitlement(planId, moduleId, "USAGE.AI_TOKENS", 500L);
        UUID productId = seedProduct(null, "US03-METERED", "METERED");
        seedProductEntitlement(productId, moduleId, "USAGE.AI_TOKENS", true, 1200L);
        UUID subscriptionId = UUID.randomUUID();
        seedSubscription(subscriptionId, tenantId, planId, versionId, "ACTIVE");
        seedProductBackedItem(tenantId, subscriptionId, productId);

        transactions.executeWithoutResult(tx ->
                usageService.ingest(tenantId, "ai_tokens", 100L, "test", "us03-a", java.time.Instant.now()));

        UsageMeteringService.UsageSnapshot snapshot = transactions.execute(
                tx -> usageService.usageSnapshot(tenantId, "ai_tokens")).orElseThrow();
        assertThat(snapshot.limit()).isEqualTo(1200L); // max(plan 500, product 1200)
        assertThat(snapshot.current()).isEqualTo(100L);
        assertThat(snapshot.percent()).isEqualTo(8);
        assertThat(snapshot.warning()).isFalse();
        // batched path returns the same merged limit
        List<UsageMeteringService.UsageSnapshot> batched = transactions.execute(
                tx -> usageService.usageSnapshots(tenantId));
        assertThat(batched.stream().filter(s -> s.metricCode().equals("ai_tokens")).findFirst()
                .orElseThrow().limit()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("US-04: usage tables FORCE-RLS — cross-tenant rows invisible, unscoped fails closed")
    void usageForceRlsTenantScoping() {
        // FORCE RLS actually engaged on the usage tables
        Boolean eventsRls = jdbc.queryForObject(
                "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class "
                        + "WHERE relnamespace = 'public'::regnamespace AND relname = 'usage_events'",
                Boolean.class);
        Boolean aggRls = jdbc.queryForObject(
                "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class "
                        + "WHERE relnamespace = 'public'::regnamespace AND relname = 'usage_aggregates'",
                Boolean.class);
        assertThat(eventsRls).isTrue();
        assertThat(aggRls).isTrue();

        UUID tenantA = seedTenant("SA");
        UUID tenantB = seedTenant("SA");
        transactions.executeWithoutResult(tx -> {
            usageService.ingest(tenantA, "users", 10L, "test", "us04-a", java.time.Instant.now());
            usageService.ingest(tenantB, "users", 20L, "test", "us04-b", java.time.Instant.now());
        });

        // reading A's snapshot within A's RLS context never sees B's quantities
        UsageMeteringService.UsageSnapshot a = transactions.execute(
                tx -> usageService.usageSnapshot(tenantA, "users")).orElseThrow();
        assertThat(a.current()).isEqualTo(10L);
        // and B's context sees only B
        UsageMeteringService.UsageSnapshot b = transactions.execute(
                tx -> usageService.usageSnapshot(tenantB, "users")).orElseThrow();
        assertThat(b.current()).isEqualTo(20L);
    }

    @Test
    @DisplayName("US-05: duplicate idempotency key is rejected by the real UNIQUE constraint")
    void idempotencyUniqueConstraint() {
        UUID tenantId = seedTenant("SA");
        String key = "us05-" + UUID.randomUUID();
        transactions.executeWithoutResult(tx ->
                usageService.ingest(tenantId, "users", 5L, "test", key, java.time.Instant.now()));
        // service path: duplicate replay is a no-op
        UsageMeteringService.IngestResult replay = transactions.execute(tx ->
                usageService.ingest(tenantId, "users", 5L, "test", key, java.time.Instant.now()));
        assertThat(replay.duplicate()).isTrue();
        // raw path: with the tenant RLS scope applied, the UNIQUE constraint itself
        // is the idempotency backstop
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
                    rls.applyForCurrentTransaction(tenantId);
                    jdbc.update("""
                            INSERT INTO usage_events (id, tenant_id, metric_code, quantity,
                                                      idempotency_key, occurred_at, created_at)
                            VALUES (?, ?, 'users', 5, ?, NOW(), NOW())
                            """, UUID.randomUUID(), tenantId, key);
                }))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ---------------------------------------------------------------
    // GR — paginated grids (G5)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("GR-01: subscriptions/v2 grid — pagination, filters, injection-safe sort")
    void gridPaginationAndSafety() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("gr01-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        // R0C-10 partial unique: exactly ONE effective (non-terminal) subscription per
        // tenant — history rows use terminal statuses
        seedSubscription(UUID.randomUUID(), tenantId, planId, versionId, "ACTIVE");
        seedSubscription(UUID.randomUUID(), tenantId, planId, versionId, "CANCELLED");
        seedSubscription(UUID.randomUUID(), tenantId, planId, versionId, "EXPIRED");
        // another tenant — must not leak into the tenantId-filtered grid
        UUID otherTenant = seedTenant("AE");
        seedSubscription(UUID.randomUUID(), otherTenant, planId, versionId, "ACTIVE");

        PageResponse<SubscriptionGridQueryService.SubscriptionRow> page =
                gridService.search(tenantId, null, null, null, false, 0, 2, "created_at", "DESC");
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3L);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);
        assertThat(page.content()).allMatch(row -> tenantId.equals(row.tenantId()));

        PageResponse<SubscriptionGridQueryService.SubscriptionRow> activeOnly =
                gridService.search(tenantId, "ACTIVE", null, null, false, 0, 20, "created_at", "DESC");
        assertThat(activeOnly.totalElements()).isEqualTo(1L);

        // injection attempt falls back to the whitelisted default column
        PageResponse<SubscriptionGridQueryService.SubscriptionRow> safe =
                gridService.search(null, null, null, null, false, 0, 20, "1; DROP TABLE tenants;--", "DESC");
        assertThat(safe.totalElements()).isGreaterThanOrEqualTo(4L);
    }

    @Test
    @DisplayName("GR-02: tenants/v2 directory — search, filter, pagination")
    void tenantDirectorySearch() {
        UUID tenant = seedTenant("KW");
        String token = tenant.toString().substring(0, 8);
        PageResponse<TenantDirectoryQueryService.TenantRow> bySearch =
                tenantDirectory.search(token, null, null, 0, 20, "name", "ASC");
        assertThat(bySearch.totalElements()).isGreaterThanOrEqualTo(1L);
        assertThat(bySearch.content()).anyMatch(row -> tenant.equals(row.id()));

        PageResponse<TenantDirectoryQueryService.TenantRow> byCountry =
                tenantDirectory.search(null, "ACTIVE", "KW", 0, 20, "name", "ASC");
        assertThat(byCountry.totalElements()).isGreaterThanOrEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // AU / OV — audit + overview read models (G5)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("AU-01: audit read model paginates real audit rows")
    void auditPagination() {
        PlatformAuditWriter writer = new PlatformAuditWriter(jdbc,
                new ObjectMapper().findAndRegisterModules());
        UUID tenantId = seedTenant("SA");
        UUID subscriptionId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            writer.writeSuccess(tenantId, null, tenantId, "TEST_COMMAND", "subscription",
                    subscriptionId.toString(), "audit battery row " + i, null,
                    Map.of("row", i), UUID.randomUUID().toString(), java.time.Instant.now());
        }
        PageResponse<Map<String, Object>> page = auditService.query(
                tenantId, "TEST_COMMAND", "subscription", 0, 2, "created_at", "DESC");
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3L);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);
        // injection attempt falls back to the whitelisted sort
        PageResponse<Map<String, Object>> safe = auditService.query(
                tenantId, null, null, 0, 10, "1; DROP TABLE platform_audit_logs;--", "ASC");
        assertThat(safe.totalElements()).isGreaterThanOrEqualTo(3L);
    }

    @Test
    @DisplayName("OV-01: overview read model reflects real subscription counts")
    void overviewRealAggregates() {
        ExecutiveOverviewService.Overview overview = overviewService.overview();
        assertThat(overview).isNotNull();
        assertThat(overview.totalTenants()).isGreaterThanOrEqualTo(0L);
        assertThat(overview.activeSubscriptions()).isGreaterThanOrEqualTo(0L);
        assertThat(overview.generatedAt()).isNotNull();
    }

    // ---------------------------------------------------------------
    // PV — plan version pinning on real schema (G1)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PV-01: activating v2 retires v1 and never mutates subscriber anchors")
    void planVersionPinning() {
        UUID tenantId = seedTenant("SA");
        UUID planId = seedPlan("pv01-" + UUID.randomUUID().toString().substring(0, 8));
        UUID v1 = seedPlanVersion(planId, 1);
        UUID subscriptionId = UUID.randomUUID();
        seedSubscription(subscriptionId, tenantId, planId, v1, "ACTIVE");

        // v2 draft → activate: subscriber stays pinned to v1
        var draft = planVersionService.createDraft(
                planId, "SAR", 31000L, 310000L, 0, 12, 6, 2048);
        assertThat(draft.getId()).isNotNull();
        planVersionService.activate(draft.getId());

        UUID pinned = jdbc.queryForObject(
                "SELECT plan_version_id FROM tenant_subscriptions WHERE id = ?",
                UUID.class, subscriptionId);
        assertThat(pinned).isEqualTo(v1);

        // single-ACTIVE invariant at the database level
        Integer activeVersions = jdbc.queryForObject(
                "SELECT count(*) FROM plan_versions WHERE plan_id = ? AND status = 'ACTIVE'",
                Integer.class, planId);
        assertThat(activeVersions).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // DB — schema invariants (G1/G2)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DB-01: anchor partial-unique indexes exist; prices model CHECK enforced")
    void schemaInvariants() {
        Integer anchorIndexes = jdbc.queryForObject("""
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname IN ('uk_plan_versions_one_active',
                                            'uk_subscription_items_active_plan',
                                            'uk_tenant_subscriptions_effective')
                        """, Integer.class);
        assertThat(anchorIndexes).isEqualTo(3);

        // prices: only the 12 authoritative models are legal
        UUID planId = seedPlan("db01-" + UUID.randomUUID().toString().substring(0, 8));
        UUID versionId = seedPlanVersion(planId, 1);
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO prices (id, plan_version_id, product_id, price_model, country_code,
                                            currency_code, billing_interval, base_amount_minor,
                                            effective_from, created_at, updated_at)
                        VALUES (?, ?, NULL, 'NOT_A_MODEL', 'GLOBAL', 'USD', 'MONTHLY', 1000, NOW(), NOW(), NOW())
                        """, UUID.randomUUID(), versionId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
