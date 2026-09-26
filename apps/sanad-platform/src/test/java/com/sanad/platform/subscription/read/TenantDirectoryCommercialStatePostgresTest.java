package com.sanad.platform.subscription.read;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantDirectoryCommercialStatePostgresTest {

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private TenantDirectoryQueryService directory;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "TenantDirectoryCommercialStatePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping TenantDirectoryCommercialStatePostgresTest.");
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
    void migrate() {
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
        directory = new TenantDirectoryQueryService(jdbc);
    }

    @Test
    void terminalLatestHistoryNeverOverridesEffectiveSubscription() {
        UUID tenant = newTenant("directory-effective");
        UUID plan = seedPlan("DIR-EFFECTIVE");
        UUID effective = insertSubscription(
                tenant, plan, "ACTIVE", "CURRENT", Instant.parse("2026-09-01T00:00:00Z"));
        insertSubscription(
                tenant, plan, "EXPIRED", "CURRENT", Instant.parse("2026-09-20T00:00:00Z"));

        var page = directory.search("directory-effective", null, null,
                0, 20, "name", "ASC");

        assertThat(page.content()).hasSize(1);
        var row = page.content().get(0);
        assertThat(row.subscriptionCount()).isEqualTo(2);
        assertThat(row.subscriptionStatus()).isEqualTo("ACTIVE");
        // The effective row is deliberately older than the terminal history row.
        assertThat(effective).isNotNull();
    }

    @Test
    void terminalOnlyHistoryHasNoCurrentSubscriptionStatus() {
        UUID tenant = newTenant("directory-terminal-only");
        UUID plan = seedPlan("DIR-TERMINAL");
        insertSubscription(
                tenant, plan, "TERMINATED", "CURRENT", Instant.parse("2026-09-20T00:00:00Z"));

        var page = directory.search("directory-terminal-only", null, null,
                0, 20, "name", "ASC");

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).subscriptionCount()).isEqualTo(1);
        assertThat(page.content().get(0).subscriptionStatus()).isNull();
    }

    private UUID newTenant(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, legal_name, subdomain, status, country_code,
                                     currency_code, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                """,
                id, slug, slug, slug + "-" + id.toString().substring(0, 6));
        return id;
    }

    private UUID seedPlan(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code,
                                        monthly_price_minor, annual_price_minor, trial_days,
                                        max_users, max_organizations, storage_mb,
                                        created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', 'SAR', 10000, 100000, 0, 10, 5, 1024, NOW(), NOW())
                """,
                id, code, code);
        return id;
    }

    private UUID insertSubscription(
            UUID tenantId,
            UUID planId,
            String status,
            String billingState,
            Instant createdAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenant_subscriptions (
                    id, tenant_id, plan_id, plan_version_id, status, billing_cycle,
                    seat_quantity, credit_balance_minor, started_at, trial_ends_at,
                    current_period_start, current_period_end, cancel_at_period_end,
                    billing_state, created_at, updated_at)
                VALUES (?, ?, ?, NULL, ?, 'MONTHLY', 1, 0, ?, NULL,
                        ?, ?, FALSE, ?, ?, ?)
                """,
                id, tenantId, planId, status,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(30L * 86400L)),
                billingState,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return id;
    }
}
