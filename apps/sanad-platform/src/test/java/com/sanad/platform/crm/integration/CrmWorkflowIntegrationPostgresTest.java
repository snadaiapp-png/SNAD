package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.application.CrmEntitySnapshotPort;
import com.sanad.platform.crm.integration.application.CrmWorkflowOutboxWorker;
import com.sanad.platform.crm.integration.application.CrmWorkflowStore;
import com.sanad.platform.crm.integration.application.CrmWorkflowUseCases;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationEnvelope;
import com.sanad.platform.crm.integration.orchestration.WorkflowIntegrationPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CrmWorkflowIntegrationPostgresTest {

    private static PostgreSQLContainer<?> POSTGRES;
    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static CrmWorkflowUseCases useCases;
    private static CrmWorkflowOutboxWorker worker;
    private static TransactionTemplate transactions;
    private static FakeWorkflowPort workflowPort;
    private static final ObjectMapper mapper = new ObjectMapper();

    private UUID tenantId;
    private UUID actorId;
    private UUID entityId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip(
                "CrmWorkflowIntegrationPostgresTest");
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
        store = new CrmIntegrationStore(jdbc, mapper);
        CrmWorkflowStore workflowStore = new CrmWorkflowStore(jdbc, store);
        workflowPort = new FakeWorkflowPort();
        CrmEntitySnapshotPort snapshots = (tenantId, entityType, entityId) ->
                new CrmEntitySnapshotPort.CrmEntitySnapshot(
                        tenantId, entityType, entityId, 7L, "ACTIVE", true);
        useCases = new CrmWorkflowUseCases(
                store, workflowStore, snapshots, workflowPort, mapper);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        worker = new CrmWorkflowOutboxWorker(
                store, workflowStore, workflowPort, mapper, transactions,
                "workflow-test-worker", 60);
    }

    @BeforeEach
    void seedTenant() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        workflowPort.reset();
        jdbc.update(
                "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Workflow Tenant', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId,
                "workflow-" + tenantId.toString().substring(0, 8));
    }

    @Test
    void dispatchWorkerAndCallbacksReachCompleted() {
        ObjectNode payload = mapper.createObjectNode().put("assigneeUserId", actorId.toString());
        CrmIntegrationStore.StoredRequest created = transactions.execute(status ->
                useCases.dispatchAssignmentWorkflow(
                        tenantId, actorId, "corr-workflow", "cause-workflow",
                        "workflow-idem-1", "ACCOUNT", entityId, 7L,
                        payload, Locale.ENGLISH));

        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_integration_outbox " +
                        "WHERE integration_request_id=? AND event_type='WORKFLOW_DISPATCH'",
                Integer.class, created.id())).isEqualTo(1);

        worker.processWorkflowEvents();
        CrmIntegrationStore.StoredRequest accepted = store.find(tenantId, created.id()).orElseThrow();
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.externalReference()).isEqualTo(workflowPort.runId);

        CrmIntegrationStore.StoredRequest running = useCases.handleWorkflowCallback(
                tenantId, workflowPort.runId, "corr-workflow", "RUNNING",
                Instant.now(), null, null);
        assertThat(running.status()).isEqualTo("RUNNING");

        ObjectNode callbackResult = mapper.createObjectNode().put("approved", true);
        CrmIntegrationStore.StoredRequest completed = useCases.handleWorkflowCallback(
                tenantId, workflowPort.runId, "corr-workflow", "COMPLETED",
                Instant.now(), callbackResult, null);
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.resultPayload()).isNotNull();
        assertThat(completed.resultPayload().path("result").path("approved").asBoolean()).isTrue();

        CrmIntegrationStore.StoredRequest replay = useCases.handleWorkflowCallback(
                tenantId, workflowPort.runId, "corr-workflow", "COMPLETED",
                Instant.now(), callbackResult, null);
        assertThat(replay.version()).isEqualTo(completed.version());
    }

    @Test
    void acceptedWorkflowCanBeCancelledWithVersionCheck() {
        CrmIntegrationStore.StoredRequest created = transactions.execute(status ->
                useCases.scheduleReminder(
                        tenantId, actorId, "corr-cancel", "cause-cancel",
                        "workflow-idem-2", "CONTACT", entityId, 7L,
                        mapper.createObjectNode().put("dueAt", Instant.now().plusSeconds(3600).toString()),
                        Locale.ENGLISH));
        worker.processWorkflowEvents();
        CrmIntegrationStore.StoredRequest accepted = store.find(tenantId, created.id()).orElseThrow();

        CrmIntegrationStore.StoredRequest cancelled = useCases.cancelWorkflow(
                tenantId, accepted.id(), accepted.version(),
                "corr-cancel", "cancel-idem-1", "No longer required");

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(workflowPort.cancelCalled.get()).isTrue();
        assertThat(workflowPort.cancelIdempotencyKey).isEqualTo("cancel-idem-1");
    }

    @Test
    void workflowWorkerNeverClaimsAiEvents() {
        Instant now = Instant.now();
        IntegrationEnvelope envelope = new IntegrationEnvelope(
                "crm.ai.test", "1.0", tenantId, actorId,
                "corr-ai", "cause-ai", "ai-idem", "ACCOUNT", entityId, 7L,
                now, now.plusSeconds(60), Locale.ENGLISH, "CRM.AI.READ", "INTERNAL");
        CrmIntegrationStore.CreateResult ai = store.create(
                envelope, "AI", mapper.createObjectNode().put("capability", "CUSTOMER_SUMMARY"));
        store.createOutboxEvent(
                tenantId, ai.request().id(), "AI", "AI_REQUEST_DISPATCH",
                "ai-idem", mapper.createObjectNode().put("capability", "CUSTOMER_SUMMARY"));

        worker.processWorkflowEvents();

        assertThat(jdbc.queryForObject(
                "SELECT dispatch_status FROM crm_integration_outbox WHERE integration_request_id=?",
                String.class, ai.request().id())).isEqualTo("PENDING");
    }

    private static final class FakeWorkflowPort implements WorkflowIntegrationPort {
        private UUID runId = UUID.randomUUID();
        private final AtomicBoolean cancelCalled = new AtomicBoolean(false);
        private String cancelIdempotencyKey;

        @Override
        public WorkflowDispatch dispatch(
                IntegrationEnvelope envelope,
                String workflowType,
                com.fasterxml.jackson.databind.JsonNode minimizedPayload) {
            return new WorkflowDispatch(runId, Status.ACCEPTED, Instant.now(), null);
        }

        @Override
        public void cancel(
                UUID tenantId,
                UUID workflowRunId,
                String correlationId,
                String idempotencyKey,
                String reason) {
            assertThat(workflowRunId).isEqualTo(runId);
            cancelIdempotencyKey = idempotencyKey;
            cancelCalled.set(true);
        }

        private void reset() {
            runId = UUID.randomUUID();
            cancelCalled.set(false);
            cancelIdempotencyKey = null;
        }
    }
}
