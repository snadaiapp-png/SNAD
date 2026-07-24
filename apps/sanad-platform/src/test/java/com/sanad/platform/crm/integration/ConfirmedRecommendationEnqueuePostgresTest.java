package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationExecutor;
import com.sanad.platform.crm.integration.application.CrmEntitySnapshotPort;
import com.sanad.platform.crm.integration.application.CrmIntegrationUseCases;
import com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter;
import com.sanad.platform.crm.integration.application.AfterCommandCommitFaultInjector;
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
 * CRM-009 PostgreSQL test: confirm atomically creates execution event.
 *
 * <p>Verifies Item 1: the confirm transaction atomically creates a
 * CONFIRMED_COMMAND_EXECUTION outbox event alongside the decision and
 * request transitions. If event creation fails, the entire transaction
 * rolls back.</p>
 */
class ConfirmedRecommendationEnqueuePostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static CrmIntegrationUseCases useCases;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID requestId;
    private UUID actorId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("ConfirmedRecommendationEnqueuePostgresTest");
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

        // Stub snapshot returns valid snapshot for any entity
        CrmEntitySnapshotPort stubSnapshot = (tid, et, eid) ->
                new CrmEntitySnapshotPort.CrmEntitySnapshot(tid, et, eid, 5L, "ACTIVE", true);

        ConfirmedRecommendationExecutor executor = new ConfirmedRecommendationExecutor(
                store, new StubConfirmedRecommendationCommandAdapter(), mapper,
                new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)),
                AfterCommandCommitFaultInjector.NO_OP,
                "test-worker", 60, 30);
        useCases = new CrmIntegrationUseCases(store, stubSnapshot, executor, mapper);
    }

    @BeforeEach
    void seedRequest() throws Exception {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        ObjectNode resultPayload = mapper.createObjectNode();
        resultPayload.put("actionCode", "CREATE_FOLLOW_UP_ACTIVITY");
        resultPayload.put("status", "AVAILABLE");
        resultPayload.put("actionable", true);
        resultPayload.put("humanConfirmationRequired", true);
        resultPayload.put("generatedAt", "2026-07-24T00:00:00Z");
        resultPayload.put("expiresAt", "2099-12-31T00:00:00Z");
        resultPayload.put("policyVersion", "v1");
        resultPayload.put("modelVersion", "v1");

        jdbc.update("INSERT INTO crm_integration_requests " +
                        "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                        "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                        "source_entity_version, required_capability, data_classification, requested_locale, " +
                        "payload, result_payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 5, " +
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), CAST(? AS jsonb), " +
                        "'RECOMMENDATION_AVAILABLE', ?, ?, ?, ?, 0)",
                requestId, tenantId, actorId,
                "corr-" + requestId, "caus-" + requestId,
                "idem-" + requestId, UUID.randomUUID(),
                mapper.writeValueAsString(resultPayload),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Test
    void confirmAtomicallyCreatesExecutionEvent() {
        useCases.confirmRecommendation(tenantId, actorId, requestId,
                "corr-confirm", "idem-confirm", 5L, 0L);

        // Verify request transitioned to CONFIRMED
        Map<String, Object> req = jdbc.queryForMap(
                "SELECT status, version FROM crm_integration_requests WHERE id=?", requestId);
        assertThat(req.get("status")).isEqualTo("CONFIRMED");

        // Verify execution event was created atomically
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT event_type, dispatch_status FROM crm_integration_outbox " +
                        "WHERE integration_request_id=?", requestId);
        assertThat(event.get("event_type")).isEqualTo("CONFIRMED_COMMAND_EXECUTION");
        assertThat(event.get("dispatch_status")).isEqualTo("PENDING");

        // Verify decision transitioned to CONFIRMED
        Map<String, Object> dec = jdbc.queryForMap(
                "SELECT decision_status FROM crm_integration_decisions " +
                        "WHERE integration_request_id=?", requestId);
        assertThat(dec.get("decision_status")).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmReplayDoesNotCreateSecondEvent() {
        // First confirm
        useCases.confirmRecommendation(tenantId, actorId, requestId,
                "corr-1", "idem-replay", 5L, 0L);

        // Replay with same idempotency key
        useCases.confirmRecommendation(tenantId, actorId, requestId,
                "corr-2", "idem-replay", 5L, 0L);

        // Only one outbox event should exist (unique constraint on tenant_id, integration_request_id, event_type)
        Integer eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_outbox WHERE integration_request_id=?",
                Integer.class, requestId);
        assertThat(eventCount).isEqualTo(1);
    }
}
