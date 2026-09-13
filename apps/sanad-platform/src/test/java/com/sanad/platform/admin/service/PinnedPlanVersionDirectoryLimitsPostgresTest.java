package com.sanad.platform.admin.service;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateMembershipAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateOrganizationAdminRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression: directory limits must come from the subscription's pinned
 * plan_version, not the mutable legacy saas_plans compatibility row.
 */
class PinnedPlanVersionDirectoryLimitsPostgresTest {

    private JdbcTemplate jdbc;
    private TenantDirectoryAdministrationService service;

    @BeforeEach
    void migrate() {
        String url = System.getenv().getOrDefault("PG_ACCEPTANCE_JDBC_URL",
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://127.0.0.1:5432/sanad"));
        String user = System.getenv().getOrDefault("PG_ACCEPTANCE_USERNAME",
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        String password = System.getenv().getOrDefault("PG_ACCEPTANCE_PASSWORD",
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

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
        service = new TenantDirectoryAdministrationService(
                jdbc, mock(PlatformAuditService.class));
    }

    @Test
    @DisplayName("pinned plan-version max_organizations governs directory creation")
    void pinnedVersionMaxOrganizationsIsAuthoritative() {
        Seed seed = seedSubscription(10, 1, 10, 2);
        insertOrganization(seed.tenantId(), "Existing Organization");

        var created = service.createOrganization(
                seed.tenantId(),
                new CreateOrganizationAdminRequest("Second Organization", null),
                null);

        assertThat(created.name()).isEqualTo("Second Organization");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND status <> 'ARCHIVED'",
                Integer.class, seed.tenantId());
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("pinned plan-version max_users governs directory seat creation")
    void pinnedVersionMaxUsersIsAuthoritative() {
        Seed seed = seedSubscription(1, 5, 2, 5);
        UUID organizationId = insertOrganization(seed.tenantId(), "Seat Organization");
        insertMembership(seed.tenantId(), organizationId, "first@example.com");

        var created = service.createMembership(
                seed.tenantId(),
                organizationId,
                new CreateMembershipAdminRequest("second@example.com", "Second User", "MEMBER"),
                null);

        assertThat(created.email()).isEqualTo("second@example.com");
        Integer seats = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT LOWER(email))
                FROM organization_memberships
                WHERE tenant_id = ? AND status IN ('INVITED', 'ACTIVE')
                """,
                Integer.class, seed.tenantId());
        assertThat(seats).isEqualTo(2);
    }

    private Seed seedSubscription(int legacyMaxUsers, int legacyMaxOrganizations,
                                  int pinnedMaxUsers, int pinnedMaxOrganizations) {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO tenants (
                    id, name, subdomain, status, country_code, currency_code,
                    created_at, updated_at
                ) VALUES (?, 'Limits Tenant', ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                """,
                tenantId, "limits-" + tenantId.toString().substring(0, 8));

        jdbc.update("""
                INSERT INTO saas_plans (
                    id, code, name, status, currency_code,
                    monthly_price_minor, annual_price_minor, trial_days,
                    max_users, max_organizations, storage_mb,
                    created_at, updated_at
                ) VALUES (?, ?, 'Limits Plan', 'ACTIVE', 'SAR',
                          10000, 100000, 0, ?, ?, 1024, NOW(), NOW())
                """,
                planId, "LIM-" + planId.toString().substring(0, 8),
                legacyMaxUsers, legacyMaxOrganizations);

        jdbc.update("""
                INSERT INTO plan_versions (
                    id, plan_id, version_number, status, currency_code,
                    monthly_price_minor, annual_price_minor, trial_days,
                    max_users, max_organizations, storage_mb,
                    effective_from, created_at, updated_at
                ) VALUES (?, ?, 1, 'ACTIVE', 'SAR',
                          10000, 100000, 0, ?, ?, 1024, NOW(), NOW(), NOW())
                """,
                versionId, planId, pinnedMaxUsers, pinnedMaxOrganizations);

        jdbc.update("""
                INSERT INTO tenant_subscriptions (
                    id, tenant_id, plan_id, plan_version_id, status, billing_cycle,
                    seat_quantity, credit_balance_minor, started_at,
                    current_period_start, current_period_end, cancel_at_period_end,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, NOW(),
                          NOW(), NOW() + INTERVAL '30 days', false, NOW(), NOW())
                """,
                subscriptionId, tenantId, planId, versionId);

        return new Seed(tenantId);
    }

    private UUID insertOrganization(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (
                    id, tenant_id, name, status, created_at, updated_at
                ) VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, id, tenantId, name);
        return id;
    }

    private void insertMembership(UUID tenantId, UUID organizationId, String email) {
        jdbc.update("""
                INSERT INTO organization_memberships (
                    id, tenant_id, organization_id, email, role_code, status,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'MEMBER', 'INVITED', NOW(), NOW())
                """,
                UUID.randomUUID(), tenantId, organizationId, email);
    }

    private record Seed(UUID tenantId) {
    }
}
