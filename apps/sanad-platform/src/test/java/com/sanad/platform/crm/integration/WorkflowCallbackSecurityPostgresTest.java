package com.sanad.platform.crm.integration;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.security.CallbackReplayStore;
import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import com.sanad.platform.crm.integration.security.WorkflowCallbackSecurity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowCallbackSecurityPostgresTest {

    private static final String SECRET = "crm-009-callback-security-test-secret-0123456789";
    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static ServiceJwtProvider jwt;
    private static WorkflowCallbackSecurity security;

    private UUID tenantId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip(
                "WorkflowCallbackSecurityPostgresTest");
        Assumptions.assumeTrue(
                docker,
                "Docker unavailable in local development — skipping in non-CI environment");
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jwt = new ServiceJwtProvider(SECRET, "workflow-engine", "workflow-engine", 60);
        security = new WorkflowCallbackSecurity(
                jwt,
                new CallbackReplayStore(jdbc),
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
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_callback_replay WHERE tenant_id=?",
                Integer.class,
                tenantId)).isEqualTo(1);
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
