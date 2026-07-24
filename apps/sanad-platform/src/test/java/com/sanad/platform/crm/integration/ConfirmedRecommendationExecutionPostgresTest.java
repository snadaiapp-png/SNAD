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
 * CRM-009 PostgreSQL test: command execution worker processes events correctly.
 *
 * <p>Verifies Items 4 & 6: the execution worker claims a CONFIRMED_COMMAND_EXECUTION
 * event, creates a ledger, transitions to EXECUTING, executes the command,
 * and finalizes to EXECUTED with outbox completed.</p>
 */
class ConfirmedRecommendationExecutionPostgresTest {

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
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("ConfirmedRecommendationExecutionPostgresTest");
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

        // Create a CONFIRMED decision
        jdbc.update("INSERT INTO crm_integration_decisions " +
                        "(id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                        "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                        "created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, 'CONFIRM', ?, 'fp', 5, ?, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                decisionId, tenantId, requestId, UUID.randomUUID(),
                "idem-exec", "corr-exec");

        // Enqueue execution event
        executor.enqueueExecution(tenantId, requestId, decisionId,
                UUID.randomUUID(), "corr-exec", 0L);
    }

    @Test
    void executionWorkerCompletesFullLifecycle() {
        // Claim the execution event
        var claimed = store.claimNextOutboxEvent("test-worker", 60,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed).isPresent();

        // Process it
        executor.processSingleExecutionEvent(claimed.get());

        // Verify request reached EXECUTED
        Map<String, Object> req = jdbc.queryForMap(
                "SELECT status FROM crm_integration_requests WHERE id=?", requestId);
        assertThat(req.get("status")).isEqualTo("EXECUTED");

        // Verify decision reached EXECUTED
        Map<String, Object> dec = jdbc.queryForMap(
                "SELECT decision_status, completed_at FROM crm_integration_decisions WHERE id=?", decisionId);
        assertThat(dec.get("decision_status")).isEqualTo("EXECUTED");
        assertThat(dec.get("completed_at")).isNotNull();

        // Verify ledger reached EXECUTED
        Map<String, Object> led = jdbc.queryForMap(
                "SELECT execution_status, completed_at, command_reference " +
                        "FROM crm_integration_command_executions WHERE decision_id=?", decisionId);
        assertThat(led.get("execution_status")).isEqualTo("EXECUTED");
        assertThat(led.get("completed_at")).isNotNull();
        // Stub adapter returns "CREATE_FOLLOW_UP_ACTIVITY:<uuid>" — just verify non-null
        assertThat(led.get("command_reference")).asString().isNotNull();

        // Verify outbox event was completed and claim fields cleared
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT dispatch_status, completed_at, claim_token " +
                        "FROM crm_integration_outbox WHERE integration_request_id=?", requestId);
        assertThat(event.get("dispatch_status")).isEqualTo("COMPLETED");
        assertThat(event.get("completed_at")).isNotNull();
        assertThat(event.get("claim_token")).isNull();
    }

    @Test
    void outboxNeverCompletedWithDecisionStillExecuting() {
        // This test verifies the invariant: outbox is never completed while
        // the decision is still EXECUTING. The normal flow transitions both
        // atomically in Transaction B, so this should never happen.
        // We verify by checking that after successful execution, the decision
        // is NOT in EXECUTING state.
        var claimed = store.claimNextOutboxEvent("test-worker", 60,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed).isPresent();
        executor.processSingleExecutionEvent(claimed.get());

        String decisionStatus = jdbc.queryForObject(
                "SELECT decision_status FROM crm_integration_decisions WHERE id=?",
                String.class, decisionId);
        assertThat(decisionStatus).isNotEqualTo("EXECUTING");

        String outboxStatus = jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE integration_request_id=?",
                String.class, requestId);
        assertThat(outboxStatus).isEqualTo("COMPLETED");
    }
}
