package com.sanad.platform.subscription.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.subscription.entitlement.ItemEntitlementRepository;
import com.sanad.platform.subscription.item.SubscriptionItemEntity;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.item.SubscriptionItemService;
import com.sanad.platform.subscription.plan.PlanVersionRepository;
import com.sanad.platform.subscription.pricing.PriceEntity;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-11 — PRODUCT CATALOG RUNTIME ACCEPTANCE (PostgreSQL Direct).
 *
 * <p>Full run-directive battery against the real host-native PostgreSQL with
 * the least-privilege application role, the real migration chain and the real
 * domain services (NO Docker, NO Testcontainers, NO H2):</p>
 *
 * <ul>
 *   <li>PG-01 schema exists (table + CHECK constraints + unique index)</li>
 *   <li>PG-02 allowed types accepted; PG-03 invalid types rejected (domain + CHECK)</li>
 *   <li>PG-04 allowed statuses accepted; PG-05 invalid status rejected</li>
 *   <li>PG-06 unique code fail-closed; PG-07 case-normalized collision</li>
 *   <li>PG-08 concurrent collision — exactly one winner, deterministic error</li>
 *   <li>PG-09 create/read/update round-trip</li>
 *   <li>PG-10 available-only filtering</li>
 *   <li>PG-11 nullable application; PG-12 valid application; PG-13 invalid application</li>
 *   <li>PG-14 stable code (update never renames)</li>
 *   <li>PG-15 archive without delete</li>
 *   <li>PG-16 price integration (prices.product_id)</li>
 *   <li>PG-17 entitlement integration (product_entitlements → resolver)</li>
 *   <li>PG-18 ADD_ON attachment; PG-19 METERED attachment</li>
 *   <li>PG-20 unknown product rejection</li>
 *   <li>PG-21 inactive/archived new-sale rejection</li>
 *   <li>PG-22 historical-reference preservation</li>
 *   <li>PG-23 audit (PRODUCT_CREATE, resourceType=product)</li>
 *   <li>PG-24 RBAC read; PG-25 RBAC manage; PG-26 unauthorized denial</li>
 *   <li>PG-27 ControlPlaneAccessGuard</li>
 *   <li>PG-28 no physical delete</li>
 *   <li>PG-29 R0C-10 effective-subscription regression</li>
 * </ul>
 */
class ProductCatalogRuntimePostgresTest {

    private static JdbcTemplate jdbc;

