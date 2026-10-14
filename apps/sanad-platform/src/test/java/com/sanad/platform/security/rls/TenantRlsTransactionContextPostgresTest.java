package com.sanad.platform.security.rls;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for {@link TenantRlsTransactionContext}.
 *
 * <p>Verifies the trusted-tenant GUC application contract:
 * <ul>
 *   <li>Applies the GUC inside an active transaction.</li>
 *   <li>Explicit scope overrides any lazy SecurityContext tenant.</li>
 *   <li>Refuses to run outside a transaction (programming error).</li>
 *   <li>Fresh transactions do not inherit previous tenant scope.</li>
 * </ul>
 */
@DisplayName("TenantRlsTransactionContext — PostgreSQL Direct regression")
class TenantRlsTransactionContextPostgresTest {

    private static final String BASE_JDBC_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String JDBC_URL =
            MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_JDBC_URL);

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private TenantRlsTransactionContext context;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "TenantRlsTransactionContextPostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping TenantRlsTransactionContextPostgresTest.");
        MigrationTestSchemaSupport.ensureDatabase(BASE_JDBC_URL, USERNAME, PASSWORD);
    }

    @BeforeEach
    void migrateSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        // SingleConnectionDataSource so SET LOCAL app.tenant_id inside a
        // Spring transaction survives across the multiple jdbc ops the
        // TransactionTemplate issues within the same transaction.
        SingleConnectionDataSource physical = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, true);
        physical.setDriverClassName("org.postgresql.Driver");
        TenantRlsDataSource ds = new TenantRlsDataSource(physical);
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        context = new TenantRlsTransactionContext(jdbc);
    }

    @Test
    @DisplayName("inside an active transaction the trusted tenant GUC is applied")
    void insideTransactionAppliesTrustedTenantScope() {
        UUID tenant = seedTenant();

        tx.executeWithoutResult(status -> {
            context.applyForCurrentTransaction(tenant);
            String applied = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            assertThat(applied).isEqualTo(tenant.toString());
        });
    }

    @Test
    @DisplayName("explicit trusted scope overrides any lazy SecurityContext tenant")
    void explicitScopeOverridesLazySecurityContextTenant() {
        UUID lazyTenant = seedTenant();
        UUID explicitTenant = seedTenant();
        // Simulate a lazy SecurityContext tenant by setting the GUC to a
        // different value BEFORE invoking the trusted scope.
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, lazyTenant.toString());
            // The trusted context overrides the previous value.
            context.applyForCurrentTransaction(explicitTenant);
            String applied = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            assertThat(applied).isEqualTo(explicitTenant.toString());
        });
    }

    @Test
    @DisplayName("calling applyForCurrentTransaction outside a transaction throws IllegalState")
    void outsideTransactionThrowsIllegalState() {
        UUID tenant = seedTenant();
        assertThatThrownBy(() -> context.applyForCurrentTransaction(tenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside a Spring-managed transaction");
    }

    @Test
    @DisplayName("a fresh transaction does not inherit the previous transaction's tenant scope")
    void freshTransactionDoesNotInheritPreviousTenantScope() {
        UUID tenant = seedTenant();
        tx.executeWithoutResult(status -> context.applyForCurrentTransaction(tenant));
        // After commit, SET LOCAL has been reset. A new transaction
        // starts without any GUC value.
        tx.executeWithoutResult(status -> {
            String current = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            // The GUC should be empty (NULL) in the fresh transaction.
            assertThat(current).isNullOrEmpty();
        });
    }

    @Test
    @DisplayName("RLS probe isolates tenant rows and denies access when tenant scope is missing")
    void rlsProbeEnforcesTenantIsolationAndMissingScopeFailsClosed() {
        jdbc.execute("CREATE TABLE g1_auth_rls_probe (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, payload TEXT NOT NULL)");
        jdbc.execute("ALTER TABLE g1_auth_rls_probe ENABLE ROW LEVEL SECURITY");
        jdbc.execute("ALTER TABLE g1_auth_rls_probe FORCE ROW LEVEL SECURITY");
        jdbc.execute("CREATE POLICY g1_auth_rls_probe_tenant_policy ON g1_auth_rls_probe "
                + "USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid) "
                + "WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)");

        // The probe table is self-contained: use distinct fixture UUIDs so
        // the test does not depend on or mutate the public.tenants catalog.
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID rowA = UUID.randomUUID();
        UUID rowB = UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            try (TenantRlsExecutionContext.Scope ignored = TenantRlsExecutionContext.open(tenantA)) {
                jdbc.update("INSERT INTO g1_auth_rls_probe(id, tenant_id, payload) VALUES (?, ?, ?)",
                        rowA, tenantA, "tenant-a");
            }
        });
        tx.executeWithoutResult(status -> {
            try (TenantRlsExecutionContext.Scope ignored = TenantRlsExecutionContext.open(tenantB)) {
                jdbc.update("INSERT INTO g1_auth_rls_probe(id, tenant_id, payload) VALUES (?, ?, ?)",
                        rowB, tenantB, "tenant-b");
            }
        });

        tx.executeWithoutResult(status -> {
            try (TenantRlsExecutionContext.Scope ignored = TenantRlsExecutionContext.open(tenantA)) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM g1_auth_rls_probe", Long.class))
                        .isEqualTo(1L);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM g1_auth_rls_probe WHERE tenant_id = ?",
                        Long.class, tenantB)).isZero();
            }
        });

        // No scope installs an empty GUC; nullif() makes the policy evaluate
        // to NULL (deny), rather than interpreting an unset GUC permissively.
        tx.executeWithoutResult(status ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM g1_auth_rls_probe", Long.class))
                        .isZero());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            try (TenantRlsExecutionContext.Scope ignored = TenantRlsExecutionContext.open(tenantA)) {
                jdbc.update("INSERT INTO g1_auth_rls_probe(id, tenant_id, payload) VALUES (?, ?, ?)",
                        UUID.randomUUID(), tenantB, "cross-tenant-write");
            }
        })).isNotNull();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, "tx-rls-" + tenantId.toString().substring(0, 8),
                "tx-" + tenantId.toString().substring(0, 8));
        return tenantId;
    }
}
