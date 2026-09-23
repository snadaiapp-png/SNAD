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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutiveBillingQueryServicePostgresTest {

    private static final UUID STARTER_PLAN =
            UUID.fromString("c3000000-0000-0000-0000-000000000001");

    private static String isolatedUrl;
    private JdbcTemplate jdbc;
    private ExecutiveBillingQueryService service;
    private TransactionTemplate transaction;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExecutiveBillingQueryServicePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping ExecutiveBillingQueryServicePostgresTest.");
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
        service = new ExecutiveBillingQueryService(jdbc, new TenantRlsTransactionContext(jdbc));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    @Test
    void tenantScopedReadFailsClosedOnMissingFinanceEvidenceAndNeverLeaksAnotherTenant() {
        UUID tenantA = seedTenant("billing-a");
        UUID tenantB = seedTenant("billing-b");
        UUID subscriptionA = seedSubscription(tenantA);
        UUID subscriptionB = seedSubscription(tenantB);
        UUID invoiceA = seedInvoice(tenantA, subscriptionA, "INV-A", 12_000L, 2_000L);
        seedInvoice(tenantB, subscriptionB, "INV-B", 90_000L, 0L);

        List<ExecutiveBillingQueryService.BillingRow> rows = transaction.execute(
                ignored -> service.list(tenantA));

        assertThat(rows).isNotNull().hasSize(1);
        var row = rows.get(0);
        assertThat(row.id()).isEqualTo(invoiceA);
        assertThat(row.tenantId()).isEqualTo(tenantA);
        assertThat(row.invoiceNumber()).isEqualTo("INV-A");
        assertThat(row.totalMinor()).isEqualTo(12_000L);
        assertThat(row.amountPaidMinor()).isEqualTo(2_000L);
        assertThat(row.outstandingMinor()).isEqualTo(10_000L);
        assertThat(row.financeLinkId()).isNull();
        assertThat(row.financeInvoiceId()).isNull();
        assertThat(row.financeStatus()).isEqualTo("UNLINKED");
        assertThat(row.settlementState()).isEqualTo("UNKNOWN");
        assertThat(row.reconciliationState()).isEqualTo("UNRECONCILED");
        assertThat(row.accountingSourceOfTruth()).isEqualTo("FINANCE");
        assertThat(row.projectionSource()).isEqualTo("SCP_BILLING_PROJECTION");
        assertThat(rows).noneMatch(candidate -> candidate.tenantId().equals(tenantB));
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
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(30L * 86400L)),
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID seedInvoice(
            UUID tenantId,
            UUID subscriptionId,
            String invoiceNumber,
            long totalMinor,
            long amountPaidMinor
    ) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("""
                INSERT INTO billing_invoices (
                    id, tenant_id, subscription_id, invoice_number, status, currency_code,
                    subtotal_minor, credit_applied_minor, tax_minor, total_minor, amount_paid_minor,
                    description, period_start, period_end, due_at, paid_at, payment_reference,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OPEN', 'SAR', ?, 0, 0, ?, ?, ?, ?, ?, ?, NULL, NULL, NOW(), NOW())
                """,
                id, tenantId, subscriptionId, invoiceNumber,
                totalMinor, totalMinor, amountPaidMinor,
                "PostgreSQL Direct executive billing acceptance",
                Timestamp.from(start), Timestamp.from(end), Timestamp.from(end));
        return id;
    }
}
