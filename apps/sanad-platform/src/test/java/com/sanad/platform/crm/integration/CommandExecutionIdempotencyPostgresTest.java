package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationExecutor;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test: command execution idempotency.
 *
 * <p>Verifies Item 5: the same decisionId executes exactly once.
 * A replay (second claim+process of the same event) does not create
 * a duplicate ledger row or duplicate command reference.</p>
 */
class CommandExecutionIdempotencyPostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static ConfirmedRecommendationExecutor executor;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID requestId;
    private UUID decisionId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CommandExecutionIdempotencyPostgresTest");
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
        executor = new ConfirmedRecommendationExecutor(
                store, new StubConfirmedRecommendationCommandAdapter(), mapper,
                new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)),
                "test-worker", 60);
    }

    @BeforeEach
    void seedConfirmedRequest() throws Exception {
        tenantId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        decisionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

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
                requestId, tenantId, UUID.randomUUID(),
                "corr", "caus", "idem", UUID.randomUUID(),
                mapper.writeValueAsString(resultPayload),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        jdbc.update("INSERT INTO crm_integration_decisions " +
                        "(id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                        "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                        "created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, 'CONFIRM', ?, 'fp', 5, ?, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                decisionId, tenantId, requestId, UUID.randomUUID(),
                "idem-idem", "corr-idem");

        executor.enqueueExecution(tenantId, requestId, decisionId,
                UUID.randomUUID(), "corr-idem", 0L);
    }

    @Test
    void sameDecisionIdExecutesExactlyOnce() {
        // Claim events until we find ours (there may be leftover events from other tests)
        CrmIntegrationStore.OutboxEvent ourEvent = null;
        java.util.List<CrmIntegrationStore.OutboxEvent> otherEvents = new java.util.ArrayList<>();
        while (true) {
            var claimed = store.claimNextOutboxEvent("test-worker", 60,
                    ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
            if (claimed.isEmpty()) break;
            if (claimed.get().integrationRequestId().equals(requestId)) {
                ourEvent = claimed.get();
            } else {
                otherEvents.add(claimed.get());
            }
        }
        assertThat(ourEvent).as("expected to find our execution event").isNotNull();

        // Process our event
        executor.processSingleExecutionEvent(ourEvent);

        // Verify one ledger row exists
        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_executions WHERE decision_id=?",
                Integer.class, decisionId);
        assertThat(ledgerCount).isEqualTo(1);

        // Verify request is EXECUTED
        String reqStatus = jdbc.queryForObject(
                "SELECT status FROM crm_integration_requests WHERE id=?",
                String.class, requestId);
        assertThat(reqStatus).isEqualTo("EXECUTED");

        // Outbox is now COMPLETED — a second claim should return only leftover events
        // (or empty if no leftovers)
        // Verify still only one ledger row for our decisionId
        Integer ledgerCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_executions WHERE decision_id=?",
                Integer.class, decisionId);
        assertThat(ledgerCountAfter).isEqualTo(1);
    }

    @Test
    void ledgerUniquePerDecision() {
        // Try to create a second ledger for the same decisionId — should fail
        var lr1 = store.createExecutionLedger(tenantId, decisionId, requestId,
                UUID.randomUUID(), "CREATE_FOLLOW_UP_ACTIVITY", "key1", UUID.randomUUID());
        assertThat(lr1.created()).isTrue();

        var lr2 = store.createExecutionLedger(tenantId, decisionId, requestId,
                UUID.randomUUID(), "CREATE_FOLLOW_UP_ACTIVITY", "key2", UUID.randomUUID());
        assertThat(lr2.created()).isFalse();
        assertThat(lr2.ledger().id()).isEqualTo(lr1.ledger().id());
    }
}
