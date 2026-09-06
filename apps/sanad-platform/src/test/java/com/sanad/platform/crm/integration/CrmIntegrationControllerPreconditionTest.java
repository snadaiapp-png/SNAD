package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.application.CrmIntegrationUseCases;
import com.sanad.platform.crm.integration.application.ConfirmedRecommendationExecutor;
import com.sanad.platform.crm.integration.application.CrmEntitySnapshotPort;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Direct unit test for atomic If-Match enforcement.
 *
 * <p>Runs against the same PostgreSQL Direct instance used by the whole
 * Maven test suite (SPRING_DATASOURCE_URL, default
 * {@code jdbc:postgresql://localhost:5432/sanad}). Each test method gets
 * its own disposable schema ({@code crm_it_<uuid>}) bound through the
 * {@code currentSchema} JDBC URL parameter. H2 is no longer on the test
 * classpath — PostgreSQL is the only acceptance database.</p>
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>Stale If-Match version → INTEGRATION_VERSION_MISMATCH (HTTP 412).</li>
 *   <li>Reject also requires If-Match (cannot reject without expectedIntegrationVersion).</li>
 *   <li>Concurrent confirm races are detected via version mismatch.</li>
 * </ul>
 */
class CrmIntegrationControllerPreconditionTest {

    private static final String PG_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://localhost:5432/sanad");
    private static final String PG_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PG_PASS = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    private JdbcTemplate jdbc;
    private CrmIntegrationStore store;
    private CrmIntegrationUseCases useCases;
    private UUID tenantId;
    private UUID requestId;
    private String schema;

    @BeforeAll
    static void requirePostgreSql() {
        Assumptions.assumeTrue(
                Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("CrmIntegrationControllerPreconditionTest"),
                "PostgreSQL Direct required");
    }

    @BeforeEach
    void setup() throws Exception {
        schema = "crm_it_" + UUID.randomUUID().toString().replace("-", "");
        // Bootstrap connection: create the disposable schema for this test.
        try (Connection c = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
             Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            s.execute("CREATE SCHEMA " + schema);
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(
                PG_URL + (PG_URL.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                PG_USER, PG_PASS);
        jdbc = new JdbcTemplate(ds);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_requests (" +
                    "id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY, tenant_id UUID NOT NULL, actor_id UUID NOT NULL, " +
                    "integration_type VARCHAR(80) NOT NULL, contract_name VARCHAR(120) NOT NULL, " +
                    "contract_version VARCHAR(40) NOT NULL, correlation_id VARCHAR(160) NOT NULL, " +
                    "causation_id VARCHAR(160) NOT NULL, idempotency_key VARCHAR(200) NOT NULL, " +
                    "source_entity_type VARCHAR(80) NOT NULL, source_entity_id UUID NOT NULL, " +
                    "source_entity_version BIGINT NOT NULL, required_capability VARCHAR(160) NOT NULL, " +
                    "data_classification VARCHAR(80) NOT NULL, requested_locale VARCHAR(20) NOT NULL, " +
                    "payload JSONB NOT NULL DEFAULT '{}'::jsonb, result_payload JSONB, " +
                    "status VARCHAR(40) NOT NULL, external_reference UUID, error_code VARCHAR(120), " +
                    "requested_at TIMESTAMP WITH TIME ZONE NOT NULL, " +
                    "expires_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "version BIGINT NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_outbox (" +
                    "id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY, tenant_id UUID NOT NULL, " +
                    "integration_request_id UUID NOT NULL, integration_type VARCHAR(80) NOT NULL, " +
                    "event_type VARCHAR(40) NOT NULL DEFAULT 'AI_REQUEST_DISPATCH', " +
                    "dispatch_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', " +
                    "attempt_count INTEGER NOT NULL DEFAULT 0, max_attempts INTEGER NOT NULL DEFAULT 5, " +
                    "next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "claimed_at TIMESTAMP WITH TIME ZONE, claimed_by VARCHAR(200), " +
                    "claim_token UUID, claim_expires_at TIMESTAMP WITH TIME ZONE, " +
                    "last_error_code VARCHAR(120), idempotency_key VARCHAR(200) NOT NULL, " +
                    "payload JSONB NOT NULL DEFAULT '{}'::jsonb, completed_at TIMESTAMP WITH TIME ZONE, " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "version BIGINT NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_decisions (" +
                    "id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY, tenant_id UUID NOT NULL, " +
                    "integration_request_id UUID NOT NULL, actor_id UUID NOT NULL, " +
                    "decision VARCHAR(20) NOT NULL, idempotency_key VARCHAR(200) NOT NULL, " +
                    "request_fingerprint VARCHAR(500) NOT NULL, " +
                    "expected_entity_version BIGINT NOT NULL, correlation_id VARCHAR(160) NOT NULL, " +
                    "decision_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', " +
                    "command_reference VARCHAR(500), error_code VARCHAR(120), " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "completed_at TIMESTAMP WITH TIME ZONE, " +
                    "version BIGINT NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_command_executions (" +
                    "id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY, tenant_id UUID NOT NULL, " +
                    "decision_id UUID NOT NULL, integration_request_id UUID NOT NULL, " +
                    "action_code VARCHAR(80) NOT NULL, " +
                    "execution_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', " +
                    "idempotency_key VARCHAR(200) NOT NULL, " +
                    "attempt_count INTEGER NOT NULL DEFAULT 0, " +
                    "command_reference VARCHAR(500), result_payload JSONB, " +
                    "error_code VARCHAR(120), claim_token UUID, " +
                    "started_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE, " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "version BIGINT NOT NULL DEFAULT 0, " +
                    "CONSTRAINT crm_cmd_exec_decision_uq UNIQUE (tenant_id, decision_id))");
        }
        store = new CrmIntegrationStore(jdbc, new ObjectMapper());
        // Stub snapshot that always returns a valid snapshot for the request's entity
        CrmEntitySnapshotPort stubSnapshot = (tenantId, entityType, entityId) ->
                new CrmEntitySnapshotPort.CrmEntitySnapshot(
                        tenantId, entityType, entityId, 5L, "ACTIVE", true);
        // Pass a no-op executor — confirm path tests do not exercise execution
        ConfirmedRecommendationExecutor noopExecutor = null;
        try {
            noopExecutor = new ConfirmedRecommendationExecutor(
                    store,
                    new com.sanad.platform.crm.integration.application.StubConfirmedRecommendationCommandAdapter(),
                    new ObjectMapper(),
                    new org.springframework.transaction.support.TransactionTemplate(
                            new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)),
                    com.sanad.platform.crm.integration.application.AfterCommandCommitFaultInjector.NO_OP,
                    "test-worker",
                    60,
                    30);
        } catch (Exception e) {
            // If executor construction fails, tests that need it will fail explicitly
        }
        AuditPort audit = (tenant, actor, action, type, id, change, at) -> {};
        TimelineEventPort timeline = (tenant, type, id, event, summary, source, sourceId, actor, at) -> {};
        useCases = new CrmIntegrationUseCases(store, stubSnapshot, noopExecutor, new ObjectMapper(), audit, timeline);

        tenantId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // Insert with result_payload containing actionCode so confirm's enqueueExecution succeeds.
        // CAST mirrors the production store's own JSONB binding (CrmIntegrationStore).
        String resultPayloadJson = "{\"actionCode\":\"CREATE_FOLLOW_UP_ACTIVITY\",\"status\":\"AVAILABLE\",\"actionable\":true,\"humanConfirmationRequired\":true,\"generatedAt\":\"2026-07-24T00:00:00Z\",\"expiresAt\":\"2099-12-31T00:00:00Z\",\"policyVersion\":\"v1\",\"modelVersion\":\"v1\"}";
        jdbc.update("INSERT INTO crm_integration_requests " +
                "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                "source_entity_version, required_capability, data_classification, requested_locale, " +
                "payload, result_payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 5, " +
                "'CRM.AI.READ', 'INTERNAL', 'en', '{}', " +
                "CAST(? AS jsonb), " +
                "'RECOMMENDATION_AVAILABLE', ?, ?, ?, ?, 0)",
                requestId, tenantId, UUID.randomUUID(),
                "corr", "caus", "idem", UUID.randomUUID(),
                resultPayloadJson,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @AfterEach
    void tearDown() {
        if (schema != null) {
            try (Connection c = DriverManager.getConnection(PG_URL, PG_USER, PG_PASS);
                 Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            } catch (Exception ignored) {
                // best-effort cleanup of the disposable test schema
            }
        }
    }

    @Test
    void staleIfMatchThrowsVersionMismatch() {
        // Pass an outdated If-Match (current version is 0, we pass 99)
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-1", "idem-1", 5L, 99L))
                .isInstanceOf(IntegrationException.class)
                .satisfies(e -> {
                    IntegrationException ie = (IntegrationException) e;
                    assertThat(ie.errorCode()).isEqualTo(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH);
                    assertThat(ie.httpStatus()).isEqualTo(412);
                });
    }

    @Test
    void rejectAlsoRequiresCorrectIfMatch() {
        assertThatThrownBy(() -> useCases.rejectRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-2", "idem-2", "rejected", 99L))
                .isInstanceOf(IntegrationException.class)
                .satisfies(e -> {
                    IntegrationException ie = (IntegrationException) e;
                    assertThat(ie.errorCode()).isEqualTo(IntegrationErrorCode.INTEGRATION_VERSION_MISMATCH);
                });
    }

    @Test
    void concurrentConfirmRaceDetectedByVersionMismatch() {
        // First confirm succeeds (version 0 → 1, request status → CONFIRMED)
        useCases.confirmRecommendation(tenantId, UUID.randomUUID(), requestId,
                "corr-first", "idem-first", 5L, 0L);

        // Second confirm with stale If-Match=0 must fail with version mismatch
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-second", "idem-second", 5L, 0L))
                .isInstanceOf(IntegrationException.class);
    }
}
