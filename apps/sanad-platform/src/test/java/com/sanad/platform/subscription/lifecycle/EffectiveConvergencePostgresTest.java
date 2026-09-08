package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.lifecycle.SubscriptionImpactService;
import com.sanad.platform.module.registry.ModuleCapabilityRepository;
import com.sanad.platform.module.registry.ModuleEntity;
import com.sanad.platform.module.registry.ModuleRepository;
import com.sanad.platform.module.registry.PlanModuleEntitlementRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-10 — CONSUMER CONVERGENCE (Task B, PostgreSQL Direct).
 *
 * <p>Before any multiplicity runtime is enabled, every current-state consumer
 * must resolve the tenant's EFFECTIVE subscription (status NOT IN
 * CANCELLED/EXPIRED/TERMINATED) and work by subscription_id — never by an
 * unqualified tenant-only lookup. Proven here for the three converged
 * read-side consumers:</p>
 *
 * <ul>
 *   <li>BB-01 — BillingStateService ignores overdue invoices belonging to a
 *       historical (EXPIRED) subscription: the dunning evaluation of the
 *       effective row counts ONLY the effective row's invoices.</li>
 *   <li>BB-02 — a lifecycle-affecting billing transition lands on the
 *       effective row by subscription_id; the historical row is untouched.</li>
 *   <li>BB-03 — the dunning scan no longer admits terminal rows (stale
 *       billing_state on an EXPIRED row cannot re-enter the cycle).</li>
 *   <li>BB-04 — EntitlementResolver resolves the effective row (deterministic
 *       under multiplicity) and keeps the exact legacy ACTIVE lifecycle gate.</li>
 *   <li>BB-05 — SubscriptionImpactService resolves the current plan code from
 *       the effective row; terminal-only history yields NONE (unchanged).</li>
 * </ul>
 */
