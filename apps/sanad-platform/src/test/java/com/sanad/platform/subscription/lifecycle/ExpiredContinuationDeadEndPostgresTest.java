package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.BillingStateService;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.module.registry.ModuleCapabilityRepository;
import com.sanad.platform.module.registry.ModuleEntity;
import com.sanad.platform.module.registry.ModuleRepository;
import com.sanad.platform.module.registry.PlanModuleEntitlementRepository;
import com.sanad.platform.subscription.change.SubscriptionChangeService;
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
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-9 — EXPIRED SUBSCRIPTION CONTINUATION / MULTIPLICITY CONTRACT GATE (RED).
 *
 * <p>Proves, on PostgreSQL Direct through public/real production paths, that
 * a tenant whose trial expired under the R0C-8 runtime is a lifecycle
 * dead end in the current repository state:</p>
 *
 * <ul>
 *   <li>PG-01 — real production path: trial subscription expires to EXPIRED
 *       through the R0C-8 runtime driver (canonical EXPIRE command).</li>
 *   <li>PG-02 — {@code createSubscription} for the same tenant is rejected
 *       by the one-subscription-per-tenant count guard (409 CONFLICT).</li>
 *   <li>PG-03 — {@code RESUME} from EXPIRED is illegal in the canonical
 *       lifecycle table (both public command path and legacy endpoint).</li>
 *   <li>PG-04 — {@code RENEW} from EXPIRED is illegal (both paths).</li>
 *   <li>PG-05 — {@code ACTIVATE} from EXPIRED is illegal, and in fact EVERY
 *       lifecycle command is illegal from EXPIRED (terminal, no exits).</li>
 *   <li>PG-06 — a second subscription row for the same tenant is impossible:
 *       {@code uk_tenant_subscriptions_tenant UNIQUE (tenant_id)} rejects the
 *       INSERT at the storage layer (legacy one-per-tenant model).</li>
 *   <li>PG-07 — the billing tenant lookup is unqualified-by-status: it finds
 *       the EXPIRED row (findSubscription returns it; evaluateAndTransition
 *       no-ops on it) — billing relies on the one-row uniqueness.</li>
 *   <li>PG-08 — the entitlement tenant lookup requires status ACTIVE: with
 *       only an EXPIRED row, the resolver denies every module (no effective
 *       subscription).</li>
 *   <li>PG-09 — tenant isolation is unaffected: tenant A's dead end never
 *       touches tenant B's subscription, ledger, events or entitlement
 *       resolution.</li>
 * </ul>
 *
 * <p>This battery is deliberately READ-ONLY with respect to the contract
 * decision: it captures the CURRENT behavior as evidence for the R0C-9
 * decision gate. No continuation semantics are invented here.</p>
 */
class ExpiredContinuationDeadEndPostgresTest {

    private JdbcTemplate jdbc;
    private SaasAdministrationService legacy;
    private BillingStateService billing;
    private PlatformAuditService audit;
    private PlatformAuditService billingAudit;
    private List<Object> publishedEvents;
    private TransactionTemplate transactions;

    private UUID tenant;
    private UUID planA;
    private UUID versionA;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExpiredContinuationDeadEndPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ExpiredContinuationDeadEndPostgresTest.");
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

