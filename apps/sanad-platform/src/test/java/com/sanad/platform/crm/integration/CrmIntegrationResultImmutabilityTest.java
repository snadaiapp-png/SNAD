package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-009 PostgreSQL test for AI result immutability.
 *
 * <p>Verifies at the SQL level:</p>
 * <ul>
 *   <li>First {@code transitionWithResult} call writes result_payload.</li>
 *   <li>Second {@code transitionWithResult} call returns success=false because
 *       the UPDATE includes {@code AND result_payload IS NULL}.</li>
 *   <li>Status-only transition ({@code transitionStatus}) preserves
 *       result_payload byte-for-byte.</li>
 * </ul>
 */

class CrmIntegrationResultImmutabilityTest {

    
    static PostgreSQLContainer<?> POSTGRES;

    private static JdbcTemplate jdbc;
    private static CrmIntegrationStore store;
    private static ObjectMapper mapper;

    private UUID tenantId;
    private UUID requestId;

    @BeforeAll
    static void setup() {
        boolean docker = Crm009TestEnvironment.requireDockerOrSkip("CrmIntegrationResultImmutabilityTest");
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
        mapper = new ObjectMapper();
        store = new CrmIntegrationStore(jdbc, mapper);
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
                        "'CRM.AI.READ', 'INTERNAL', 'en-US', CAST('{}' AS jsonb), 'DISPATCHED', ?, ?, ?, ?, 0)",
                requestId, tenantId, UUID.randomUUID(),
                "corr-" + requestId, "caus-" + requestId,
                "idem-" + requestId, UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Test
    void firstResultWriteSucceedsSecondFails() {
        ObjectNode firstResult = mapper.createObjectNode();
        firstResult.put("actionCode", "CREATE_FOLLOW_UP_ACTIVITY");
        firstResult.put("explanation", "first result");

        CrmIntegrationStore.TransitionResult first = store.transitionWithResult(
                tenantId, requestId, 0L,
                Set.of("DISPATCHED"), "RECOMMENDATION_AVAILABLE",
                null, firstResult, null);
        assertThat(first.success()).isTrue();
        assertThat(first.request().resultPayload()).isNotNull();
        assertThat(first.request().resultPayload().get("actionCode").asText())
                .isEqualTo("CREATE_FOLLOW_UP_ACTIVITY");

        CrmIntegrationStore.StoredRequest after = store.find(tenantId, requestId).orElseThrow();

        ObjectNode secondResult = mapper.createObjectNode();
        secondResult.put("actionCode", "SCHEDULE_CONTACT");
        secondResult.put("explanation", "second result");

        CrmIntegrationStore.TransitionResult second = store.transitionWithResult(
                tenantId, requestId, after.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "RECOMMENDATION_AVAILABLE",
                null, secondResult, null);
        assertThat(second.success()).isFalse();

        // Original result_payload preserved
        CrmIntegrationStore.StoredRequest finalRow = store.find(tenantId, requestId).orElseThrow();
        assertThat(finalRow.resultPayload().get("actionCode").asText())
                .isEqualTo("CREATE_FOLLOW_UP_ACTIVITY");
        assertThat(finalRow.resultPayload().get("explanation").asText())
                .isEqualTo("first result");
    }

    @Test
    void statusOnlyTransitionPreservesResultByteForByte() {
        ObjectNode result = mapper.createObjectNode();
        result.put("actionCode", "CREATE_FOLLOW_UP_ACTIVITY");
        result.put("explanation", "preserve me");
        result.put("confidence", 0.87);

        store.transitionWithResult(tenantId, requestId, 0L,
                Set.of("DISPATCHED"), "RECOMMENDATION_AVAILABLE",
                null, result, null);
        CrmIntegrationStore.StoredRequest afterResult = store.find(tenantId, requestId).orElseThrow();
        JsonNode originalPayload = afterResult.resultPayload();

        CrmIntegrationStore.TransitionResult confirm = store.transitionStatus(
                tenantId, requestId, afterResult.version(),
                Set.of("RECOMMENDATION_AVAILABLE"), "CONFIRMED");
        assertThat(confirm.success()).isTrue();

        CrmIntegrationStore.StoredRequest afterConfirm = store.find(tenantId, requestId).orElseThrow();
        assertThat(afterConfirm.resultPayload()).isEqualTo(originalPayload);
        assertThat(afterConfirm.status()).isEqualTo("CONFIRMED");
    }
}
