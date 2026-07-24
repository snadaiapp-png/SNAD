package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;



import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRM-009 PostgreSQL acceptance tests for the transactional outbox.
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>CLAIMED → COMPLETED clears all claim fields (claimed_at, claimed_by,
 *       claim_token, claim_expires_at are NULL).</li>
 *   <li>completed_at is NOT NULL after completion.</li>
 *   <li>Stale worker (wrong claim_token) cannot complete.</li>
 *   <li>Expired claim cannot complete.</li>
 *   <li>Wrong expected version cannot complete.</li>
 * </ul>
 */

class CrmIntegrationOutboxPostgresTest {

    
    static PostgreSQLContainer<?> POSTGRES;

    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static ObjectMapper mapper;

    private UUID tenantId;
    private UUID requestId;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for CRM-009 outbox PostgreSQL tests");

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        mapper = new ObjectMapper();
        store = new CrmIntegrationStore(jdbc, mapper);
    }

    @BeforeEach
    void seedRequest() {
        tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 0, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                requestId, tenantId, actorId, "corr-" + requestId, "caus-" + requestId,
                "idem-" + requestId, UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        store.createOutboxEvent(tenantId, requestId, "AI", "AI_REQUEST_DISPATCH",
                "idem-" + requestId, mapper.createObjectNode());
    }

    @Test
    void completedClearsAllClaimFields() {
        var claimed = store.claimNextOutboxEvent("worker-A", 60);
        assertThat(claimed).isPresent();
        CrmIntegrationStore.OutboxEvent event = claimed.get();

        store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), "worker-A");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT dispatch_status, completed_at, claimed_at, claimed_by, " +
                        "claim_token, claim_expires_at, version " +
                        "FROM crm_integration_outbox WHERE id = ?", event.id());
        assertThat(row.get("dispatch_status")).isEqualTo("COMPLETED");
        assertThat(row.get("completed_at")).isNotNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("claimed_by")).isNull();
        assertThat(row.get("claim_token")).isNull();
        assertThat(row.get("claim_expires_at")).isNull();
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(event.version() + 1);
    }

    @Test
    void staleWorkerCannotComplete() {
        var claimed = store.claimNextOutboxEvent("worker-A", 60);
        CrmIntegrationStore.OutboxEvent event = claimed.orElseThrow();

        // Wrong claim_token
        assertThatThrownBy(() -> store.completeOutboxEvent(
                event.tenantId(), event.id(), event.version(),
                UUID.randomUUID(), "worker-A"))
                .isInstanceOf(Exception.class);

        // Row should remain CLAIMED, not COMPLETED
        String status = jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE id = ?",
                String.class, event.id());
        assertThat(status).isEqualTo("CLAIMED");
    }

    @Test
    void expiredClaimCannotComplete() {
        var claimed = store.claimNextOutboxEvent("worker-A", 1);
        CrmIntegrationStore.OutboxEvent event = claimed.orElseThrow();

        // Wait for claim to expire
        try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        assertThatThrownBy(() -> store.completeOutboxEvent(
                event.tenantId(), event.id(), event.version(),
                event.claimToken(), "worker-A"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void wrongExpectedVersionCannotComplete() {
        var claimed = store.claimNextOutboxEvent("worker-A", 60);
        CrmIntegrationStore.OutboxEvent event = claimed.orElseThrow();

        assertThatThrownBy(() -> store.completeOutboxEvent(
                event.tenantId(), event.id(), event.version() + 999,
                event.claimToken(), "worker-A"))
                .isInstanceOf(Exception.class);
    }
}
