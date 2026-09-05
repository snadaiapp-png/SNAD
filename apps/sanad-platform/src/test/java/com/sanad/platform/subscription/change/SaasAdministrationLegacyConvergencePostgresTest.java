package com.sanad.platform.subscription.change;

import com.sanad.platform.admin.api.SaasAdminDtos.ChangeSubscriptionPlanRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateSubscriptionRequest;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.module.entitlement.SubscriptionEntitlementListener;
import com.sanad.platform.subscription.item.SubscriptionItemRepository;
import com.sanad.platform.subscription.pricing.PriceRepository;
import com.sanad.platform.subscription.pricing.PriceResolver;
import com.sanad.platform.subscription.read.SubscriptionDetailService;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * R0C-RECOVERY-CHAIN STAGE-3 (R0C-4 re-certification) — legacy plan-write
 * convergence on PostgreSQL Direct.
 *
 * <p>Re-proves the divergence of the legacy writers (lost-branch hypotheses,
 * re-verified on the current tree):</p>
 * <ul>
 *   <li>{@code SaasAdministrationService.createSubscription} — non-trial
 *       creation crashes on {@code Timestamp.from(null)} (defect A) and never
 *       creates the canonical composition: no {@code plan_version_id}, no
 *       initial ACTIVE PLAN item (defect C).</li>
 *   <li>{@code changePlan(IMMEDIATE)} — moves {@code plan_id} directly,
 *       leaving the ACTIVE PLAN item on the old plan and no version anchor
 *       (reverse divergence).</li>
 *   <li>{@code renewSubscription()} pending-plan application — same direct
 *       write, items never follow.</li>
 *   <li>{@code SubscriptionDetailService} — timeline mapper uses
 *       {@code Map.of} with NULL from/to statuses (UNION literal NULLs) and
 *       NPEs on any subscription that has legacy change events (defect B).</li>
 * </ul>
 *
 * <p>Post-fix contract: every effective PLAN composition change — creation,
 * immediate change, renewal pending application, and the SCP change path —
 * flows through the single canonical authority in
 * {@link SubscriptionChangeService}, while legacy billing (proration,
 * invoices, credit), audit, entitlement events, and wire responses remain
 * byte-for-byte unchanged.</p>
 */
class SaasAdministrationLegacyConvergencePostgresTest {

    private JdbcTemplate jdbc;
    private SaasAdministrationService legacy;
    private SubscriptionDetailService detail;
    private PlatformAuditService audit;
    private List<Object> publishedEvents;
    private TransactionTemplate transactions;

    private UUID tenant;
    private UUID otherTenant;
    private UUID planA;
    private UUID planB;
    private UUID planDraftOnly;
    private UUID versionA;
    private UUID versionB;
    private UUID versionB2;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SaasAdministrationLegacyConvergencePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping SaasAdministrationLegacyConvergencePostgresTest.");
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
        publishedEvents = new ArrayList<>();
        legacy = new SaasAdministrationService(jdbc, audit, publishedEvents::add, null);
        detail = new SubscriptionDetailService(jdbc);

        tenant = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        planA = UUID.randomUUID();
        planB = UUID.randomUUID();
        planDraftOnly = UUID.randomUUID();
        versionA = UUID.randomUUID();
        versionB = UUID.randomUUID();
        versionB2 = UUID.randomUUID();

