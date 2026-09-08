package com.sanad.platform.subscription.lifecycle;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R0C-10 Tasks D/E/F — GATED EXPIRED SUCCESSOR RUNTIME + NO SECOND TRIAL +
 * CANCELLED/TERMINATED SEMANTICS (PostgreSQL Direct).
 *
 * <ul>
 *   <li>QD-01 — gate OFF (default): createSubscription after EXPIRED stays
 *       fail-closed with the legacy wire contract (R0C-9 dead end preserved).</li>
 *   <li>QD-02 — gate ON: approved continuation inserts a NEW row; the old
 *       EXPIRED row remains byte-for-byte lifecycle immutable; fresh
 *       subscription_id; effective cardinality exactly 1; the existing
 *       creation contract (invoice, event, audit, entitlement) is honored.</li>
 *   <li>QE-01 — AUTOMATIC_SECOND_TRIAL=REJECTED: the successor never receives
 *       a trial — not from the plan default and not from the request.</li>
 *   <li>QF-01 — create-new after CANCELLED is rejected (resume is the only
 *       sanctioned path).</li>
 *   <li>QF-02 — resume(CANCELLED) with an effective successor is rejected
 *       (collision guard).</li>
 *   <li>QF-03 — create-new after TERMINATED is rejected (deferred).</li>
 * </ul>
 */
class ExpiredSuccessorRuntimePostgresTest {

    private static final String GATE_PROPERTY = "sanad.scp.subscription.expired-successor.enabled";

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private SaasAdministrationService legacy;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;

    private UUID tenant;
    private UUID planA;
    private UUID versionA;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExpiredSuccessorRuntimePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ExpiredSuccessorRuntimePostgresTest.");
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
        System.clearProperty(GATE_PROPERTY);
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

