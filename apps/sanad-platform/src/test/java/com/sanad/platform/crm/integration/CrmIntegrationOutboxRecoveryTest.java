package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;



import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test for outbox recovery semantics.
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>An expired CLAIM is recoverable — a new claim attempt re-claims it
 *       with a new claim_token and incremented attempt_count.</li>
 *   <li>A COMPLETED event is NOT re-claimable.</li>
 * </ul>
 */

class CrmIntegrationOutboxRecoveryTest {

    
    static PostgreSQLContainer<?> POSTGRES;

    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private UUID tenantId;
    private UUID requestId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CrmIntegrationOutboxRecoveryTest");
        Assumptions.assumeTrue(docker, "Docker unavailable in local development — skipping in non-CI environment");

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
        store = new CrmIntegrationStore(jdbc, new ObjectMapper());
    }

    @BeforeEach
    void seedRequest() {
        tenantId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 0, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                requestId, tenantId, UUID.randomUUID(),
                "corr-" + requestId, "caus-" + requestId,
                "idem-" + requestId, UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        store.createOutboxEvent(tenantId, requestId, "AI", "AI_REQUEST_DISPATCH",
                "idem-" + requestId, new ObjectMapper().createObjectNode());
    }

    @Test
    void expiredClaimIsRecoverable() {
        var first = store.claimNextOutboxEvent("worker-1", 1);
        assertThat(first).isPresent();
        CrmIntegrationStore.OutboxEvent firstEvent = first.get();
        assertThat(firstEvent.attemptCount()).isEqualTo(1);

        try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        var second = store.claimNextOutboxEvent("worker-2", 60);
        assertThat(second).isPresent();
        CrmIntegrationStore.OutboxEvent secondEvent = second.get();
        assertThat(secondEvent.id()).isEqualTo(firstEvent.id());
        assertThat(secondEvent.attemptCount()).isEqualTo(2);
        assertThat(secondEvent.claimToken()).isNotEqualTo(firstEvent.claimToken());
        assertThat(secondEvent.version()).isEqualTo(firstEvent.version() + 1);
    }

    @Test
    void completedEventIsNotReclaimable() {
        var claimed = store.claimNextOutboxEvent("worker-1", 60);
        CrmIntegrationStore.OutboxEvent event = claimed.orElseThrow();
        store.completeOutboxEvent(event.tenantId(), event.id(), event.version(),
                event.claimToken(), "worker-1");

        var reclaim = store.claimNextOutboxEvent("worker-2", 60);
        assertThat(reclaim).isEmpty();
    }
}
