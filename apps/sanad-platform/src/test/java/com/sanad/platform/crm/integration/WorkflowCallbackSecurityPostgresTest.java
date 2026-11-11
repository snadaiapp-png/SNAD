package com.sanad.platform.crm.integration;

import com.sanad.platform.crm.integration.security.CallbackReplayStore;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.integration.security.WorkflowCallbackSecurity;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Callback security regression suite (PostgreSQL Direct).
 *
 * <p>Since V20261111_1 the replay store is guarded by ENABLE+FORCE RLS with
 * fail-closed tenant policies; the fixture cluster runs the chain in the
 * migration-fixture topology where {@code sanad} owns the tables and is
 * therefore bound by FORCE RLS exactly like the restricted runtime of the
 * separated topology. All direct table assertions therefore run with
 * {@code app.tenant_id} bound inside a transaction, mirroring the trusted
 * scoped path the application itself uses.</p>
 */
class WorkflowCallbackSecurityPostgresTest {

    private static final String SECRET = "crm-009-callback-security-test-secret-0123456789";
    private static JdbcTemplate jdbc;
    private static TransactionTemplate txTemplate;
    private static ServiceJwtProvider jwt;
    private static WorkflowCallbackSecurity security;

    private UUID tenantId;

    @BeforeAll
    static void setup() {
        boolean postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "WorkflowCallbackSecurityPostgresTest");
        Assumptions.assumeTrue(
                postgresAvailable,
                "PostgreSQL Direct unavailable — skipping in non-CI environment");
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        txTemplate = new TransactionTemplate(txManager);
        // The store binds the trusted tenant GUC inside its own transaction.
        CallbackReplayStore replayStore = new CallbackReplayStore(
                jdbc, txTemplate, new TenantRlsTransactionContext(jdbc));
        jwt = new ServiceJwtProvider(SECRET, "workflow-engine", "workflow-engine", 60);
        security = new WorkflowCallbackSecurity(
                jwt,
                replayStore,
                SECRET,
                "sanad-crm",
                300);
    }

    @BeforeEach
    void seedTenant() {
        tenantId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Callback Tenant', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId,
                "callback-" + tenantId.toString().substring(0, 8));
    }

    /** Count rows for the bound tenant under the fail-closed RLS contract. */
    private int countReplayRowsFor(UUID tenant) {
        AtomicInteger count = new AtomicInteger();
        txTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenant.toString());
            count.set(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_callback_replay WHERE tenant_id=?",
                    Integer.class,
                    tenant));
        });
        return count.get();
    }

    @Test
    void validCallbackConsumesJtiAndNonceOnce() {
        String correlationId = "corr-valid";
        String body = body(tenantId, correlationId);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String token = jwt.mint(tenantId, correlationId, "1.0", "sanad-crm");
        String signature = security.signForTest(timestamp, nonce, body);

        var validated = security.verify(
                "Bearer " + token, signature, timestamp, nonce, body,
                tenantId, correlationId, "1.0");

        assertThat(validated.tenantId()).isEqualTo(tenantId);
        assertThat(countReplayRowsFor(tenantId)).isEqualTo(1);
    }

    @Test
    void replayedNonceOrJtiIsRejected() {
        String correlationId = "corr-replay";
        String body = body(tenantId, correlationId);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String token = jwt.mint(tenantId, correlationId, "1.0", "sanad-crm");
        String signature = security.signForTest(timestamp, nonce, body);

        security.verify(
                "Bearer " + token, signature, timestamp, nonce, body,
                tenantId, correlationId, "1.0");

        assertThatThrownBy(() -> security.verify(
                "Bearer " + token, signature, timestamp, nonce, body,
                tenantId, correlationId, "1.0"))
                .isInstanceOf(WorkflowCallbackSecurity.CallbackSecurityException.class)
                .hasMessageContaining("CALLBACK_REPLAY_DETECTED");
    }

    @Test
    void tamperedBodyIsRejected() {
        String correlationId = "corr-tamper";
        String body = body(tenantId, correlationId);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String token = jwt.mint(tenantId, correlationId, "1.0", "sanad-crm");
        String signature = security.signForTest(timestamp, nonce, body);

        assertThatThrownBy(() -> security.verify(
                "Bearer " + token, signature, timestamp, nonce,
                body.replace("RUNNING", "COMPLETED"),
                tenantId, correlationId, "1.0"))
                .isInstanceOf(WorkflowCallbackSecurity.CallbackSecurityException.class)
                .hasMessageContaining("CALLBACK_SIGNATURE_INVALID");
    }

    @Test
    void tenantBindingMismatchIsRejected() {
        String correlationId = "corr-tenant";
        String body = body(tenantId, correlationId);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String token = jwt.mint(tenantId, correlationId, "1.0", "sanad-crm");
        String signature = security.signForTest(timestamp, nonce, body);

        assertThatThrownBy(() -> security.verify(
                "Bearer " + token, signature, timestamp, nonce, body,
                UUID.randomUUID(), correlationId, "1.0"))
                .isInstanceOf(WorkflowCallbackSecurity.CallbackSecurityException.class)
                .hasMessageContaining("CALLBACK_TENANT_MISMATCH");
    }

    @Test
    void staleTimestampIsRejected() {
        String correlationId = "corr-skew";
        String body = body(tenantId, correlationId);
        String timestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String token = jwt.mint(tenantId, correlationId, "1.0", "sanad-crm");
        String signature = security.signForTest(timestamp, nonce, body);

        assertThatThrownBy(() -> security.verify(
                "Bearer " + token, signature, timestamp, nonce, body,
                tenantId, correlationId, "1.0"))
                .isInstanceOf(WorkflowCallbackSecurity.CallbackSecurityException.class)
                .hasMessageContaining("CALLBACK_TIMESTAMP_OUT_OF_RANGE");
    }

    private static String body(UUID tenantId, String correlationId) {
        return "{\"tenantId\":\"" + tenantId +
                "\",\"correlationId\":\"" + correlationId +
                "\",\"status\":\"RUNNING\"}";
    }
}
