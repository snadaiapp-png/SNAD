package com.sanad.platform.subscription.provisioning;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.registry.ModuleCapabilityRepository;
import com.sanad.platform.module.registry.ModuleEntity;
import com.sanad.platform.module.registry.ModuleRepository;
import com.sanad.platform.module.registry.PlanModuleEntitlementRepository;
import com.sanad.platform.subscription.lifecycle.SubscriptionCommandService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R0C-10 Task I — ENTITLEMENT + PROVISIONING CONVERGENCE ISOLATION
 * (PostgreSQL Direct).
 *
 * <p>With history S1 (terminal) and effective successor S2:</p>
 * <ul>
 *   <li>QI-01 — entitlement is derived from S2 only (plan identity and
 *       module decision come from the effective row; the historical row's
 *       plan is irrelevant).</li>
 *   <li>QI-02 — a provisioning job for the historical EXPIRED S1 fails
 *       closed (terminal refusal) and never touches S2.</li>
 *   <li>QI-03 — a provisioning job for S2 activates S2 by subscription_id;
 *       the historical S1 is untouched.</li>
 *   <li>QI-04 — cross-tenant isolation: provisioning tenant B's subscription
 *       never mutates tenant A's rows, ledger or entitlements.</li>
 * </ul>
 */
class EntitlementProvisioningIsolationPostgresTest {

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;
    private UUID planA;
    private UUID planB;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "EntitlementProvisioningIsolationPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping EntitlementProvisioningIsolationPostgresTest.");
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
        audit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new java.util.ArrayList<>();

        planA = seedPlan("R0C10-IA");
        planB = seedPlan("R0C10-IB");
    }

    // ---------------------------------------------------------------
    // QI-01 — entitlement derived from the effective row only
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QI-01: entitlement resolution comes from effective S2's plan; historical S1's plan is irrelevant")
    void qi01_entitlementFromEffectiveRowOnly() {
        UUID tenant = newTenant();
        UUID s1 = insertSubscription(tenant, planA, "EXPIRED");
        UUID s2 = insertSubscription(tenant, planB, "ACTIVE");

        EntitlementResolver resolver = resolver();
        var ctx = resolver.getEffectiveEntitlements(tenant, "CRM");

        assertThat(ctx.isModuleEnabled()).isTrue();
        assertThat(ctx.subscriptionId()).isEqualTo(s2);
        assertThat(ctx.planId()).isEqualTo(planB);
        // The historical row's plan never appears in the resolution.
        assertThat(ctx.planId()).isNotEqualTo(jdbc.queryForObject(
                "SELECT plan_id FROM tenant_subscriptions WHERE id = ?", UUID.class, s1));
    }

    // ---------------------------------------------------------------
    // QI-02 — provisioning the historical row fails closed
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QI-02: provisioning job for EXPIRED S1 fails closed (terminal refusal); S2 untouched")
    void qi02_provisioningHistoricalRowFailsClosed() {
        UUID tenant = newTenant();
        UUID s1 = insertSubscription(tenant, planA, "EXPIRED");
        UUID s2 = insertSubscription(tenant, planB, "ACTIVE");

        ProvisioningJobRunner runner = runner();
        UUID jobId = enqueueJob(tenant, s1);
        ProvisioningJobRunner.JobOutcome outcome = runner.run(jobId);

        assertThat(outcome.status()).isEqualTo("FAILED");
        // The terminal subscription was never activated.
        assertThat(field(s1, "status")).isEqualTo("EXPIRED");
        // The effective successor is untouched: no status change, no ledger, no job.
        assertThat(field(s2, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(s2)).isZero();
        Long s2Jobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM provisioning_jobs WHERE subscription_id = ?", Long.class, s2);
        assertThat(s2Jobs).isZero();
    }

    // ---------------------------------------------------------------
    // QI-03 — provisioning the successor targets it by id
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QI-03: provisioning job for S2 activates S2 by subscription_id; historical S1 untouched")
    void qi03_provisioningSuccessorTargetsById() {
        UUID tenant = newTenant();
        UUID s1 = insertSubscription(tenant, planA, "EXPIRED");
        UUID s2 = insertSubscription(tenant, planB, "PENDING_ACTIVATION");

        ProvisioningJobRunner runner = runner();
        UUID jobId = enqueueJob(tenant, s2);
        ProvisioningJobRunner.JobOutcome outcome = runner.run(jobId);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(field(s2, "status")).isEqualTo("ACTIVE");
        // The historical row is untouched.
        assertThat(field(s1, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(s1)).isZero();
    }

    // ---------------------------------------------------------------
    // QI-04 — cross-tenant provisioning isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QI-04: provisioning tenant B's subscription never mutates tenant A (rows, ledger, jobs)")
    void qi04_crossTenantProvisioningIsolation() {
        UUID tenantA = newTenant();
        UUID tenantB = newTenant();
        UUID sA = insertSubscription(tenantA, planA, "ACTIVE");
        UUID sB = insertSubscription(tenantB, planB, "PENDING_ACTIVATION");

        ProvisioningJobRunner runner = runner();
        UUID jobB = enqueueJob(tenantB, sB);
        ProvisioningJobRunner.JobOutcome outcome = runner.run(jobB);

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(field(sB, "status")).isEqualTo("ACTIVE");
        // Tenant A completely untouched.
        assertThat(field(sA, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(sA)).isZero();
        Long aJobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM provisioning_jobs WHERE tenant_id = ?", Long.class, tenantA);
        assertThat(aJobs).isZero();
        // Tenant B's ledger exists only under tenant B's subscription.
        Long bLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE tenant_id = ?", Long.class, tenantB);
        assertThat(bLedger).isEqualTo(1L); // the canonical ACTIVATE transition
        Long aLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE tenant_id = ?", Long.class, tenantA);
        assertThat(aLedger).isZero();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private ProvisioningJobRunner runner() {
        return new ProvisioningJobRunner(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add));
    }

    private EntitlementResolver resolver() {
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

    private UUID enqueueJob(UUID tenantId, UUID subscriptionId) {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO provisioning_jobs (
                            id, tenant_id, subscription_id, action, status, attempts, created_at, updated_at
                        ) VALUES (?, ?, ?, 'PROVISION_SUBSCRIPTION', 'PENDING', 0, NOW(), NOW())
                        """,
                jobId, tenantId, subscriptionId);
        return jobId;
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
        return id;
    }

    private UUID insertSubscription(UUID tenantId, UUID planId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, 'MONTHLY', 1, 0, NOW(), NULL, "
                        + "NOW(), NOW() + INTERVAL '30 days', FALSE, 'CURRENT', NOW(), NOW())",
                id, tenantId, planId, status);
        seedPlanItem(id, tenantId, planId);
        return id;
    }

    /** Canonical birth composition: one ACTIVE PLAN item (V20260829_3 model). */
    private void seedPlanItem(UUID subscriptionId, UUID tenantId, UUID planId) {
        jdbc.update(
                "INSERT INTO subscription_items (id, tenant_id, subscription_id, item_type, "
                        + "plan_id, name_snapshot, quantity, unit_amount_minor, currency_code, status, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PLAN', ?, 'seeded plan item', 1, 30000, 'SAR', 'ACTIVE', NOW(), NOW())",
                UUID.randomUUID(), tenantId, subscriptionId, planId);
    }

    private Object field(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long ledgerCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }
}
