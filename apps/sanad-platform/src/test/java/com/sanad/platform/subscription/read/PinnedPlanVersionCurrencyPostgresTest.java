package com.sanad.platform.subscription.read;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: read models must render the currency of the subscription's
 * pinned plan version, not the mutable legacy saas_plans compatibility row.
 */
class PinnedPlanVersionCurrencyPostgresTest {

    private JdbcTemplate jdbc;
    private SubscriptionGridQueryService gridService;
    private SubscriptionDetailService detailService;

    @BeforeEach
    void migrate() {
        String baseUrl = System.getenv().getOrDefault("PG_ACCEPTANCE_JDBC_URL",
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://127.0.0.1:5432/sanad"));
        String user = System.getenv().getOrDefault("PG_ACCEPTANCE_USERNAME",
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"));
        String password = System.getenv().getOrDefault("PG_ACCEPTANCE_PASSWORD",
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

        MigrationTestSchemaSupport.ensureDatabase(baseUrl, user, password);
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(baseUrl);

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
        gridService = new SubscriptionGridQueryService(jdbc);
        detailService = new SubscriptionDetailService(jdbc);
    }

    @Test
    @DisplayName("pinned plan-version currency is authoritative in grid and detail")
    void pinnedVersionCurrencyIsAuthoritative() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, country_code, currency_code,
                                     created_at, updated_at)
                VALUES (?, 'Currency Tenant', ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                """, tenantId, "currency-" + tenantId.toString().substring(0, 8));

        jdbc.update("""
                INSERT INTO saas_plans (id, code, name, status, currency_code,
                                        monthly_price_minor, annual_price_minor, trial_days,
                                        max_users, max_organizations, storage_mb,
                                        created_at, updated_at)
                VALUES (?, ?, 'Currency Plan', 'ACTIVE', 'SAR',
                        30000, 300000, 0, 10, 5, 1024, NOW(), NOW())
                """, planId, "CUR-" + planId.toString().substring(0, 8));

        jdbc.update("""
                INSERT INTO plan_versions (id, plan_id, version_number, status, currency_code,
                                           monthly_price_minor, annual_price_minor, trial_days,
                                           max_users, max_organizations, storage_mb,
                                           effective_from, created_at, updated_at)
                VALUES (?, ?, 1, 'ACTIVE', 'USD',
                        12345, 123450, 0, 10, 5, 1024, NOW(), NOW(), NOW())
                """, versionId, planId);

        jdbc.update("""
                INSERT INTO tenant_subscriptions (
                    id, tenant_id, plan_id, plan_version_id, status, billing_cycle,
                    seat_quantity, credit_balance_minor, started_at,
                    current_period_start, current_period_end, cancel_at_period_end,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0, NOW(),
                          NOW(), NOW() + INTERVAL '30 days', false, NOW(), NOW())
                """, subscriptionId, tenantId, planId, versionId);

        SubscriptionGridQueryService.SubscriptionRow gridRow = gridService.search(
                        tenantId, "ACTIVE", null, null, false,
                        0, 20, "created_at", "DESC")
                .content().stream()
                .filter(row -> subscriptionId.equals(row.id()))
                .findFirst()
                .orElseThrow();

        assertThat(gridRow.monthlyPriceMinor()).isEqualTo(12345L);
        assertThat(gridRow.currencyCode()).isEqualTo("USD");

        SubscriptionDetailService.SubscriptionDetail detail = detailService.detail(subscriptionId);
        assertThat(detail.overview().get("currencyCode")).isEqualTo("USD");
    }
}
