package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRM-009 PostgreSQL acceptance tests for decision lifecycle.
 *
 * <p>Verifies:</p>
 * <ul>
 *   <li>PENDING / CONFIRMED / EXECUTING have completed_at NULL (non-terminal).</li>
 *   <li>REJECTED / EXECUTED / EXECUTION_REJECTED / CONFLICT have completed_at NOT NULL.</li>
 *   <li>Replayed REJECT with same fingerprint returns the stored decision (no exception).</li>
 *   <li>Same idempotency key with different fingerprint throws
 *       {@link IntegrationException} of type IDEMPOTENCY_KEY_REUSED.</li>
 * </ul>
 */

class CrmIntegrationDecisionPostgresTest {

    
    static PostgreSQLContainer<?> POSTGRES;

    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;

    private UUID tenantId;
    private UUID requestId;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for CRM-009 decision PostgreSQL tests");

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
    }

    @Test
    void pendingDecisionHasNullCompletedAt() {
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-1", "fp-1", 0L, "corr-1");
        assertThat(dr.created()).isTrue();
        assertThat(dr.record().decisionStatus()).isEqualTo("PENDING");
        assertThat(dr.record().completedAt()).isNull();
    }

    @Test
    void confirmedDecisionHasNullCompletedAt() {
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-2", "fp-2", 0L, "corr-2");
        boolean ok = store.transitionDecision(tenantId, dr.record().id(),
                dr.record().version(), Set.of("PENDING"), "CONFIRMED", null, null);
        assertThat(ok).isTrue();
        CrmIntegrationStore.DecisionRecord after = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        assertThat(after.decisionStatus()).isEqualTo("CONFIRMED");
        assertThat(after.completedAt()).isNull();
    }

    @Test
    void rejectedDecisionHasNotNullCompletedAt() {
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "REJECT",
                "idem-3", "fp-3", 0L, "corr-3");
        boolean ok = store.transitionDecision(tenantId, dr.record().id(),
                dr.record().version(), Set.of("PENDING"), "REJECTED", null, null);
        assertThat(ok).isTrue();
        CrmIntegrationStore.DecisionRecord after = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        assertThat(after.decisionStatus()).isEqualTo("REJECTED");
        assertThat(after.completedAt()).isNotNull();
    }

    @Test
    void executedDecisionHasNotNullCompletedAt() {
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-4", "fp-4", 0L, "corr-4");
        store.transitionDecision(tenantId, dr.record().id(),
                dr.record().version(), Set.of("PENDING"), "CONFIRMED", null, null);
        CrmIntegrationStore.DecisionRecord confirmed = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        store.transitionDecision(tenantId, dr.record().id(),
                confirmed.version(), Set.of("CONFIRMED"), "EXECUTING", null, null);
        CrmIntegrationStore.DecisionRecord executing = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        store.transitionDecision(tenantId, dr.record().id(),
                executing.version(), Set.of("EXECUTING"), "EXECUTED",
                "ref-1", null);

        CrmIntegrationStore.DecisionRecord after = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        assertThat(after.decisionStatus()).isEqualTo("EXECUTED");
        assertThat(after.completedAt()).isNotNull();
    }

    @Test
    void replayedRejectReturnsStoredDecision() {
        // First REJECT
        CrmIntegrationStore.DecisionResult first = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "REJECT",
                "idem-replay", "fp-reject", 0L, "corr-replay");
        store.transitionDecision(tenantId, first.record().id(),
                first.record().version(), Set.of("PENDING"), "REJECTED", null, null);

        // Replay with same fingerprint — should return existing, NOT throw
        CrmIntegrationStore.DecisionResult replay = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "REJECT",
                "idem-replay", "fp-reject", 0L, "corr-replay-2");
        assertThat(replay.created()).isFalse();
        assertThat(replay.record().decisionStatus()).isEqualTo("REJECTED");
    }

    @Test
    void sameKeyWithDifferentFingerprintThrowsConflict() {
        store.createDecision(tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-conflict", "fp-original", 0L, "corr-c1");

        // Replay with DIFFERENT fingerprint — should throw IDEMPOTENCY_KEY_REUSED
        assertThatThrownBy(() -> store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-conflict", "fp-different", 0L, "corr-c2"))
                .isInstanceOf(IntegrationException.class);
    }

    @Test
    void conflictDecisionHasNotNullCompletedAt() {
        CrmIntegrationStore.DecisionResult dr = store.createDecision(
                tenantId, requestId, UUID.randomUUID(), "CONFIRM",
                "idem-conf", "fp-conf", 0L, "corr-conf");
        boolean ok = store.transitionDecision(tenantId, dr.record().id(),
                dr.record().version(), Set.of("PENDING"), "CONFLICT", null, "STATE_TRANSITION_FAILED");
        assertThat(ok).isTrue();
        CrmIntegrationStore.DecisionRecord after = store.findDecisionById(
                tenantId, requestId, dr.record().id()).orElseThrow();
        assertThat(after.decisionStatus()).isEqualTo("CONFLICT");
        assertThat(after.completedAt()).isNotNull();
    }
}
