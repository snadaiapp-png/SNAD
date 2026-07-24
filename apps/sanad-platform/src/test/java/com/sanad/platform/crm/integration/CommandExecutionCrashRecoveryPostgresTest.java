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
 * CRM-009 PostgreSQL test: command execution crash recovery.
 *
 * <p>Verifies Item 4: crash recovery semantics.
 * Scenario 1 — crash BEFORE command: the event is reclaimed by a second
 * worker and execution proceeds normally (no command was invoked).
 * Scenario 2 — crash AFTER command but BEFORE finalize: the ledger shows
 * EXECUTING but the outbox is still CLAIMED. The next worker claims the
 * event, sees the ledger in EXECUTING, and finalizes using the stub's
 * idempotent replay (returns the same command reference).</p>
 */
class CommandExecutionCrashRecoveryPostgresTest {

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
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CommandExecutionCrashRecoveryPostgresTest");
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
                "idem-crash", "corr-crash");

        executor.enqueueExecution(tenantId, requestId, decisionId,
                UUID.randomUUID(), "corr-crash", 0L);
    }

    @Test
    void crashBeforeCommandCausesSafeRetry() {
        // Claim the event but DO NOT process it (simulate crash before command)
        var claimed1 = store.claimNextOutboxEvent("test-worker-1", 1,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed1).isPresent();
        // Simulate crash — claim expires

        try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        // Second worker claims the same event (recovery)
        var claimed2 = store.claimNextOutboxEvent("test-worker-2", 60,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed2).isPresent();
        assertThat(claimed2.get().id()).isEqualTo(claimed1.get().id());

        // Process normally — should reach EXECUTED
        executor.processSingleExecutionEvent(claimed2.get());

        String reqStatus = jdbc.queryForObject(
                "SELECT status FROM crm_integration_requests WHERE id=?",
                String.class, requestId);
        assertThat(reqStatus).isEqualTo("EXECUTED");

        String outboxStatus = jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE integration_request_id=?",
                String.class, requestId);
        assertThat(outboxStatus).isEqualTo("COMPLETED");
    }

    @Test
    void executingRecoveryReachesTerminalState() {
        // Simulate crash AFTER command but BEFORE finalize:
        // 1. Claim the event
        // 2. Manually transition request and decision to EXECUTING + create ledger in EXECUTING
        // 3. Do NOT complete the outbox event (simulate crash)
        var claimed = store.claimNextOutboxEvent("crash-worker", 60,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed).isPresent();

        // Manually simulate Transaction A completing but Transaction B not
        CrmIntegrationStore.OutboxEvent event = claimed.get();
        UUID claimToken = event.claimToken();

        // Transition request to EXECUTING
        store.transitionStatus(tenantId, requestId, 0L, java.util.Set.of("CONFIRMED"), "EXECUTING");
        // Transition decision to EXECUTING
        store.transitionDecision(tenantId, decisionId, 0L, java.util.Set.of("CONFIRMED"), "EXECUTING", null, null);
        // Create ledger in EXECUTING state
        var lr = store.createExecutionLedger(tenantId, decisionId, requestId,
                UUID.randomUUID(), "CREATE_FOLLOW_UP_ACTIVITY", "exec-" + decisionId, claimToken);
        store.transitionExecutionLedger(tenantId, lr.ledger().id(), lr.ledger().version(),
                java.util.Set.of("PENDING"), "EXECUTING", null, null, null);

        // Outbox is still CLAIMED — simulate crash (do nothing, let claim expire)
        // The next worker should claim the expired event and finalize

        // Force claim expiry by setting claim_expires_at to past
        jdbc.update("UPDATE crm_integration_outbox SET claim_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' " +
                "WHERE id = ?", event.id());

        // Second worker claims and processes
        var claimed2 = store.claimNextOutboxEvent("recovery-worker", 60,
                ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
        assertThat(claimed2).isPresent();
        assertThat(claimed2.get().id()).isEqualTo(event.id());

        executor.processSingleExecutionEvent(claimed2.get());

        // Verify recovery reached a terminal state (EXECUTED, not stuck in EXECUTING)
        String reqStatus = jdbc.queryForObject(
                "SELECT status FROM crm_integration_requests WHERE id=?",
                String.class, requestId);
        assertThat(reqStatus).isIn("EXECUTED", "EXECUTION_REJECTED");

        String outboxStatus = jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE integration_request_id=?",
                String.class, requestId);
        assertThat(outboxStatus).isEqualTo("COMPLETED");

        String ledgerStatus = jdbc.queryForObject(
                "SELECT execution_status FROM crm_integration_command_executions WHERE decision_id=?",
                String.class, decisionId);
        assertThat(ledgerStatus).isIn("EXECUTED", "EXECUTION_REJECTED", "UNKNOWN_OUTCOME");
    }
}