        seedTenant(tenant);
        seedTenant(otherTenant);
        seedPlan(planA, "R0C4-A", 30000L);
        seedPlan(planB, "R0C4-B", 90000L);
        seedPlan(planDraftOnly, "R0C4-DRAFT-ONLY", 50000L);
        seedPlanVersion(versionA, planA, 1, "ACTIVE", 30000L);
        seedPlanVersion(versionB, planB, 2, "ACTIVE", 90000L);
        // Draft-only plan: no ACTIVE version at all.
        seedPlanVersion(UUID.randomUUID(), planDraftOnly, 1, "DRAFT", 50000L);
        // SCP resolver price for the canonical-path regression test.
        jdbc.update("""
                        INSERT INTO prices (id, plan_version_id, price_model, country_code, currency_code,
                                            billing_interval, base_amount_minor, effective_from,
                                            created_at, updated_at)
                        VALUES (?, ?, 'FLAT', 'SA', 'SAR', 'MONTHLY', 90000, NOW() - INTERVAL '1 hour', NOW(), NOW())
                        """,
                UUID.randomUUID(), versionB);
    }

    // ---------------------------------------------------------------
    // Seeding helpers
    // ---------------------------------------------------------------

    private void seedTenant(UUID id) {
        jdbc.update("""
                        INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                             created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                        """,
                id, "Tenant " + id, "t-" + id.toString().substring(0, 8));
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

    private UUID createSubscription(UUID tenantId, UUID planId, Integer trialDays) {
        return transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenantId, planId, "MONTHLY", 2, trialDays), null).id());
    }

    private void changePlanImmediate(UUID subscriptionId, UUID targetPlanId) {
        transactions.executeWithoutResult(status -> legacy.changePlan(subscriptionId,
                new ChangeSubscriptionPlanRequest(targetPlanId, "MONTHLY", "IMMEDIATE",
                        "convergence test"), null));
    }

    private Map<String, Object> activePlanItem(UUID subscriptionId) {
        return jdbc.queryForMap(
                "SELECT plan_id, plan_version_id, quantity, unit_amount_minor, currency_code "
                        + "FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                subscriptionId);
    }

    private long activePlanItemCount(UUID subscriptionId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                        + "AND item_type = 'PLAN' AND status = 'ACTIVE'",
                Long.class, subscriptionId);
        return count == null ? 0L : count;
    }

    private Map<String, Object> anchors(UUID subscriptionId) {
        return jdbc.queryForMap(
                "SELECT plan_id, plan_version_id, pending_plan_id FROM tenant_subscriptions WHERE id = ?",
                subscriptionId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    // ---------------------------------------------------------------
    // Defect A + C — non-trial create: NPE + missing canonical composition
    // ---------------------------------------------------------------

    @Test
    @DisplayName("non-trial create succeeds null-safely and births the canonical composition")
    void nonTrialCreateSucceedsWithCanonicalComposition() {
        var response = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 0), null));

        // Legacy wire response unchanged in shape and semantics.
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.trialEndsAt()).isNull();
        assertThat(response.planId()).isEqualTo(planA);
        assertThat(response.billingCycle()).isEqualTo("MONTHLY");
        assertThat(response.seatQuantity()).isEqualTo(2);

        // Canonical composition: version anchor + exactly one ACTIVE PLAN item.
        assertThat(activePlanItemCount(response.id())).isEqualTo(1L);
        Map<String, Object> item = activePlanItem(response.id());
        assertThat(item.get("plan_id")).isEqualTo(planA);
        assertThat(item.get("plan_version_id")).isEqualTo(versionA);
        assertThat(item.get("unit_amount_minor")).isEqualTo(30000L);
        assertThat(item.get("currency_code")).isEqualTo("SAR");
        assertThat(anchors(response.id()).get("plan_version_id")).isEqualTo(versionA);

        // Legacy billing semantics preserved: initial recurring invoice issued
        // (30000 * 2 seats), one change event, one audit, one entitlement event.
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?",
                response.id())).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ? "
                + "AND subtotal_minor = 60000", response.id())).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                + "AND action = 'SUBSCRIPTION.CREATED'", response.id())).isEqualTo(1L);
        verify(audit, times(1)).success(any(), eq(tenant), eq("SUBSCRIPTION.CREATE"),
                eq("TENANT_SUBSCRIPTION"), eq(response.id().toString()), any(), any(), any());
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionActivatedEvent)
                .count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("trial create still works and also births the canonical composition")
    void trialCreateBirthsCanonicalComposition() {
        var response = transactions.execute(status -> legacy.createSubscription(
                new CreateSubscriptionRequest(tenant, planA, "MONTHLY", 2, 14), null));

        assertThat(response.status()).isEqualTo("TRIALING");
        assertThat(response.trialEndsAt()).isNotNull();

        assertThat(activePlanItemCount(response.id())).isEqualTo(1L);
        Map<String, Object> item = activePlanItem(response.id());
        assertThat(item.get("plan_version_id")).isEqualTo(versionA);
        assertThat(anchors(response.id()).get("plan_version_id")).isEqualTo(versionA);

        // Trial behavior unchanged: no initial invoice, entitlement + audit fired.
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ?",
                response.id())).isZero();
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionActivatedEvent)
                .count()).isEqualTo(1L);
        verify(audit, times(1)).success(any(), eq(tenant), eq("SUBSCRIPTION.CREATE"),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create fails closed when the plan has no ACTIVE version — zero partial rows")
    void createFailsClosedWithoutActivePlanVersion() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> legacy.createSubscription(
                                new CreateSubscriptionRequest(tenant, planDraftOnly, "MONTHLY", 2, 14), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no ACTIVE version");

        assertThat(count("SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?", tenant)).isZero();
        assertThat(count("SELECT COUNT(*) FROM subscription_items WHERE tenant_id = ?", tenant)).isZero();
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE tenant_id = ?", tenant)).isZero();
    }

    // ---------------------------------------------------------------
    // changePlan(IMMEDIATE) convergence
    // ---------------------------------------------------------------

    @Test
    @DisplayName("legacy IMMEDIATE plan change converges the canonical composition with billing untouched")
    void legacyImmediatePlanChangeConvergesComposition() {
        UUID subscriptionId = createSubscription(tenant, planA, 14);

        changePlanImmediate(subscriptionId, planB);

        // Canonical composition: old item cancelled, exactly one ACTIVE PLAN
        // item on plan B / version B at the legacy price.
        assertThat(activePlanItemCount(subscriptionId)).isEqualTo(1L);
        Map<String, Object> item = activePlanItem(subscriptionId);
        assertThat(item.get("plan_id")).isEqualTo(planB);
        assertThat(item.get("plan_version_id")).isEqualTo(versionB);
        assertThat(item.get("unit_amount_minor")).isEqualTo(90000L);
        assertThat(count("SELECT COUNT(*) FROM subscription_items WHERE subscription_id = ? "
                + "AND item_type = 'PLAN' AND status = 'CANCELLED'", subscriptionId)).isEqualTo(1L);

        // Anchors converged; pending cleared.
        Map<String, Object> anchorRow = anchors(subscriptionId);
        assertThat(anchorRow.get("plan_id")).isEqualTo(planB);
        assertThat(anchorRow.get("plan_version_id")).isEqualTo(versionB);
        assertThat(anchorRow.get("pending_plan_id")).isNull();

        // Command ledger written by the canonical authority.
        assertThat(count("SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? "
                + "AND command = 'PLAN_CHANGE'", subscriptionId)).isEqualTo(1L);

        // Legacy billing/audit/event semantics preserved: prorated upgrade
        // invoice issued, PLAN.CHANGE.APPLIED event, single audit, single
        // entitlement event for the change.
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ? "
                + "AND description = 'Prorated immediate plan upgrade'", subscriptionId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                + "AND action = 'PLAN.CHANGE.APPLIED'", subscriptionId)).isEqualTo(1L);
        verify(audit, times(1)).success(any(), eq(tenant), eq("SUBSCRIPTION.PLAN.CHANGE"),
                eq("TENANT_SUBSCRIPTION"), eq(subscriptionId.toString()), any(), any(), any());
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionPlanChangedEvent)
                .count()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // NEXT_CYCLE + renewal pending application
    // ---------------------------------------------------------------

    @Test
    @DisplayName("legacy renewal applies the pending plan through the canonical authority")
    void legacyRenewalAppliesPendingPlanConvergence() {
        UUID subscriptionId = createSubscription(tenant, planA, 14);

        transactions.executeWithoutResult(status -> legacy.changePlan(subscriptionId,
                new ChangeSubscriptionPlanRequest(planB, "MONTHLY", "NEXT_CYCLE", "scheduled"), null));
        // Scheduling itself must not move the composition.
        assertThat(activePlanItemCount(subscriptionId)).isEqualTo(1L);
        assertThat(activePlanItem(subscriptionId).get("plan_id")).isEqualTo(planA);
        assertThat(anchors(subscriptionId).get("pending_plan_id")).isEqualTo(planB);

        transactions.executeWithoutResult(status -> legacy.renewSubscription(subscriptionId, null));

        // Renewal applied the pending plan via the canonical authority.
        assertThat(activePlanItemCount(subscriptionId)).isEqualTo(1L);
        Map<String, Object> item = activePlanItem(subscriptionId);
        assertThat(item.get("plan_id")).isEqualTo(planB);
        assertThat(item.get("plan_version_id")).isEqualTo(versionB);
        Map<String, Object> anchorRow = anchors(subscriptionId);
        assertThat(anchorRow.get("plan_id")).isEqualTo(planB);
        assertThat(anchorRow.get("plan_version_id")).isEqualTo(versionB);
        assertThat(anchorRow.get("pending_plan_id")).isNull();

        // Command ledger + legacy events + renewal invoice + entitlement counts.
        assertThat(count("SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? "
                + "AND command = 'PLAN_CHANGE'", subscriptionId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                + "AND action = 'PLAN.CHANGE.SCHEDULED'", subscriptionId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM subscription_change_events WHERE subscription_id = ? "
                + "AND action = 'SUBSCRIPTION.RENEWED'", subscriptionId)).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ? "
                + "AND description = 'Subscription renewal'", subscriptionId)).isEqualTo(1L);
        // One entitlement event at schedule time (legacy behavior), none extra at renewal.
        assertThat(publishedEvents.stream()
                .filter(e -> e instanceof SubscriptionEntitlementListener.SubscriptionPlanChangedEvent)
                .count()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------
    // Tenant isolation through the converged legacy paths
    // ---------------------------------------------------------------

    @Test
    @DisplayName("legacy plan change on tenant A leaves tenant B untouched")
    void legacyChangePlanIsTenantScoped() {
        UUID subscriptionA = createSubscription(tenant, planA, 14);
        UUID subscriptionB = createSubscription(otherTenant, planA, 14);

        changePlanImmediate(subscriptionA, planB);

        assertThat(activePlanItemCount(subscriptionB)).isEqualTo(1L);
        assertThat(activePlanItem(subscriptionB).get("plan_id")).isEqualTo(planA);
        assertThat(anchors(subscriptionB).get("plan_id")).isEqualTo(planA);
        assertThat(count("SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ?",
                subscriptionB)).isZero();
        assertThat(count("SELECT COUNT(*) FROM billing_invoices WHERE subscription_id = ? "
                + "AND description = 'Prorated immediate plan upgrade'", subscriptionB)).isZero();
    }

    // ---------------------------------------------------------------
    // Defect B — detail timeline null-safety
    // ---------------------------------------------------------------

    @Test
    @DisplayName("detail timeline survives legacy change events with NULL from/to statuses")
    void detailTimelineSurvivesLegacyEvents() {
        UUID subscriptionId = createSubscription(tenant, planA, 14);
        changePlanImmediate(subscriptionId, planB);

        var result = detail.detail(subscriptionId);

        List<Map<String, Object>> changes = result.changes();
        assertThat(changes).isNotEmpty();
        // Legacy EVENT rows carry NULL from/to statuses and must not crash Map.of.
        assertThat(changes.stream().anyMatch(m -> "EVENT".equals(m.get("source"))
                && m.get("fromStatus") == null && m.get("toStatus") == null)).isTrue();
        // COMMAND ledger rows carry real statuses.
        assertThat(changes.stream().anyMatch(m -> "COMMAND".equals(m.get("source"))
                && m.get("fromStatus") != null)).isTrue();
    }

    // ---------------------------------------------------------------
    // SCP canonical path regression — both authorities must agree
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SCP canonical change path still works alongside the converged legacy engine")
    void scpCanonicalPathUnchanged() {
        UUID subscriptionId = createSubscription(tenant, planA, 14);
        SubscriptionChangeService canonical = new SubscriptionChangeService(
                jdbc, new SubscriptionItemRepository(jdbc),
                new PriceResolver(new PriceRepository(jdbc)));

        transactions.executeWithoutResult(status ->
                canonical.execute(subscriptionId, versionB, "SA", "scp change", null, null));

        assertThat(activePlanItemCount(subscriptionId)).isEqualTo(1L);
        assertThat(activePlanItem(subscriptionId).get("plan_version_id")).isEqualTo(versionB);
        assertThat(anchors(subscriptionId).get("plan_id")).isEqualTo(planB);
        assertThat(anchors(subscriptionId).get("plan_version_id")).isEqualTo(versionB);
    }
}
