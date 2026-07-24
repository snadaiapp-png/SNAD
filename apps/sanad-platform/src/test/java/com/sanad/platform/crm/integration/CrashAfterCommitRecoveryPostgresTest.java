package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.activity.application.ActivityUseCases;
import com.sanad.platform.crm.activity.infrastructure.JdbcActivityRepository;
import com.sanad.platform.crm.integration.application.AfterCommandCommitFaultInjector;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationExecutor;
import com.sanad.platform.crm.integration.application.CreateFollowUpActivityCommandAdapter;
import com.sanad.platform.crm.integration.application.FaultInjectedException;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test: crash-after-side-effect recovery with real adapters.
 *
 * <p>Verifies Item 4: when a worker crashes after the CRM command commits
 * but before Transaction B (finalization), the next worker reclaims the
 * event and uses findExisting to recover without duplicating the artifact.</p>
 *
 * <p>Uses the REAL CreateFollowUpActivityCommandAdapter (not stub) to
 * verify that the activity is created exactly once and the recovery
 * returns the same artifact.</p>
 */
class CrashAfterCommitRecoveryPostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static CreateFollowUpActivityCommandAdapter realAdapter;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID actorId;
    private UUID requestId;
    private UUID decisionId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CrashAfterCommitRecoveryPostgresTest");
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
        JdbcActivityRepository activityRepo = new JdbcActivityRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(ds));
        ActivityUseCases activityUseCases = new ActivityUseCases(activityRepo, null);
        realAdapter = new CreateFollowUpActivityCommandAdapter(activityUseCases, jdbc, store);
    }

    @BeforeEach
    void seedConfirmedRequest() throws Exception {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        decisionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // Seed account for the activity to link to
        jdbc.update("INSERT INTO crm_accounts (id, tenant_id, version, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 0, 'Test Account', 'ACTIVE', ?, ?)",
                UUID.randomUUID(), tenantId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

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
                requestId, tenantId, actorId,
                "corr", "caus", "idem", UUID.randomUUID(),
                mapper.writeValueAsString(resultPayload),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        jdbc.update("INSERT INTO crm_integration_decisions " +
                        "(id, tenant_id, integration_request_id, actor_id, decision, idempotency_key, " +
                        "request_fingerprint, expected_entity_version, correlation_id, decision_status, " +
                        "created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, 'CONFIRM', ?, 'fp', 5, ?, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                decisionId, tenantId, requestId, actorId,
                "idem-crash", "corr-crash");
    }

    @Test
    void crashAfterCommandBeforeFinalizeDoesNotDuplicateArtifact() throws Exception {
        // Create an executor with a fault injector that throws after the first command commit
        Set<UUID> injectedDecisions = new HashSet<>();
        AfterCommandCommitFaultInjector faultInjector = decisionId -> {
            if (injectedDecisions.add(decisionId)) {
                throw new FaultInjectedException("Simulated crash after command commit for " + decisionId);
            }
        };

        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ConfirmedRecommendationExecutor executor = new ConfirmedRecommendationExecutor(
                store, realAdapter, mapper,
                new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)),
                faultInjector, "test-worker", 60, 30);

        // Enqueue the execution event
        executor.enqueueExecution(tenantId, requestId, decisionId,
                actorId, "corr-crash", 0L);

        // First attempt: should crash after command commit (fault injected)
        var claimed1 = claimOurEvent(executor);
        assertThat(claimed1).isPresent();
        executor.processSingleExecutionEvent(claimed1.get());

        // Verify the activity was created (side effect committed)
        Integer activityCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%");
        assertThat(activityCount).isEqualTo(1);

        // Verify the artifact idempotency row exists
        Integer artifactCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_artifacts WHERE tenant_id = ? AND decision_id = ?",
                Integer.class, tenantId, decisionId);
        assertThat(artifactCount).isEqualTo(1);

        // Verify request is still EXECUTING (Transaction B did not complete)
        String reqStatus = jdbc.queryForObject(
                "SELECT status FROM crm_integration_requests WHERE id = ?",
                String.class, requestId);
        assertThat(reqStatus).isEqualTo("EXECUTING");

        // Force claim expiry so the second worker can reclaim
        jdbc.update("UPDATE crm_integration_outbox SET claim_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' " +
                "WHERE integration_request_id = ?", requestId);

        // Second attempt: recovery — findExisting should return the original artifact
        var claimed2 = claimOurEvent(executor);
        assertThat(claimed2).isPresent();
        executor.processSingleExecutionEvent(claimed2.get());

        // Verify request reached EXECUTED
        reqStatus = jdbc.queryForObject(
                "SELECT status FROM crm_integration_requests WHERE id = ?",
                String.class, requestId);
        assertThat(reqStatus).isEqualTo("EXECUTED");

        // Verify decision reached EXECUTED
        String decStatus = jdbc.queryForObject(
                "SELECT decision_status FROM crm_integration_decisions WHERE id = ?",
                String.class, decisionId);
        assertThat(decStatus).isEqualTo("EXECUTED");

        // Verify ledger reached EXECUTED
        String ledStatus = jdbc.queryForObject(
                "SELECT execution_status FROM crm_integration_command_executions WHERE decision_id = ?",
                String.class, decisionId);
        assertThat(ledStatus).isEqualTo("EXECUTED");

        // Verify outbox is COMPLETED
        String outboxStatus = jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE integration_request_id = ?",
                String.class, requestId);
        assertThat(outboxStatus).isEqualTo("COMPLETED");

        // CRITICAL: verify only ONE activity exists (no duplicate)
        activityCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND subject LIKE ?",
                Integer.class, tenantId, "%" + decisionId + "%");
        assertThat(activityCount).isEqualTo(1);

        // CRITICAL: verify only ONE artifact row exists
        artifactCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_artifacts WHERE tenant_id = ? AND decision_id = ?",
                Integer.class, tenantId, decisionId);
        assertThat(artifactCount).isEqualTo(1);

        // CRITICAL: verify only ONE ledger row exists
        Integer ledgerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_command_executions WHERE decision_id = ?",
                Integer.class, decisionId);
        assertThat(ledgerCount).isEqualTo(1);
    }

    private java.util.Optional<CrmIntegrationStore.OutboxEvent> claimOurEvent(ConfirmedRecommendationExecutor executor) {
        while (true) {
            var claimed = store.claimNextOutboxEvent("test-worker", 60,
                    ConfirmedRecommendationExecutor.ACCEPTED_EVENT_TYPES);
            if (claimed.isEmpty()) return java.util.Optional.empty();
            if (claimed.get().integrationRequestId().equals(requestId)) {
                return java.util.Optional.of(claimed.get());
            }
        }
    }
}