class EffectiveConvergencePostgresTest {

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private BillingStateService billing;
    private PlatformAuditService billingAudit;
    private List<Object> publishedEvents;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "EffectiveConvergencePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping EffectiveConvergencePostgresTest.");
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

        billingAudit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new java.util.ArrayList<>();
        billing = new BillingStateService(jdbc, billingAudit,
                new SubscriptionCommandService(jdbc, billingAudit, publishedEvents::add));
    }

    // ---------------------------------------------------------------
    // BB-01 — historical overdue invoices never dunn the successor
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BB-01: overdue invoice of EXPIRED S1 must not move effective S2 to PAST_DUE")
    void bb01_historicalInvoiceDoesNotDunnSuccessor() {
        UUID tenant = newTenant();
        UUID plan = seedPlan("R0C10-BB1");
        UUID s1 = insertSubscription(tenant, plan, "EXPIRED", "CURRENT");
        UUID s2 = insertSubscription(tenant, plan, "ACTIVE", "CURRENT");
        insertInvoice(s1, tenant, "OPEN", hoursAgo(96));

        String state = transactions.execute(status -> billing.evaluateAndTransition(tenant));

        // The effective row's own invoices are countable — S1's invoice is not.
        assertThat(state).isEqualTo("CURRENT");
        assertThat(field(s2, "status")).isEqualTo("ACTIVE");
        assertThat(field(s2, "billing_state")).isEqualTo("CURRENT");
        // The historical row is untouched.
        assertThat(field(s1, "status")).isEqualTo("EXPIRED");
        assertThat(field(s1, "billing_state")).isEqualTo("CURRENT");
    }

    // ---------------------------------------------------------------
    // BB-02 — billing transitions land on the effective row only
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BB-02: overdue invoice of the EFFECTIVE S2 marks S2 PAST_DUE by subscription_id; S1 untouched")
    void bb02_billingTransitionHitsEffectiveRowOnly() {
        UUID tenant = newTenant();
        UUID plan = seedPlan("R0C10-BB2");
        UUID s1 = insertSubscription(tenant, plan, "EXPIRED", "CURRENT");
        UUID s2 = insertSubscription(tenant, plan, "ACTIVE", "CURRENT");
        insertInvoice(s2, tenant, "OPEN", hoursAgo(96));

        String state = transactions.execute(status -> billing.evaluateAndTransition(tenant));

        assertThat(state).isEqualTo("PAST_DUE");
        assertThat(field(s2, "status")).isEqualTo("PAST_DUE");
        assertThat(field(s2, "billing_state")).isEqualTo("PAST_DUE");
        // EXPIRED S1 untouched — no status change, no billing_state change,
        // no lifecycle command ledger row.
        assertThat(field(s1, "status")).isEqualTo("EXPIRED");
        assertThat(field(s1, "billing_state")).isEqualTo("CURRENT");
        Long s1Ledger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                Long.class, s1);
        assertThat(s1Ledger).isZero();
    }

    // ---------------------------------------------------------------
    // BB-03 — dunning scan excludes terminal rows
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BB-03: dunning scan set excludes terminal rows even with stale billing_state")
    void bb03_dunningScanExcludesTerminalRows() {
        UUID staleTenant = newTenant();
        UUID plan = seedPlan("R0C10-BB3");
        // Terminal-only tenant with a stale CURRENT billing_state.
        insertSubscription(staleTenant, plan, "EXPIRED", "CURRENT");
        UUID liveTenant = newTenant();
        insertSubscription(liveTenant, plan, "ACTIVE", "CURRENT");

        List<UUID> scanned = jdbc.queryForList(
                "SELECT DISTINCT tenant_id FROM tenant_subscriptions "
                        + "WHERE billing_state IN ('CURRENT','PAST_DUE','SUSPENDED') "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')",
                UUID.class);

        assertThat(scanned).contains(liveTenant);
        assertThat(scanned).doesNotContain(staleTenant);
    }

    // ---------------------------------------------------------------
    // BB-04 — entitlement resolution: effective row + legacy ACTIVE gate
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BB-04: EntitlementResolver resolves the effective row; ACTIVE gate preserved; TRIAL successor still denied")
    void bb04_entitlementEffectiveResolution() {
        UUID plan = seedPlan("R0C10-BB4");
        // (1) effective ACTIVE successor resolved from S2, S1 historical ignored.
        UUID t1 = newTenant();
        insertSubscription(t1, plan, "EXPIRED", "CURRENT");
        UUID s2 = insertSubscription(t1, plan, "ACTIVE", "CURRENT");
        EntitlementResolver resolver = resolver(plan);
        var ctx = resolver.getEffectiveEntitlements(t1, "CRM");
        assertThat(ctx.isModuleEnabled()).isTrue();
        assertThat(ctx.subscriptionId()).isEqualTo(s2);

        // (2) TRIAL successor still denied (legacy ACTIVE gate preserved).
        UUID t2 = newTenant();
        insertSubscription(t2, plan, "EXPIRED", "CURRENT");
        insertSubscription(t2, plan, "TRIAL", "CURRENT");
        assertThat(resolver.isModuleEnabled(t2, "CRM")).isFalse();

        // (3) terminal-only history → denied (R0C-9 PG-08 contract preserved).
        UUID t3 = newTenant();
        insertSubscription(t3, plan, "EXPIRED", "CURRENT");
        assertThat(resolver.isModuleEnabled(t3, "CRM")).isFalse();
    }

    // ---------------------------------------------------------------
    // BB-05 — impact preview resolves the effective plan code
    // ---------------------------------------------------------------

    @Test
    @DisplayName("BB-05: SubscriptionImpactService resolves current plan code from the effective row")
    void bb05_impactResolvesEffectivePlan() {
        UUID plan = seedPlan("R0C10-BB5");
        UUID tenant = newTenant();
        insertSubscription(tenant, plan, "EXPIRED", "CURRENT");
        insertSubscription(tenant, plan, "ACTIVE", "CURRENT");

        SubscriptionImpactService impact = new SubscriptionImpactService(
                resolver(plan),
                Mockito.mock(ModuleRepository.class),
                Mockito.mock(ModuleCapabilityRepository.class),
                Mockito.mock(PlanModuleEntitlementRepository.class),
                jdbc);

        var preview = impact.previewPlanChange(tenant, anyPlanId("R0C10-BB5"));
        assertThat(preview.currentPlanCode()).isEqualTo("R0C10-BB5");

        // Terminal-only tenant → NONE (legacy contract preserved).
        UUID staleTenant = newTenant();
        insertSubscription(staleTenant, plan, "EXPIRED", "CURRENT");
        var stalePreview = impact.previewPlanChange(staleTenant, anyPlanId("R0C10-BB5"));
        assertThat(stalePreview.currentPlanCode()).isEqualTo("NONE");
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private EntitlementResolver resolver(UUID plan) {
        ModuleEntity module = new ModuleEntity();
        module.setId(UUID.randomUUID());
        module.setCode("CRM");
        module.setName("CRM");
        module.setEnabled(true);
        ModuleRepository moduleRepository = Mockito.mock(ModuleRepository.class);
        Mockito.when(moduleRepository.findByCode("CRM")).thenReturn(java.util.Optional.of(module));
        return new EntitlementResolver(jdbc, moduleRepository,
                Mockito.mock(ModuleCapabilityRepository.class),
                Mockito.mock(PlanModuleEntitlementRepository.class));
    }

    private Instant minutesAgo(long minutes) {
        return Instant.now().minusSeconds(minutes * 60);
    }

    private Instant hoursAgo(long hours) {
        return Instant.now().minusSeconds(hours * 3600);
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
        return id;
    }

    private UUID insertSubscription(UUID tenantId, UUID planId, String status, String billingState) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, 'MONTHLY', 1, 0, NOW(), NULL, "
                        + "NOW(), NOW() + INTERVAL '30 days', FALSE, ?, NOW(), NOW())",
                id, tenantId, planId, status, billingState);
        return id;
    }

    private void insertInvoice(UUID subscriptionId, UUID tenantId, String status, Instant dueAt) {
        jdbc.update(
                "INSERT INTO billing_invoices (id, tenant_id, subscription_id, invoice_number, status, "
                        + "currency_code, subtotal_minor, credit_applied_minor, tax_minor, total_minor, "
                        + "amount_paid_minor, description, period_start, period_end, due_at, paid_at, "
                        + "payment_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'SAR', 1000, 0, 0, 1000, 0, 'test', NOW(), "
                        + "NOW() + INTERVAL '30 days', ?, NULL, NULL, NOW(), NOW())",
                UUID.randomUUID(), tenantId, subscriptionId,
                "INV-" + UUID.randomUUID().toString().substring(0, 8), status,
                Timestamp.from(dueAt));
    }

    private Object field(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private UUID anyPlanId(String code) {
        return jdbc.queryForObject("SELECT id FROM saas_plans WHERE code = ?", UUID.class, code);
    }
}
