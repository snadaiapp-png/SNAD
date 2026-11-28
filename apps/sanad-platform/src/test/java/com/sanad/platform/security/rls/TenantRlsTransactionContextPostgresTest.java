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

import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for {@link TenantRlsTransactionContext} — the SIGNED
 * binding protocol (G1 F-INT-01/03/04). Verifies against a real PostgreSQL
 * chain (V20261124_1 objects + V20261124_2 enforcement):
 * <ul>
 *   <li>The protocol (slot → HMAC mint → bind) succeeds inside an active
 *       transaction and the HMAC-VALIDATED value
 *       {@code app_tenant_ctx_text()} confirms the binding.</li>
 *   <li>The trusted scope overrides any raw-GUC prestate.</li>
 *   <li>Refuses to run outside a transaction (programming error).</li>
 *   <li>Fresh transactions do not inherit previous tenant scope.</li>
 *   <li>A FORGED raw GUC without a valid mac does NOT satisfy the validator
 *       (fail-closed RT-01 contract at the application protocol level).</li>
 * </ul>
 *
 * <p>The fixture key provisioned by V20261124_2 on the migration-fixture
 * topology matches the signer installed here — mirroring the production
 * pairing of {@code platform_tenant_ctx_keys} and
 * {@code SANAD_TENANT_CTX_HMAC_KEY}.</p>
 */
@DisplayName("TenantRlsTransactionContext — PostgreSQL Direct signed-binding regression")
class TenantRlsTransactionContextPostgresTest {

    private static final String BASE_JDBC_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String JDBC_URL =
            MigrationTestSchemaSupport.getIsolatedJdbcUrl(BASE_JDBC_URL);
    /** Documented fixture key provisioned by V20261124_2 on the disposable topology. */
    private static final String FIXTURE_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private TenantRlsTransactionContext context;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "TenantRlsTransactionContextPostgresTest");
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

        // SingleConnectionDataSource so the transaction-local signed binding
        // survives across the multiple jdbc ops the TransactionTemplate
        // issues within the same transaction.
        SingleConnectionDataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, true);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        context = new TenantRlsTransactionContext(jdbc);
        // Install the signer matching the ACTIVE fixture key the chain
        // provisioned (the app-side pairing of the operator-provisioned key).
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(
                new TenantContextSigner(HexFormat.of().parseHex(FIXTURE_KEY_HEX), true), true));
    }

    @Test
    @DisplayName("inside an active transaction the SIGNED context binds and the validator confirms it")
    void insideTransactionBindsSignedContext() {
        UUID tenant = seedTenant();

        tx.executeWithoutResult(status -> {
            context.applyForCurrentTransaction(tenant);
            // The raw GUCs were set by app_bind_tenant_ctx (transaction-local).
            String applied = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            assertThat(applied).isEqualTo(tenant.toString());
            // End-to-end proof: the HMAC-VALIDATED value the enforced policies
            // consume equals the bound tenant.
            String validated = jdbc.queryForObject(
                    "SELECT public.app_tenant_ctx_text()", String.class);
            assertThat(validated).as("app_tenant_ctx_text() must confirm the signed binding")
                    .isEqualTo(tenant.toString());
            // The mac was recorded transaction-local by the database verifier.
            String mac = jdbc.queryForObject(
                    "SELECT current_setting('app.ctx_mac', true)", String.class);
            assertThat(mac).as("app.ctx_mac must be bound").isNotBlank();
        });
    }

    @Test
    @DisplayName("explicit trusted scope overrides any raw-GUC prestate")
    void explicitScopeOverridesRawGucPrestate() {
        UUID prestateTenant = seedTenant();
        UUID explicitTenant = seedTenant();
        tx.executeWithoutResult(status -> {
            // Simulate a hostile/pre-existing raw GUC value (no valid mac).
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, prestateTenant.toString());
            // The signed binding overrides the prestate with a VALIDATED value.
            context.applyForCurrentTransaction(explicitTenant);
            String applied = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            assertThat(applied).isEqualTo(explicitTenant.toString());
            String validated = jdbc.queryForObject(
                    "SELECT public.app_tenant_ctx_text()", String.class);
            assertThat(validated).isEqualTo(explicitTenant.toString());
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
    @DisplayName("a fresh transaction does not inherit the previous transaction's signed scope")
    void freshTransactionDoesNotInheritPreviousSignedScope() {
        UUID tenant = seedTenant();
        tx.executeWithoutResult(status -> context.applyForCurrentTransaction(tenant));
        // After commit, the transaction-local GUCs have been reset. A new
        // transaction starts without any binding.
        tx.executeWithoutResult(status -> {
            String current = jdbc.queryForObject(
                    "SELECT current_setting('app.tenant_id', true)", String.class);
            assertThat(current).as("tenant GUC must be unset in a fresh transaction").isNullOrEmpty();
            String validated = jdbc.queryForObject(
                    "SELECT public.app_tenant_ctx_text()", String.class);
            assertThat(validated).as("validator must return NULL on an unbound transaction").isNull();
        });
    }

    @Test
    @DisplayName("a FORGED raw GUC without a mac does NOT satisfy the validator (RT-01, fail-closed)")
    void forgedRawGucDoesNotSatisfyValidator() {
        UUID tenant = seedTenant();
        tx.executeWithoutResult(status -> {
            // Hostile session behavior: set the tenant GUC directly, no mac.
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenant.toString());
            String validated = jdbc.queryForObject(
                    "SELECT public.app_tenant_ctx_text()", String.class);
            assertThat(validated).as("forged raw GUC must NOT satisfy the HMAC validator").isNull();
        });
    }

    @Test
    @DisplayName("binding is refused (fail-closed) when no signer is configured")
    void bindingRefusedWithoutSigner() {
        TenantRlsRuntimePolicy.install(new TenantRlsRuntimePolicy.Policy(null, true));
        UUID tenant = seedTenant();
        tx.executeWithoutResult(status -> {
            assertThatThrownBy(() -> context.applyForCurrentTransaction(tenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("signed-context binding refused");
        });
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