        audit = Mockito.mock(PlatformAuditService.class);
        billingAudit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);
        billing = new BillingStateService(jdbc, billingAudit,
                new SubscriptionCommandService(jdbc, billingAudit, publishedEvents::add));

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        versionA = UUID.randomUUID();
        seedTenant(tenant, "SA");
        seedPlan(planA, "R0C9-A");
        seedPlanVersion(versionA, planA, 1, "ACTIVE", 30000L);
    }

    /** R0C-8 runtime driver under test — wall clock. */
    private TrialExpirationService driver() {
        return new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                transactions, Clock.systemUTC());
    }

    /** Canonical public command authority (the lifecycle command API path). */
    private SubscriptionCommandService commands() {
        return new SubscriptionCommandService(jdbc, audit, publishedEvents::add);
    }

    // ---------------------------------------------------------------
    // PG-01 — trial → EXPIRED through the real production path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-01: real trial subscription expires to EXPIRED via the R0C-8 runtime driver")
    void pg01_trialExpiresToExpired() {
        UUID sub = createDueTrial(tenant, planA);

        TrialExpirationService.TrialExpiryResult result = driver().runTrialExpiryCycleOnce();

        assertThat(result.expired()).isEqualTo(1);
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "EXPIRE")).isEqualTo(1L);
        // The expired subscription is the tenant's ONLY subscription row.
        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", Long.class, tenant);
        assertThat(rows).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-02 — second createSubscription (public POST /subscriptions)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-02: createSubscription again for the EXPIRED tenant → 409 CONFLICT (one-per-tenant count guard)")
    void pg02_secondCreateRejected() {
        UUID sub = createDueTrial(tenant, planA);
        driver().runTrialExpiryCycleOnce();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");

        assertThatThrownBy(() -> transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Tenant already has a subscription");

        // Nothing was written: still exactly one row for the tenant.
        Long rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", Long.class, tenant);
        assertThat(rows).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-03 — RESUME from EXPIRED
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-03: canonical RESUME from EXPIRED is illegal (terminal — no exit transitions)")
    void pg03_resumeExpiredIllegal() {
        UUID sub = expiredSubscription();

        assertThatThrownBy(() -> transactions.execute(status ->
                commands().execute(sub, "RESUME", "r0c9 red")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal subscription transition: RESUME from EXPIRED");

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        // No ledger row was written for the rejected command.
        assertThat(ledgerCount(sub, "RESUME")).isZero();
    }

    @Test
    @DisplayName("PG-03b: legacy resume endpoint from EXPIRED is a silent no-op — status stays EXPIRED but a misleading RESUMED event is recorded")
    void pg03b_legacyResumeSilentNoOp() {
        UUID sub = expiredSubscription();
        Long eventsBefore = changeEventCount(sub);

        // The legacy resume endpoint does NOT reject EXPIRED (its guard only
        // special-cases CANCELLED): the non-CANCELLED branch clears
        // cancel_at_period_end and returns — the status transition is skipped.
        transactions.executeWithoutResult(status -> legacy.resumeSubscription(sub, null));

        // The subscription is NOT revived — the dead end stands.
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        // But a SUBSCRIPTION.RESUMED change event was recorded anyway — a
        // misleading audit artifact claiming a resume that never happened.
        assertThat(changeEventCount(sub)).isEqualTo(eventsBefore + 1);
        Long resumedEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                        + "AND action = 'SUBSCRIPTION.RESUMED'", Long.class, sub);
        assertThat(resumedEvents).isEqualTo(1L);
        // No lifecycle RESUME ledger row exists — the canonical authority
        // was never invoked (the revival never happened).
        assertThat(ledgerCount(sub, "RESUME")).isZero();
    }

    // ---------------------------------------------------------------
    // PG-04 — RENEW from EXPIRED
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-04: canonical RENEW from EXPIRED is illegal")
    void pg04_renewExpiredIllegal() {
        UUID sub = expiredSubscription();

        assertThatThrownBy(() -> transactions.execute(status ->
                commands().execute(sub, "RENEW", "r0c9 red")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal subscription transition: RENEW from EXPIRED");

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "RENEW")).isZero();
    }

    @Test
    @DisplayName("PG-04b: legacy renew endpoint path from EXPIRED → 409 CONFLICT")
    void pg04b_legacyRenewRejected() {
        UUID sub = expiredSubscription();

        assertThatThrownBy(() -> transactions.execute(status ->
                legacy.renewSubscription(sub, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        // No renewal invoice was issued for the EXPIRED subscription.
        Long invoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, sub);
        assertThat(invoices).isZero();
    }

    // ---------------------------------------------------------------
    // PG-05 — ACTIVATE from EXPIRED + full command sweep
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-05: canonical ACTIVATE from EXPIRED is illegal")
    void pg05_activateExpiredIllegal() {
        UUID sub = expiredSubscription();

        assertThatThrownBy(() -> transactions.execute(status ->
                commands().execute(sub, "ACTIVATE", "r0c9 red")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal subscription transition: ACTIVATE from EXPIRED");

        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        assertThat(ledgerCount(sub, "ACTIVATE")).isZero();
    }

    @Test
    @DisplayName("PG-05b: EVERY lifecycle command is illegal from EXPIRED (terminal, zero exits, no invented revival API)")
    void pg05b_everyCommandIllegalFromExpired() {
        UUID sub = expiredSubscription();

        for (String command : SubscriptionLifecycle.COMMANDS.keySet()) {
            assertThatThrownBy(() -> transactions.execute(status ->
                            commands().execute(sub, command, "r0c9 sweep")))
                    .as("command %s should be illegal from EXPIRED", command)
                    .isInstanceOf(IllegalStateException.class);
            // The sweep never mutated the terminal row.
            assertThat(subscriptionField(sub, "status")).as("after %s", command).isEqualTo("EXPIRED");
        }

        // Only the original EXPIRE ledger row exists — no command from the sweep landed.
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?", Long.class, sub);
        assertThat(total).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // PG-06 — storage-layer one-per-tenant constraint
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-06: direct INSERT of a second subscription row for the tenant violates uk_tenant_subscriptions_tenant")
    void pg06_uniqueTenantConstraintBlocksSecondRow() {
        expiredSubscription();

        // The constraint is exactly UNIQUE(tenant_id) on tenant_subscriptions.
        String constraintDef = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'uk_tenant_subscriptions_tenant' "
                        + "AND conrelid = 'tenant_subscriptions'::regclass",
                String.class);
        assertThat(constraintDef).isEqualTo("UNIQUE (tenant_id)");

        // A second row for the same tenant cannot exist at the storage layer —
        // regardless of the target status (even a terminal/EXPIRED one).
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'EXPIRED', 'MONTHLY', 1, 0, NOW(), NULL, NOW(), "
                        + "NOW() + INTERVAL '30 days', FALSE, 'CURRENT', NOW(), NOW())",
                UUID.randomUUID(), tenant, planA, versionA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_tenant_subscriptions_tenant");
    }

    // ---------------------------------------------------------------
    // PG-07 — billing tenant lookup behavior with an EXPIRED row
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-07: billing tenant lookup is unqualified — it finds the EXPIRED row and relies on one-row uniqueness")
    void pg07_billingTenantLookupFindsExpiredRow() {
        UUID sub = expiredSubscription();

        // The exact production SQL BillingStateService.findSubscription runs.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT billing_state FROM tenant_subscriptions WHERE tenant_id = ?", tenant);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("billing_state")).isEqualTo("CURRENT");

        // evaluateAndTransition on the expired tenant does not crash and does
        // not mutate anything: the billing machine targets the (only) EXPIRED
        // row for this tenant. It relies on the row's uniqueness — there is no
        // subscription-scoped selection rule.
        String state = transactions.execute(status -> billing.evaluateAndTransition(tenant));
        assertThat(state).isEqualTo("CURRENT");
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");

        // Dunning scan keys on tenant_id (not subscription_id) — the expired
        // tenant is absent from the CURRENT/PAST_DUE/SUSPENDED billing_state
        // scan set by luck of its billing_state value, not by status.
        List<UUID> dunned = jdbc.queryForList(
                "SELECT tenant_id FROM tenant_subscriptions "
                        + "WHERE billing_state IN ('CURRENT','PAST_DUE','SUSPENDED')", UUID.class);
        // (billing_state='CURRENT' IS in the scan set — the row is scanned by
        // tenant even though its lifecycle status is terminal.)
        assertThat(dunned).contains(tenant);
    }

    // ---------------------------------------------------------------
    // PG-08 — entitlement tenant lookup behavior with an EXPIRED row
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-08: entitlement lookup requires status ACTIVE — EXPIRED-only tenant resolves to denied everywhere")
    void pg08_entitlementLookupDeniedForExpiredTenant() {
        expiredSubscription();

        // The exact production SQL EntitlementResolver.findActiveSubscription runs.
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT id, plan_id FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status = 'ACTIVE' LIMIT 1", tenant);
        assertThat(active).isEmpty();

        // Full resolver behavior through the real entitlement engine: the
        // expired tenant is denied (no effective ACTIVE subscription).
        ModuleEntity module = new ModuleEntity();
        module.setId(UUID.randomUUID());
        module.setCode("CRM");
        module.setName("CRM");
        module.setEnabled(true);
        ModuleRepository moduleRepository = Mockito.mock(ModuleRepository.class);
        Mockito.when(moduleRepository.findByCode("CRM")).thenReturn(java.util.Optional.of(module));
        EntitlementResolver resolver = new EntitlementResolver(jdbc, moduleRepository,
                Mockito.mock(ModuleCapabilityRepository.class),
                Mockito.mock(PlanModuleEntitlementRepository.class));

        assertThat(resolver.isModuleEnabled(tenant, "CRM")).isFalse();
        var ctx = resolver.getEffectiveEntitlements(tenant, "CRM");
        assertThat(ctx.isModuleEnabled()).isFalse();
        assertThat(ctx.subscriptionId()).isNull();
    }

    // ---------------------------------------------------------------
    // PG-09 — tenant isolation unaffected
    // ---------------------------------------------------------------

    @Test
    @DisplayName("PG-09: tenant A's expired dead end never touches tenant B (rows, ledger, events, entitlement lookups)")
    void pg09_tenantIsolationUnaffected() {
        UUID other = UUID.randomUUID();
        seedTenant(other, "AE");
        UUID subA = createDueTrial(tenant, planA);
        UUID subB = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(other, planA, "MONTHLY", 2, 0), null).id());

        driver().runTrialExpiryCycleOnce();
        assertThat(subscriptionField(subA, "status")).isEqualTo("EXPIRED");

        // Tenant A's dead end: every revival attempt rejected...
        assertThatThrownBy(() -> transactions.execute(status ->
                commands().execute(subA, "RESUME", "r0c9 isolation")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transactions.execute(status ->
                commands().execute(subA, "RENEW", "r0c9 isolation")))
                .isInstanceOf(IllegalStateException.class);

        // ...and tenant B is completely untouched.
        assertThat(subscriptionField(subB, "status")).isEqualTo("ACTIVE");
        assertThat(ledgerCount(subB, "RESUME")).isZero();
        assertThat(ledgerCount(subB, "RENEW")).isZero();
        assertThat(ledgerCount(subB, "EXPIRE")).isZero();
        Long bInvoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, subB);
        assertThat(bInvoices).isGreaterThanOrEqualTo(1L); // its own initial invoice only

        // Entitlement resolution is tenant-scoped: B resolves via its own
        // ACTIVE subscription, A resolves to nothing.
        List<Map<String, Object>> aActive = jdbc.queryForList(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 1", tenant);
        List<Map<String, Object>> bActive = jdbc.queryForList(
                "SELECT id FROM tenant_subscriptions WHERE tenant_id = ? AND status = 'ACTIVE' LIMIT 1", other);
        assertThat(aActive).isEmpty();
        assertThat(bActive).hasSize(1);
        assertThat(bActive.get(0).get("id")).isEqualTo(subB);

        // No cross-tenant command ledger row exists.
        Long aLedgerUnderB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE tenant_id = ? "
                        + "AND subscription_id = ?", Long.class, tenant, subB);
        assertThat(aLedgerUnderB).isZero();
    }

    // ---------------------------------------------------------------
    // Seed helpers (production paths where possible — R0C-8 conventions)
    // ---------------------------------------------------------------

    /** Real production path: trial, expired by the real R0C-8 driver. */
    private UUID expiredSubscription() {
        UUID sub = createDueTrial(tenant, planA);
        driver().runTrialExpiryCycleOnce();
        assertThat(subscriptionField(sub, "status")).isEqualTo("EXPIRED");
        return sub;
    }

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

    private void seedPlanVersion(UUID id, UUID planId, int number, String status, long monthlyMinor) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NOW(), 'SAR', ?, ?, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                id, planId, number, status, monthlyMinor, monthlyMinor * 10);
    }

    /** Real production create path: trial subscription, TRIALING + trial_ends_at = now + trialDays. */
    private UUID createTrial(UUID tenantId, UUID planId, int trialDays) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, trialDays), null).id());
    }

    /** Trial created through the real path, then backdated to simulate elapsed time. */
    private UUID createDueTrial(UUID tenantId, UUID planId) {
        UUID sub = createTrial(tenantId, planId, 14);
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), sub);
        return sub;
    }

    private Object subscriptionField(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long ledgerCount(UUID subscriptionId, String command) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = ?",
                Long.class, subscriptionId, command);
        return count == null ? 0L : count;
    }

    private long changeEventCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ?",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }
}
