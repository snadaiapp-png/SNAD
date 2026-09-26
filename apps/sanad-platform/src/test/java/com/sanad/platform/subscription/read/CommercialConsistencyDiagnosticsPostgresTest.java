package com.sanad.platform.subscription.read;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialConsistencyDiagnosticsPostgresTest {

    private static final UUID STARTER_PLAN =
            UUID.fromString("c3000000-0000-0000-0000-000000000001");

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private CommercialConsistencyDiagnosticsService service;
    private TransactionTemplate transaction;
    private TenantRlsTransactionContext rls;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CommercialConsistencyDiagnosticsPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping CommercialConsistencyDiagnosticsPostgresTest.");
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
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        rls = new TenantRlsTransactionContext(jdbc);
        service = new CommercialConsistencyDiagnosticsService(jdbc, rls);
    }

    @Test
    void globalControlPlaneScanMustSeeTenantScopedOpenCurrencyMismatchBehindForceRls() {
        UUID tenant = seedTenant("diagnostic-rls");
        UUID otherTenant = seedTenant("diagnostic-other");
        UUID subscription = seedSubscription(tenant);
        UUID invoice = seedInvoice(tenant, subscription);
        UUID run = UUID.randomUUID();

        transaction.executeWithoutResult(ignored -> {
            rls.applyForCurrentTransaction(tenant);
            jdbc.update("""
                    INSERT INTO subscription_billing_reconciliation_runs (
                        id, tenant_id, idempotency_key, mode, state,
                        started_at, completed_at, summary_metadata)
                    VALUES (?, ?, ?, 'READ_ONLY', 'COMPLETED', NOW(), NOW(), '{}'::jsonb)
                    """, run, tenant, "diagnostic-" + run);
            jdbc.update("""
                    INSERT INTO subscription_billing_reconciliation_items (
                        id, tenant_id, reconciliation_run_id, billing_invoice_id,
                        classification, expected_amount_minor, observed_amount_minor,
                        expected_currency, observed_currency, details_metadata, state, created_at)
                    VALUES (?, ?, ?, ?, 'CURRENCY_MISMATCH', 10000, 10000,
                            'SAR', 'USD', '{}'::jsonb, 'OPEN', NOW())
                    """, UUID.randomUUID(), tenant, run, invoice);
        });

        // A second tenant guarantees that the scan must re-scope the same
        // transaction instead of relying on a single ambient tenant value.
        seedSubscription(otherTenant);

        var anomalies = transaction.execute(ignored -> service.scan());

        assertThat(anomalies)
                .isNotNull()
                .anySatisfy(anomaly -> {
                    assertThat(anomaly.code()).isEqualTo("CURRENCY_MISMATCH");
                    assertThat(anomaly.tenantId()).isEqualTo(tenant);
                    assertThat(anomaly.invoiceId()).isEqualTo(invoice);
                    assertThat(anomaly.evidence()).contains("expected=SAR", "observed=USD");
                });
    }

    private UUID seedTenant(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, legal_name, subdomain, status, country_code,
                                     currency_code, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'SA', 'SAR', NOW(), NOW())
                """,
                id, slug, slug, slug + "-" + id.toString().substring(0, 6));
        return id;
    }

    private UUID seedSubscription(UUID tenantId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        jdbc.update("""
                INSERT INTO tenant_subscriptions (
                    id, tenant_id, plan_id, plan_version_id, status, billing_cycle,
                    seat_quantity, credit_balance_minor, started_at, trial_ends_at,
                    current_period_start, current_period_end, cancel_at_period_end,
                    billing_state, created_at, updated_at)
                VALUES (?, ?, ?, NULL, 'ACTIVE', 'MONTHLY', 1, 0, ?, NULL,
                        ?, ?, FALSE, 'CURRENT', ?, ?)
                """,
                id, tenantId, STARTER_PLAN,
                Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(30L * 86400L)),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedInvoice(UUID tenantId, UUID subscriptionId) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("""
                INSERT INTO billing_invoices (
                    id, tenant_id, subscription_id, invoice_number, status, currency_code,
                    subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor,
                    description, period_start, period_end, due_at, paid_at, payment_reference,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPEN', 'SAR', 10000, 0, 0, 10000, 0,
                        'Diagnostics PostgreSQL Direct acceptance', ?, ?, ?, NULL, NULL, NOW(), NOW())
                """,
                id, tenantId, subscriptionId, "DIAG-" + id,
                Timestamp.from(start), Timestamp.from(end), Timestamp.from(end));
        return id;
    }
}