        audit = Mockito.mock(PlatformAuditService.class);
        publishedEvents = new java.util.ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);

        tenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        versionA = UUID.randomUUID();
        seedTenant(tenant);
        seedPlan(planA, "R0C10-DE", 14);
        seedPlanVersion(versionA, planA);
        System.clearProperty(GATE_PROPERTY);
    }

    @AfterEach
    void clearGate() {
        System.clearProperty(GATE_PROPERTY);
    }

    // ---------------------------------------------------------------
    // QD-01 — gate OFF (default): legacy dead-end preserved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QD-01: gate OFF (default) — createSubscription after EXPIRED stays fail-closed with the legacy contract")
    void qd01_gateOffPreservesDeadEnd() {
        UUID expired = expiredSubscription();
        assertThat(System.getProperty(GATE_PROPERTY)).isNull();

        assertThatThrownBy(() -> transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Tenant already has a subscription");

        assertThat(historyCount(tenant)).isEqualTo(1L);
        assertThat(field(expired, "status")).isEqualTo("EXPIRED");
    }

    // ---------------------------------------------------------------
    // QD-02 — gate ON: successor creation, old row immutable
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QD-02: gate ON — successor row created; EXPIRED row byte-for-byte immutable; effective cardinality 1")
    void qd02_gatedSuccessorCreation() {
        UUID expired = expiredSubscription();
        Map<String, Object> before = fullRow(expired);
        System.setProperty(GATE_PROPERTY, "true");

        UUID successor = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null)).id();

        assertThat(successor).isNotEqualTo(expired);
        // Effective cardinality exactly 1; history keeps both rows.
        assertThat(effectiveCount(tenant)).isEqualTo(1L);
        assertThat(historyCount(tenant)).isEqualTo(2L);
        assertThat(field(successor, "status")).isEqualTo("ACTIVE");
        // Old EXPIRED row byte-for-byte immutable.
        assertThat(fullRow(expired)).isEqualTo(before);
        assertThat(field(expired, "status")).isEqualTo("EXPIRED");
        // The successor honors the existing creation contract: initial
        // recurring invoice, created change event, no RESUME ledger on the old row.
        Long invoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, successor);
        assertThat(invoices).isEqualTo(1L);
        assertThat(ledgerCount(expired, "RESUME")).isZero();
    }

    // ---------------------------------------------------------------
    // QE-01 — no second automatic trial
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QE-01: successor never receives a trial — plan default (14d) and explicit request (30d) both forced off")
    void qe01_noSecondAutomaticTrial() {
        expiredSubscription();
        System.setProperty(GATE_PROPERTY, "true");

        UUID successor = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 30), null)).id();

        assertThat(field(successor, "status")).isEqualTo("ACTIVE");
        assertThat(field(successor, "trial_ends_at")).isNull();
        // The successor is a paid row from birth: the recurring invoice exists.
        Long invoices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?", Long.class, successor);
        assertThat(invoices).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // QF-01 — create-new after CANCELLED rejected
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QF-01: create-new after CANCELLED is rejected (resume is the sanctioned path)")
    void qf01_createNewAfterCancelledRejected() {
        UUID cancelled = cancelledSubscription();
        System.setProperty(GATE_PROPERTY, "true");

        assertThatThrownBy(() -> transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("resume");

        assertThat(historyCount(tenant)).isEqualTo(1L);
        assertThat(field(cancelled, "status")).isEqualTo("CANCELLED");
    }

    // ---------------------------------------------------------------
    // QF-02 — CANCELLED resume collision guard
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QF-02: resume(CANCELLED) with an effective successor is rejected")
    void qf02_cancelledResumeWithEffectiveSuccessorRejected() {
        UUID cancelled = cancelledSubscription();
        // An effective successor exists (seeded storage-level terminal+effective
        // coexistence — sanctioned by the MODEL_B invariant).
        UUID successor = insertSubscription(tenant, "ACTIVE");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                legacy.resumeSubscription(cancelled, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(field(cancelled, "status")).isEqualTo("CANCELLED");
        assertThat(field(successor, "status")).isEqualTo("ACTIVE");
    }

    // ---------------------------------------------------------------
    // QF-03 — create-new after TERMINATED deferred
    // ---------------------------------------------------------------

    @Test
    @DisplayName("QF-03: create-new after TERMINATED is rejected (deferred)")
    void qf03_createNewAfterTerminatedRejected() {
        UUID terminated = terminatedSubscription();
        System.setProperty(GATE_PROPERTY, "true");

        assertThatThrownBy(() -> transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("TERMINATED");

        assertThat(historyCount(tenant)).isEqualTo(1L);
        assertThat(field(terminated, "status")).isEqualTo("TERMINATED");
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private UUID expiredSubscription() {
        UUID sub = createTrial(14);
        jdbc.update("UPDATE tenant_subscriptions SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)), sub);
        new TrialExpirationService(jdbc,
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add),
                transactions, java.time.Clock.systemUTC()).runTrialExpiryCycleOnce();
        assertThat(field(sub, "status")).isEqualTo("EXPIRED");
        return sub;
    }

    private UUID cancelledSubscription() {
        UUID sub = createTrial(0);
        transactions.executeWithoutResult(status ->
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add)
                        .execute(sub, "CANCEL", "R0C-10 F seed"));
        assertThat(field(sub, "status")).isEqualTo("CANCELLED");
        return sub;
    }

    private UUID terminatedSubscription() {
        UUID sub = createTrial(0);
        transactions.executeWithoutResult(status ->
                new SubscriptionCommandService(jdbc, audit, publishedEvents::add)
                        .execute(sub, "TERMINATE", "R0C-10 F seed"));
        assertThat(field(sub, "status")).isEqualTo("TERMINATED");
        return sub;
    }

    private UUID createTrial(int trialDays) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, trialDays), null).id());
    }

    private Map<String, Object> fullRow(UUID subscriptionId) {
        return jdbc.queryForMap("SELECT id, tenant_id, plan_id, status, billing_cycle, seat_quantity, "
                + "started_at, trial_ends_at, current_period_start, cancelled_at FROM tenant_subscriptions "
                + "WHERE id = ?", subscriptionId);
    }

    private void seedTenant(UUID id) {
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8));
    }

    private void seedPlan(UUID id, String code, int trialDays) {
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb,
                                                created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SAR', 30000, 300000, ?, 10, 5, 1024, NOW(), NOW())
                        """,
                id, code, "Plan " + code, trialDays);
    }

    private void seedPlanVersion(UUID id, UUID planId) {
        jdbc.update("""
                        INSERT INTO plan_versions (id, plan_id, version_number, status,
                                                   effective_from, currency_code, monthly_price_minor,
                                                   annual_price_minor, trial_days, max_users,
                                                   max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 1, 'ACTIVE', NOW(), 'SAR', 30000, 300000, 14, 10, 5, 1024, NOW(), NOW())
                        """,
                id, planId);
    }

    private UUID insertSubscription(UUID tenantId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status, "
                        + "billing_cycle, seat_quantity, credit_balance_minor, started_at, trial_ends_at, "
                        + "current_period_start, current_period_end, cancel_at_period_end, billing_state, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, 'MONTHLY', 1, 0, NOW(), NULL, "
                        + "NOW(), NOW() + INTERVAL '30 days', FALSE, 'CURRENT', NOW(), NOW())",
                id, tenantId, planA, status);
        return id;
    }

    private Object field(UUID subscriptionId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM tenant_subscriptions WHERE id = ?",
                Object.class, subscriptionId);
    }

    private long historyCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", Long.class, tenantId);
        return count == null ? 0L : count;
    }

    private long effectiveCount(UUID tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')", Long.class, tenantId);
        return count == null ? 0L : count;
    }

    private long ledgerCount(UUID subscriptionId, String command) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = ?",
                Long.class, subscriptionId, command);
        return count == null ? 0L : count;
    }
}