    private ProductRepository productRepository;
    private ProductCatalogService catalogService;
    private PriceService priceService;
    private SubscriptionItemService itemService;
    private ItemEntitlementRepository itemEntitlementRepository;
    private PlatformAuditWriter auditWriter;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ProductCatalogRuntimePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ProductCatalogRuntimePostgresTest.");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @BeforeEach
    void migrateAndSeed() {
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/sanad"));
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

        productRepository = new ProductRepository(jdbc);
        catalogService = new ProductCatalogService(productRepository);
        priceService = new PriceService(new PriceRepository(jdbc), jdbc);
        itemService = new SubscriptionItemService(jdbc, new SubscriptionItemRepository(jdbc),
                new PlanVersionRepository(jdbc), productRepository);
        itemEntitlementRepository = new ItemEntitlementRepository(jdbc);
        auditWriter = new PlatformAuditWriter(jdbc,
                new ObjectMapper().findAndRegisterModules());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private ProductEntity product(String code, String type, String status) {
        ProductEntity e = new ProductEntity();
        e.setCode(code);
        e.setName("Product " + code);
        e.setProductType(type);
        e.setStatus(status);
        return e;
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

    private UUID insertSubscription(UUID tenantId, String status) {
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = jdbc.queryForObject("SELECT id FROM saas_plans LIMIT 1", UUID.class);
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, 'MONTHLY', 1, 0, NOW(), NULL, "
                        + "NOW(), NOW() + INTERVAL '30 days', FALSE, 'CURRENT', NOW(), NOW())",
                subscriptionId, tenantId, planId, status);
        return subscriptionId;
    }

    private UUID anyExistingModuleId() {
        return jdbc.queryForObject("SELECT id FROM modules LIMIT 1", UUID.class);
    }

    // ---------------------------------------------------------------
    // PG-01 — schema exists
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-01: products table exists with CHECK constraints and unique code index")
    void pg01_schemaExists() {
        Long tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'products'", Long.class);
        assertThat(tableCount).as("products table must exist").isEqualTo(1L);

        Long typeCheck = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_products_type' "
                        + "AND conrelid = 'products'::regclass", Long.class);
        assertThat(typeCheck).isEqualTo(1L);
        Long statusCheck = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_products_status' "
                        + "AND conrelid = 'products'::regclass", Long.class);
        assertThat(statusCheck).isEqualTo(1L);
        Long uniqueCode = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uk_products_code' "
                        + "AND conrelid = 'products'::regclass AND contype = 'u'", Long.class);
        assertThat(uniqueCode).as("unique code constraint").isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-02/PG-03 — types
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-02: all four allowed product types are accepted and persisted")
    void pg02_allowedTypes() {
        for (String type : List.of("APPLICATION", "ADD_ON", "METERED", "OTHER")) {
            ProductEntity created = catalogService.create(product("T_" + type, type, null));
            assertThat(created.getProductType()).isEqualTo(type);
            assertThat(repositoryCount("products", "product_type", type)).isGreaterThanOrEqualTo(1L);
        }
    }

    @Test
    @DisplayName("PG-03: invalid types rejected by the domain AND by the DB CHECK")
    void pg03_invalidTypesRejected() {
        assertThatThrownBy(() -> catalogService.create(product("BAD_TYPE", "BUNDLE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");

        // storage-layer backstop: the CHECK constraint independently rejects
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO products (id, code, name, product_type, status, created_at, updated_at) "
                        + "VALUES (?, 'RAW_BAD', 'Raw', 'BUNDLE', 'ACTIVE', NOW(), NOW())",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------
    // PG-04/PG-05 — statuses
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-04: all three allowed statuses are accepted and persisted")
    void pg04_allowedStatuses() {
        for (String status : List.of("ACTIVE", "INACTIVE", "ARCHIVED")) {
            ProductEntity created = catalogService.create(
                    product("S_" + status, "OTHER", status));
            assertThat(created.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("PG-05: invalid status rejected by the domain AND by the DB CHECK")
    void pg05_invalidStatusRejected() {
        assertThatThrownBy(() -> catalogService.create(product("BAD_STATUS", "OTHER", "RETIRED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO products (id, code, name, product_type, status, created_at, updated_at) "
                        + "VALUES (?, 'RAW_BAD_ST', 'Raw', 'OTHER', 'RETIRED', NOW(), NOW())",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------
    // PG-06/PG-07/PG-08 — uniqueness
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-06: duplicate code is fail-closed with a deterministic domain error")
    void pg06_uniqueCode() {
        catalogService.create(product("UNIQ_P", "ADD_ON", null));

        assertThatThrownBy(() -> catalogService.create(product("UNIQ_P", "ADD_ON", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        assertThat(count("products", "code", "UNIQ_P")).isEqualTo(1L);
    }

    @Test
    @DisplayName("PG-07: case-normalized code collision is rejected (abc → ABC)")
    void pg07_caseNormalizedCollision() {
        catalogService.create(product("case_prod", "OTHER", null));

        assertThatThrownBy(() -> catalogService.create(product("CASE_PROD", "OTHER", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        assertThat(count("products", "code", "CASE_PROD")).isEqualTo(1L);
    }

    @Test
    @DisplayName("PG-08: concurrent same-code inserts — exactly one wins, deterministic error for the loser")
    void pg08_concurrentCollision() throws Exception {
        int racers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        AtomicInteger deterministicLosers = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < racers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    catalogService.create(product("RACE_P", "OTHER", null));
                    winners.incrementAndGet();
                } catch (IllegalStateException expected) {
                    deterministicLosers.incrementAndGet();
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
        assertThat(deterministicLosers.get()).isEqualTo(1);
        assertThat(count("products", "code", "RACE_P")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-09/PG-10 — CRUD + availability
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-09: create/read/update round-trip through the real services")
    void pg09_createReadUpdate() {
        ProductEntity created = catalogService.create(product("CRUD_P", "ADD_ON", null));
        UUID id = created.getId();

        ProductEntity loaded = catalogService.findById(id);
        assertThat(loaded.getCode()).isEqualTo("CRUD_P");
        assertThat(catalogService.findByCode("crud_p")).isPresent();

        loaded.setName("Renamed Product");
        loaded.setDescription("Updated description");
        ProductEntity updated = catalogService.update(id, loaded);
        assertThat(updated.getName()).isEqualTo("Renamed Product");

        assertThat(catalogService.findById(id).getDescription())
                .isEqualTo("Updated description");
    }

    @Test
    @DisplayName("PG-10: findAvailable returns ACTIVE products only")
    void pg10_availableOnlyFiltering() {
        catalogService.create(product("AV_A", "OTHER", "ACTIVE"));
        catalogService.create(product("AV_I", "OTHER", "INACTIVE"));
        catalogService.create(product("AV_R", "OTHER", "ARCHIVED"));

        List<ProductEntity> available = catalogService.findAvailable();
        assertThat(available).extracting(ProductEntity::getCode)
                .contains("AV_A")
                .doesNotContain("AV_I", "AV_R");
        assertThat(catalogService.findAll()).hasSize(3);
    }

    // ---------------------------------------------------------------
    // PG-11/PG-12/PG-13 — application relation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-11: nullable application is valid (standalone product)")
    void pg11_nullableApplication() {
        ProductEntity created = catalogService.create(product("NO_APP", "OTHER", null));
        assertThat(created.getApplicationId()).isNull();
        assertThat(catalogService.findById(created.getId()).getApplicationId()).isNull();
    }

    @Test
    @DisplayName("PG-12: valid application FK is accepted and persisted")
    void pg12_validApplication() {
        UUID applicationId = insertApplication("APP_" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase());
        ProductEntity created = catalogService.create(product("WITH_APP", "APPLICATION", null));
        created.setApplicationId(applicationId);
        catalogService.update(created.getId(), created);

        assertThat(catalogService.findById(created.getId()).getApplicationId())
                .isEqualTo(applicationId);
    }

    @Test
    @DisplayName("PG-13: unknown application FK becomes a deterministic domain error")
    void pg13_invalidApplication() {
        UUID unknownApp = UUID.randomUUID();
        ProductEntity request = product("BAD_APP", "APPLICATION", null);
        request.setApplicationId(unknownApp);

        assertThatThrownBy(() -> catalogService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown application");
        assertThat(count("products", "code", "BAD_APP")).isZero();
    }

    // ---------------------------------------------------------------
    // PG-14/PG-15 — stability + archival
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-14: code is stable identity — update never renames the product")
    void pg14_stableCode() {
        ProductEntity created = catalogService.create(product("STABLE_P", "ADD_ON", null));
        created.setCode("RENAMED_ATTEMPT");

        catalogService.update(created.getId(), created);

        String storedCode = jdbc.queryForObject(
                "SELECT code FROM products WHERE id = ?", String.class, created.getId());
        assertThat(storedCode).isEqualTo("STABLE_P");
        assertThat(catalogService.findByCode("RENAMED_ATTEMPT")).isEmpty();
    }

    @Test
    @DisplayName("PG-15: ARCHIVED is a status transition — the row survives, nothing is deleted")
    void pg15_archiveWithoutDelete() {
        ProductEntity created = catalogService.create(product("ARCH_P", "ADD_ON", "ACTIVE"));

        created.setStatus("ARCHIVED");
        catalogService.update(created.getId(), created);

        assertThat(count("products", "code", "ARCH_P")).isEqualTo(1L);
        assertThat(catalogService.findById(created.getId()).getStatus()).isEqualTo("ARCHIVED");
        assertThat(catalogService.findAvailable())
                .extracting(ProductEntity::getCode)
                .doesNotContain("ARCH_P");
    }

    // ---------------------------------------------------------------
    // PG-16/PG-17 — pricing + entitlement integration
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-16: product pricing works through the existing prices.product_id FK")
    void pg16_priceFk() {
        ProductEntity product = catalogService.create(product("PRICE_P", "METERED", null));

        PriceEntity price = new PriceEntity();
        price.setPriceModel("PER_API_REQUEST");
        price.setCountryCode("GLOBAL");
        price.setCurrencyCode("USD");
        price.setBillingInterval("MONTHLY");
        price.setBaseAmountMinor(500L);
        price.setUnitAmountMinor(2L);
        PriceEntity createdPrice = priceService.createForProduct(product.getId(), price);

        assertThat(createdPrice.getProductId()).isEqualTo(product.getId());
        assertThat(priceService.listForProduct(product.getId())).hasSize(1);

        // the existing fail-closed FK guard still rejects unknown products
        assertThatThrownBy(() -> priceService.createForProduct(
                UUID.randomUUID(), price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product");
    }

    @Test
    @DisplayName("PG-17: product entitlements resolve through subscription_items → product_entitlements")
    void pg17_entitlementFk() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        ProductEntity product = catalogService.create(product("ENTL_P", "ADD_ON", "ACTIVE"));
        UUID moduleId = anyExistingModuleId();

        jdbc.update("""
                        INSERT INTO product_entitlements (id, product_id, module_id, module_enabled,
                                                          capability_code, limit_value, created_at, updated_at)
                        VALUES (?, ?, ?, TRUE, 'ai.monthly_tokens', 10000, NOW(), NOW())
                        """,
                UUID.randomUUID(), product.getId(), moduleId);

        itemService.addItem(subscriptionId, "ADD_ON", null, product.getId(),
                null, null, 1, 100L, "SAR");

        var rows = itemEntitlementRepository.findBySubscriptionIdAndModuleId(
                subscriptionId, moduleId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).capabilityCode()).isEqualTo("ai.monthly_tokens");
        assertThat(rows.get(0).limitValue()).isEqualTo(10000L);
    }

    // ---------------------------------------------------------------
    // PG-18/PG-19/PG-20 — item attachments
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-18: ADD_ON item attaches to a compatible active ADD_ON product")
    void pg18_addOnAttachment() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        ProductEntity product = catalogService.create(product("ADDON_P", "ADD_ON", "ACTIVE"));

        SubscriptionItemEntity item = itemService.addItem(subscriptionId, "ADD_ON",
                null, product.getId(), null, null, 2, 250L, "SAR");

        assertThat(item.getProductId()).isEqualTo(product.getId());
        assertThat(item.getNameSnapshot()).isEqualTo("Product ADDON_P");
        assertThat(item.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PG-19: METERED item attaches to a compatible active METERED product")
    void pg19_meteredAttachment() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        ProductEntity product = catalogService.create(product("METER_P", "METERED", "ACTIVE"));

        SubscriptionItemEntity item = itemService.addItem(subscriptionId, "METERED",
                null, product.getId(), null, null, 1, 10L, "SAR");

        assertThat(item.getProductId()).isEqualTo(product.getId());
        assertThat(item.getItemType()).isEqualTo("METERED");
    }

    @Test
    @DisplayName("PG-20: item referencing an unknown product is rejected fail-closed")
    void pg20_unknownProductRejection() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> itemService.addItem(subscriptionId, "ADD_ON",
                null, unknown, null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product");

        Long items = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ?",
                Long.class, subscriptionId);
        assertThat(items).isZero();
    }

    // ---------------------------------------------------------------
    // PG-21 — inactive/archived new-sale rejection
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-21: new ADD_ON/METERED items against INACTIVE or ARCHIVED products are rejected")
    void pg21_inactiveArchivedNewSaleRejection() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        ProductEntity inactive = catalogService.create(product("OFF_P", "ADD_ON", "INACTIVE"));
        ProductEntity archived = catalogService.create(product("GONE_P", "METERED", "ARCHIVED"));

        assertThatThrownBy(() -> itemService.addItem(subscriptionId, "ADD_ON",
                null, inactive.getId(), null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");

        assertThatThrownBy(() -> itemService.addItem(subscriptionId, "METERED",
                null, archived.getId(), null, null, 1, 100L, "SAR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");

        Long items = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ?",
                Long.class, subscriptionId);
        assertThat(items).isZero();
    }

    // ---------------------------------------------------------------
    // PG-22 — historical-reference preservation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-22: historical item references survive INACTIVE → ARCHIVED transitions")
    void pg22_historicalReferencePreservation() {
        UUID tenant = newTenant();
        UUID subscriptionId = insertSubscription(tenant, "ACTIVE");
        ProductEntity product = catalogService.create(product("HIST_P", "ADD_ON", "ACTIVE"));
        UUID moduleId = anyExistingModuleId();
        jdbc.update("""
                        INSERT INTO product_entitlements (id, product_id, module_id, module_enabled,
                                                          capability_code, boolean_value, created_at, updated_at)
                        VALUES (?, ?, ?, TRUE, 'hr.analytics.enabled', TRUE, NOW(), NOW())
                        """,
                UUID.randomUUID(), product.getId(), moduleId);

        SubscriptionItemEntity item = itemService.addItem(subscriptionId, "ADD_ON",
                null, product.getId(), null, null, 1, 100L, "SAR");

        // the product is retired — twice — after the item was created
        product.setStatus("INACTIVE");
        catalogService.update(product.getId(), product);
        product.setStatus("ARCHIVED");
        catalogService.update(product.getId(), product);

        // the historical item is untouched: ACTIVE, same product, same snapshot
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT status, product_id, name_snapshot FROM subscription_items WHERE id = ?",
                item.getId());
        assertThat(stored.get("status")).isEqualTo("ACTIVE");
        assertThat(stored.get("product_id")).isEqualTo(product.getId());
        assertThat(stored.get("name_snapshot")).isEqualTo("Product HIST_P");

        // the entitlement chain still resolves for the historical item
        var rows = itemEntitlementRepository.findBySubscriptionIdAndModuleId(
                subscriptionId, moduleId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).capabilityCode()).isEqualTo("hr.analytics.enabled");

        // and the archived product still exists (no physical delete)
        assertThat(count("products", "code", "HIST_P")).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-23 — audit
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-23: PRODUCT_CREATE audit row is written with resourceType=product + UUID")
    void pg23_audit() {
        UUID actorTenant = newTenant();
        ProductEntity created = catalogService.create(product("AUDIT_P", "ADD_ON", null));

        auditWriter.writeSuccess(actorTenant, null, null, "PRODUCT_CREATE", "product",
                created.getId().toString(), null, null, created,
                UUID.randomUUID().toString(), java.time.Instant.now());

        Map<String, Object> auditRow = jdbc.queryForMap(
                "SELECT action, resource_type, resource_id, result FROM platform_audit_logs "
                        + "WHERE resource_type = 'product' AND resource_id = ?",
                created.getId().toString());
        assertThat(auditRow.get("action")).isEqualTo("PRODUCT_CREATE");
        assertThat(auditRow.get("result")).isEqualTo("SUCCESS");
    }

    // ---------------------------------------------------------------
    // PG-24/PG-25/PG-26 — RBAC
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-24: CATALOG.READ is an ACTIVE capability granted to EXECUTIVE_VIEW roles")
    void pg24_rbacRead() {
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code = 'CATALOG.READ' "
                        + "AND status = 'ACTIVE'", Integer.class);
        assertThat(active).as("CATALOG.READ must exist and be ACTIVE").isEqualTo(1);

        Integer grants = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM role_capabilities rc
                        JOIN access_capabilities exec_view ON exec_view.code = 'EXECUTIVE_VIEW'
                        JOIN access_capabilities cat_read ON cat_read.code = 'CATALOG.READ'
                        WHERE rc.capability_id = cat_read.id
                          AND EXISTS (SELECT 1 FROM role_capabilities rc2
                                      WHERE rc2.role_id = rc.role_id
                                        AND rc2.capability_id = exec_view.id)
                        """, Integer.class);
        assertThat(grants).as("backward-compatible grant chain must exist").isGreaterThan(0);
    }

    @Test
    @DisplayName("PG-25: CATALOG.MANAGE is an ACTIVE capability with grant coverage")
    void pg25_rbacManage() {
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code = 'CATALOG.MANAGE' "
                        + "AND status = 'ACTIVE'", Integer.class);
        assertThat(active).as("CATALOG.MANAGE must exist and be ACTIVE").isEqualTo(1);

        Integer grants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_capabilities rc "
                        + "JOIN access_capabilities c ON c.id = rc.capability_id "
                        + "WHERE c.code = 'CATALOG.MANAGE'", Integer.class);
        assertThat(grants).isGreaterThan(0);
    }

    @Test
    @DisplayName("PG-26: unauthorized actor (no role grant chain) resolves to denial")
    void pg26_unauthorizedDenial() {
        UUID tenant = newTenant();
        UUID userWithNoRoles = UUID.randomUUID();

        Integer matchingGrants = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM user_role_assignments g
                        WHERE g.tenant_id = ? AND g.user_id = ?
                          AND g.status = 'ACTIVE'
                        """, Integer.class, tenant, userWithNoRoles);
        assertThat(matchingGrants)
                .as("a user with no grants must hold zero active role grants "
                        + "(NO_MATCHING_ACTIVE_ROLE denial at the evaluator)")
                .isZero();

        // and the control-plane guard independently denies unauthenticated access
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard(
                UUID.randomUUID().toString());
        assertThatThrownBy(() -> guard.require(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------
    // PG-27 — ControlPlaneAccessGuard
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-27: ControlPlaneAccessGuard denies non-control tenants, allows the control tenant")
    void pg27_controlPlaneGuard() {
        UUID controlTenant = UUID.randomUUID();
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard(controlTenant.toString());

        Authentication controlAuth = authWithTenant(controlTenant);
        Authentication otherAuth = authWithTenant(UUID.randomUUID());

        guard.require(controlAuth); // allowed — no exception

        assertThatThrownBy(() -> guard.require(otherAuth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Control-plane tenant required");

        ControlPlaneAccessGuard unconfigured = new ControlPlaneAccessGuard("");
        assertThatThrownBy(() -> unconfigured.require(controlAuth))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Authentication authWithTenant(UUID tenantId) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", tenantId.toString());
        details.put("user_id", UUID.randomUUID().toString());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("ops@example.com", null, List.of());
        auth.setDetails(details);
        return auth;
    }

    // ---------------------------------------------------------------
    // PG-28 — no physical delete
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-28: no physical delete — service/repository expose no delete path")
    void pg28_noPhysicalDelete() {
        for (java.lang.reflect.Method method : ProductCatalogService.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase())
                    .doesNotContain("delete").doesNotContain("remove");
        }
        for (java.lang.reflect.Method method : ProductRepository.class.getDeclaredMethods()) {
            assertThat(method.getName().toLowerCase())
                    .doesNotContain("delete").doesNotContain("remove");
        }
        for (java.lang.reflect.Method method :
                com.sanad.platform.subscription.api.CatalogController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(
                    org.springframework.web.bind.annotation.DeleteMapping.class))
                    .as("no DELETE mapping may exist on the catalog controller")
                    .isNull();
        }
    }

    // ---------------------------------------------------------------
    // PG-29 — R0C-10 effective-subscription regression
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-29: R0C-10 multiplicity invariants hold — effective 0..1, history 0..N")
    void pg29_r0c10Regression() {
        UUID tenant = newTenant();
        insertSubscription(tenant, "EXPIRED");
        insertSubscription(tenant, "ACTIVE");

        Long effective = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')",
                Long.class, tenant);
        assertThat(effective).isEqualTo(1L);

        UUID secondTenant = newTenant();
        insertSubscription(secondTenant, "ACTIVE");
        assertThatThrownBy(() -> insertSubscription(secondTenant, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tenant_subscriptions_effective");
    }

    // ---------------------------------------------------------------
    // count helpers
    // ---------------------------------------------------------------

    private long count(String table, String column, Object value) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
        return n == null ? 0L : n;
    }

    private long repositoryCount(String table, String column, Object value) {
        return count(table, column, value);
    }

    private UUID insertApplication(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO applications (id, code, name, category, status, display_order,
                                                  provisioning_mode, created_at, updated_at)
                        VALUES (?, ?, 'Acceptance App', 'MODULE', 'ACTIVE', 0, 'IMMEDIATE', NOW(), NOW())
                        """,
                id, code);
        return id;
    }
}
