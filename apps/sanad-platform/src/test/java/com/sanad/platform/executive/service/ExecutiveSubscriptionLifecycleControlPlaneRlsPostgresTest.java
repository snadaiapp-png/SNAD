package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.SaasAdminDtos;
import com.sanad.platform.admin.service.SaasAdministrationService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED/GREEN regression for control-plane subscription lifecycle and
 * operating-unit management across tenants (defects D5, discovered by
 * automated UAT-07/UAT-17/UAT-18 execution, 2026-11-16).
 *
 * <p>Defect: every HTTP session binds {@code app.tenant_id} to the ACTING
 * principal's tenant, while {@code tenant_subscriptions} is fail-closed FORCE
 * RLS. Control-plane operations addressed by subscription id alone (executive
 * cancel/resume/renew, operating-unit bind, billing profiles) therefore 404ed
 * for any subscription owned by a different tenant, even for the platform
 * operator holding EXECUTIVE_MANAGE. The trusted
 * {@code platform_subscription_owner_tenant} resolver (V20261116_2) plus
 * service-layer re-scoping close the gap; authorization stays upstream.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ExecutiveSubscriptionLifecycleControlPlaneRlsPostgresTest {

    private static final UUID CONTROL_PLANE_TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /** Resolved from the migration-seeded catalog at runtime (the test database
     *  carries the STARTER plan family, not the acceptance-seed plan). */
    private UUID planId() {
        return jdbc.queryForObject(
                "SELECT id FROM saas_plans WHERE code = 'STARTER' AND status = 'ACTIVE'", UUID.class);
    }

    private UUID planVersionId() {
        return jdbc.queryForObject(
                "SELECT id FROM plan_versions WHERE plan_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1",
                UUID.class, planId());
    }

    @Autowired private SaasAdministrationService saasService;
    @Autowired private com.sanad.platform.subscription.branch.SubscriptionOperatingUnitService operatingUnitService;
    @Autowired private TenantRlsTransactionContext tenantRlsContext;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "ExecutiveSubscriptionLifecycleControlPlaneRlsPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ExecutiveSubscriptionLifecycleControlPlaneRlsPostgresTest");
    }

    @AfterEach
    void resetScope() {
        jdbc.execute("SELECT set_config('app.tenant_id', '', false)");
    }

    private void ensureTenantB() {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, TENANT_B);
        if (existing == null || existing == 0) {
            jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, 'Tenant B (RLS probe)', ?, 'ACTIVE', NOW(), NOW())",
                    TENANT_B, "tenant-b-rls-probe");
        }
    }

    private UUID createBareSubscription() {
        ensureTenantB();
        UUID id = UUID.randomUUID();
        // tenant_subscriptions is FORCE RLS: seed rows under the owner scope.
        // The disposable test database may hold ACTIVE probes from a previous
        // run; the uk_tenant_subscriptions_effective index allows only one
        // effective subscription per tenant+plan, so retire them first.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(TENANT_B);
            jdbc.update("DELETE FROM subscription_commands WHERE subscription_id IN "
                    + "(SELECT id FROM tenant_subscriptions WHERE tenant_id = ?)", TENANT_B);
            jdbc.update("DELETE FROM subscription_change_events WHERE subscription_id IN "
                    + "(SELECT id FROM tenant_subscriptions WHERE tenant_id = ?)", TENANT_B);
            jdbc.update("DELETE FROM subscription_operating_units WHERE subscription_id IN "
                    + "(SELECT id FROM tenant_subscriptions WHERE tenant_id = ?)", TENANT_B);
            jdbc.update("DELETE FROM tenant_subscriptions WHERE tenant_id = ?", TENANT_B);
            insertSubscription(id);
            return null;
        });
        return id;
    }

    private void insertSubscription(UUID id) {
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, plan_version_id, status,
                                billing_cycle, seat_quantity, credit_balance_minor, started_at, current_period_start,
                                current_period_end, cancel_at_period_end, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, ?, ?, ?, FALSE, ?, ?)
                        """,
                id, TENANT_B, planId(), planVersionId(),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plusSeconds(30 * 86400)),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    @Test
    void controlPlaneActorReadsAndCancelsAnotherTenantsSubscription() {
        UUID subTenantB = createBareSubscription();

        // Simulate the control-plane HTTP request scope: bound to the ACTING tenant.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(CONTROL_PLANE_TENANT);

            // 1) previously 404 "Subscription not found"
            var read = saasService.getSubscription(subTenantB);
            assertThat(read.id()).isEqualTo(subTenantB);
            assertThat(read.status()).isEqualTo("ACTIVE");

            // 2) previously 404 on the governed cancel path
            var cancelled = saasService.cancelSubscription(
                    subTenantB, new SaasAdminDtos.CancelSubscriptionRequest(true, "UAT cross-tenant governed cancel"), null);
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
            return null;
        });

        // subscription_commands is FORCE RLS: assert under the owner scope.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(TENANT_B);
            Integer ledgerRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_commands WHERE subscription_id = ? AND command = 'CANCEL'",
                    Integer.class, subTenantB);
            assertThat(ledgerRows).isEqualTo(1);
            return null;
        });
    }

    @Test
    void controlPlaneActorBindsOperatingUnitOnAnotherTenantsSubscription() {
        UUID subTenantB = createBareSubscription();
        UUID branchOrg = UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(TENANT_B);
            jdbc.update("INSERT INTO organizations (id, tenant_id, name, description, unit_type, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, null, 'BRANCH', 'ACTIVE', NOW(), NOW())",
                    branchOrg, TENANT_B, "UAT RLS Probe Branch " + UUID.randomUUID().toString().substring(0, 8));
            return null;
        });

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(CONTROL_PLANE_TENANT);
            // previously 404/500 under the acting-tenant RLS scope
            var bound = operatingUnitService.bind(subTenantB, branchOrg, null, null);
            assertThat(bound).isNotNull();
            return null;
        });

        // subscription_operating_units is RLS-bound: assert under the owner scope.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(TENANT_B);
            Integer boundRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM subscription_operating_units WHERE subscription_id = ? AND organization_id = ?",
                    Integer.class, subTenantB, branchOrg);
            assertThat(boundRows).isEqualTo(1);
            return null;
        });
    }
}
