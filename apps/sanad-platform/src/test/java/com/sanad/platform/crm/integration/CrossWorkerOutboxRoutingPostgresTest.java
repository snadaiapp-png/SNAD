package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationExecutor;
import com.sanad.platform.crm.integration.application.CrmIntegrationOutboxWorker;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test: cross-worker outbox routing.
 *
 * <p>Verifies Item 2: the AI worker never claims a CONFIRMED_COMMAND_EXECUTION
 * event, and the command worker never claims an AI_REQUEST_DISPATCH event.
 * Event-type-filtered claim prevents a worker from claiming an event it
 * cannot handle.</p>
 */
class CrossWorkerOutboxRoutingPostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID aiRequestId;
    private UUID cmdRequestId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CrossWorkerOutboxRoutingPostgresTest");
        Assumptions.assumeTrue(docker, "Docker unavailable in local development — skipping in non-CI environment");

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        store = new CrmIntegrationStore(jdbc, mapper);
    }

    @BeforeEach
    void seedTwoEvents() throws Exception {
        tenantId = UUID.randomUUID();
        aiRequestId = UUID.randomUUID();
        cmdRequestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Seed AI request + AI_REQUEST_DISPATCH event
        jdbc.update("INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 0, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                aiRequestId, tenantId, UUID.randomUUID(),
                "corr-ai", "caus-ai", "idem-ai", UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        ObjectNode aiPayload = mapper.createObjectNode();
        aiPayload.put("capability", "CUSTOMER_SUMMARY");
        store.createOutboxEvent(tenantId, aiRequestId, "AI", "AI_REQUEST_DISPATCH", "idem-ai", aiPayload);

        // Seed command request + CONFIRMED_COMMAND_EXECUTION event
        ObjectNode resultPayload = mapper.createObjectNode();
        resultPayload.put("actionCode", "CREATE_FOLLOW_UP_ACTIVITY");
        jdbc.update("INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, result_payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 5, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), CAST(? AS jsonb), " +
                        "'CONFIRMED', ?, ?, ?, ?, 0)",
                cmdRequestId, tenantId, UUID.randomUUID(),
                "corr-cmd", "caus-cmd", "idem-cmd", UUID.randomUUID(),
                mapper.writeValueAsString(resultPayload),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        ObjectNode cmdPayload = mapper.createObjectNode();
        cmdPayload.put("decisionId", UUID.randomUUID().toString());
        cmdPayload.put("actorId", UUID.randomUUID().toString());
        cmdPayload.put("correlationId", "corr-cmd");
        cmdPayload.put("actionCode", "CREATE_FOLLOW_UP_ACTIVITY");
        cmdPayload.put("expectedIntegrationVersion", 0);
        store.createOutboxEvent(tenantId, cmdRequestId, "AI", "CONFIRMED_COMMAND_EXECUTION",
                "exec-" + UUID.randomUUID(), cmdPayload);
    }

    @Test
    void aiWorkerNeverClaimsCommandEvent() {
        // AI worker claims only AI_REQUEST_DISPATCH
        Optional<CrmIntegrationStore.OutboxEvent> claimed = store.claimNextOutboxEvent(
                "ai-worker", 60, CrmIntegrationOutboxWorker.ACCEPTED_EVENT_TYPES);
        assertThat(claimed).isPresent();
        assertThat(claimed.get().eventType()).isEqualTo("AI_REQUEST_DISPATCH");
        assertThat(claimed.get().integrationRequestId()).isEqualTo(aiRequestId);

        // Second claim by AI worker should return empty (command event is not claimable)
        Optional<CrmIntegrationStore.OutboxEvent> claimed2 = store.claimNextOutboxEvent(
                "ai-worker", 60, CrmIntegrationOutboxWorker.ACCEPTED_EVENT_TYPES);
        assertThat(claimed2).isEmpty();
    }

    @Test
    void commandWorkerNeverClaimsAiEvent() {
        // Command worker claims only CONFIRMED_COMMAND_EXECUTION
        Optional<CrmIntegrationStore.OutboxEvent> claimed = store.claimNextOutboxEvent(
                "cmd-worker", 60, ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed).isPresent();
        assertThat(claimed.get().eventType()).isEqualTo("CONFIRMED_COMMAND_EXECUTION");
        assertThat(claimed.get().integrationRequestId()).isEqualTo(cmdRequestId);

        // Second claim by command worker should return empty (AI event is not claimable)
        Optional<CrmIntegrationStore.OutboxEvent> claimed2 = store.claimNextOutboxEvent(
                "cmd-worker", 60, ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed2).isEmpty();
    }
}
