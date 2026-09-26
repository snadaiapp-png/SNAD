package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class ExecutiveTenantProvisioningPostgresTest {

    @Autowired private ExecutiveTenantProvisioningService provisioning;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExecutiveTenantProvisioningPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ExecutiveTenantProvisioningPostgresTest");
    }

    @Test
    void provisionsExactlyOneTenantAndOneEffectiveSubscriptionByPlanCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String subdomain = "exec-code-" + suffix;
        long planCountBefore = count("SELECT COUNT(*) FROM saas_plans");

        var result = provisioning.provision(request(
                subdomain, "exec-code-" + suffix + "@example.test",
                "STARTER", null, "ANNUAL", 2, 14), null);

        assertThat(result.tenant().subdomain()).isEqualTo(subdomain);
        assertThat(result.tenant().status()).isEqualTo("ACTIVE");
        assertThat(result.commercialState().effectiveSubscriptionId()).isNotNull();
        assertThat(result.commercialState().effectiveSubscriptionStatus()).isIn("TRIAL", "TRIALING");
        assertThat(result.commercialState().loginAllowed()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM tenants WHERE subdomain = ?", subdomain)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ? "
                        + "AND status NOT IN ('CANCELLED','EXPIRED','TERMINATED')",
                result.tenant().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT billing_cycle FROM tenant_subscriptions WHERE tenant_id = ?",
                String.class, result.tenant().id())).isEqualTo("ANNUAL");
        assertThat(jdbc.queryForObject(
                "SELECT seat_quantity FROM tenant_subscriptions WHERE tenant_id = ?",
                Integer.class, result.tenant().id())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM saas_plans")).isEqualTo(planCountBefore);
    }

    @Test
    void resolvesExistingActivePlanById() {
        UUID starterId = jdbc.queryForObject(
                "SELECT id FROM saas_plans WHERE code = 'STARTER' AND status = 'ACTIVE'",
                UUID.class);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        var result = provisioning.provision(request(
                "exec-id-" + suffix, "exec-id-" + suffix + "@example.test",
                null, starterId, "MONTHLY", 1, 7), null);

        UUID persistedPlan = jdbc.queryForObject(
                "SELECT plan_id FROM tenant_subscriptions WHERE tenant_id = ?",
                UUID.class, result.tenant().id());
        assertThat(persistedPlan).isEqualTo(starterId);
    }

    @Test
    void defaultsToExistingStarterWithoutCreatingCatalogEntries() {
        long plansBefore = count("SELECT COUNT(*) FROM saas_plans");
        long versionsBefore = count("SELECT COUNT(*) FROM plan_versions");
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        var result = provisioning.provision(request(
                "exec-default-" + suffix, "exec-default-" + suffix + "@example.test",
                null, null, null, null, 14), null);

        assertThat(jdbc.queryForObject(
                "SELECT p.code FROM tenant_subscriptions s JOIN saas_plans p ON p.id = s.plan_id "
                        + "WHERE s.tenant_id = ?",
                String.class, result.tenant().id())).isEqualTo("STARTER");
        assertThat(count("SELECT COUNT(*) FROM saas_plans")).isEqualTo(plansBefore);
        assertThat(count("SELECT COUNT(*) FROM plan_versions")).isEqualTo(versionsBefore);
    }

    @Test
    void archivedPlanFailsBeforeAnyTenantIsPersisted() {
        UUID planId = insertPlan("ARCHIVED");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String subdomain = "exec-archived-" + suffix;

        assertThatThrownBy(() -> provisioning.provision(request(
                subdomain, "exec-archived-" + suffix + "@example.test",
                null, planId, "MONTHLY", 1, 0), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(count("SELECT COUNT(*) FROM tenants WHERE subdomain = ?", subdomain)).isZero();
    }

    @Test
    void subscriptionCreationFailureRollsBackTenantAdministratorOrganizationAndRoles() {
        UUID brokenPlan = insertPlan("ACTIVE"); // deliberately no ACTIVE plan_version
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String subdomain = "exec-rollback-" + suffix;
        String email = "exec-rollback-" + suffix + "@example.test";

        assertThatThrownBy(() -> provisioning.provision(request(
                subdomain, email, null, brokenPlan, "MONTHLY", 1, 0), null))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(count("SELECT COUNT(*) FROM tenants WHERE subdomain = ?", subdomain)).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email)).isZero();
        assertThat(count("SELECT COUNT(*) FROM tenant_subscriptions s JOIN tenants t ON t.id = s.tenant_id "
                + "WHERE t.subdomain = ?", subdomain)).isZero();
    }

    private CreateTenantRequest request(
            String subdomain,
            String adminEmail,
            String planCode,
            UUID planId,
            String billingCycle,
            Integer seats,
            Integer trialDays
    ) {
        return new CreateTenantRequest(
                "Executive " + subdomain,
                "Executive Legal " + subdomain,
                subdomain,
                "billing-" + subdomain + "@example.test",
                adminEmail,
                "Executive Admin",
                "SA",
                "ar-SA",
                "Asia/Riyadh",
                "SAR",
                trialDays,
                planCode,
                planId,
                billingCycle,
                seats,
                true);
    }

    private UUID insertPlan(String status) {
        UUID id = UUID.randomUUID();
        String code = "EXEC-" + id.toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO saas_plans (
                    id, code, name, description, status, currency_code,
                    monthly_price_minor, annual_price_minor, trial_days,
                    max_users, max_organizations, storage_mb, created_at, updated_at)
                VALUES (?, ?, ?, NULL, ?, 'SAR', 10000, 100000, 0, 10, 5, 1024, NOW(), NOW())
                """, id, code, code, status);
        return id;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
