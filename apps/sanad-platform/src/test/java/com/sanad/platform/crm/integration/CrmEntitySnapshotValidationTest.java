package com.sanad.platform.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.application.CrmIntegrationUseCases;
import com.sanad.platform.crm.integration.application.CrmEntitySnapshotPort;
import com.sanad.platform.crm.integration.orchestration.CrmIntegrationStore;
import com.sanad.platform.crm.integration.orchestration.IntegrationErrorCode;
import com.sanad.platform.crm.integration.orchestration.IntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2-based unit test for entity snapshot validation.
 *
 * <p>Verifies that the application layer enforces all entity validation
 * rules before transitioning to CONFIRMED:</p>
 * <ul>
 *   <li>Snapshot not found → ENTITY_NOT_FOUND (HTTP 404).</li>
 *   <li>Tenant mismatch → INVALID_TENANT (HTTP 400).</li>
 *   <li>Version drift (snapshot vs. request) → STALE_RECOMMENDATION (HTTP 409).</li>
 *   <li>Version drift (snapshot vs. expected) → STALE_RECOMMENDATION (HTTP 409).</li>
 *   <li>Entity inactive → ENTITY_STATE_CONFLICT (HTTP 409).</li>
 * </ul>
 */
class CrmEntitySnapshotValidationTest {

    private JdbcTemplate jdbc;
    private CrmIntegrationStore store;
    private CrmIntegrationUseCases useCases;
    private final StubEntitySnapshot stubSnapshot = new StubEntitySnapshot();
    private UUID tenantId;
    private UUID requestId;

    @BeforeEach
    void setup() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:entity-validation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(ds);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_requests (" +
                    "id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY, tenant_id UUID NOT NULL, actor_id UUID NOT NULL, " +
                    "integration_type VARCHAR(80) NOT NULL, contract_name VARCHAR(120) NOT NULL, " +
                    "contract_version VARCHAR(40) NOT NULL, correlation_id VARCHAR(160) NOT NULL, " +
                    "causation_id VARCHAR(160) NOT NULL, idempotency_key VARCHAR(200) NOT NULL, " +
                    "source_entity_type VARCHAR(80) NOT NULL, source_entity_id UUID NOT NULL, " +
                    "source_entity_version BIGINT NOT NULL, required_capability VARCHAR(160) NOT NULL, " +
                    "data_classification VARCHAR(80) NOT NULL, requested_locale VARCHAR(20) NOT NULL, " +
                    "payload JSON NOT NULL DEFAULT '{}', result_payload JSON, " +
                    "status VARCHAR(40) NOT NULL, external_reference UUID, error_code VARCHAR(120), " +
                    "requested_at TIMESTAMP WITH TIME ZONE NOT NULL, " +
                    "expires_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "version BIGINT NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_outbox (" +
                    "id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY, tenant_id UUID NOT NULL, " +
                    "integration_request_id UUID NOT NULL, integration_type VARCHAR(80) NOT NULL, " +
                    "event_type VARCHAR(40) NOT NULL DEFAULT 'AI_REQUEST_DISPATCH', " +
                    "dispatch_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', " +
                    "attempt_count INTEGER NOT NULL DEFAULT 0, max_attempts INTEGER NOT NULL DEFAULT 5, " +
                    "next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "claimed_at TIMESTAMP WITH TIME ZONE, claimed_by VARCHAR(200), " +
                    "claim_token UUID, claim_expires_at TIMESTAMP WITH TIME ZONE, " +
                    "last_error_code VARCHAR(120), idempotency_key VARCHAR(200) NOT NULL, " +
                    "payload JSON NOT NULL DEFAULT '{}', completed_at TIMESTAMP WITH TIME ZONE, " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "version BIGINT NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS crm_integration_decisions (" +
                    "id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY, tenant_id UUID NOT NULL, " +
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
        }
        store = new CrmIntegrationStore(jdbc, new ObjectMapper());
        useCases = new CrmIntegrationUseCases(store, stubSnapshot, new ObjectMapper());

        tenantId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO crm_integration_requests " +
                "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                "source_entity_version, required_capability, data_classification, requested_locale, " +
                "payload, status, requested_at, expires_at, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'AI', 'crm.ai', '1.0', ?, ?, ?, 'ACCOUNT', ?, 5, " +
                "'CRM.AI.READ', 'INTERNAL', 'en', '{}', 'RECOMMENDATION_AVAILABLE', ?, ?, ?, ?, 0)",
                requestId, tenantId, UUID.randomUUID(),
                "corr", "caus", "idem", UUID.randomUUID(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(30, ChronoUnit.SECONDS)),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    @Test
    void snapshotNotFoundThrowsEntityNotFound() {
        stubSnapshot.snapshot = null;
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-1", "idem-1", 5L, 0L))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("ENTITY_NOT_FOUND");
    }

    @Test
    void tenantMismatchThrowsInvalidTenant() {
        stubSnapshot.snapshot = new CrmEntitySnapshotPort.CrmEntitySnapshot(
                UUID.randomUUID(), // different tenant
                "ACCOUNT", UUID.randomUUID(), 5L, "ACTIVE", true);
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-2", "idem-2", 5L, 0L))
                .isInstanceOf(IntegrationException.class)
                .satisfies(e -> {
                    IntegrationException ie = (IntegrationException) e;
                    assertThat(ie.errorCode()).isEqualTo(IntegrationErrorCode.INVALID_TENANT);
                });
    }

    @Test
    void versionDriftThrowsStaleRecommendation() {
        stubSnapshot.snapshot = new CrmEntitySnapshotPort.CrmEntitySnapshot(
                tenantId, "ACCOUNT", UUID.randomUUID(), 99L, "ACTIVE", true);
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-3", "idem-3", 5L, 0L))
                .isInstanceOf(IntegrationException.class)
                .satisfies(e -> {
                    IntegrationException ie = (IntegrationException) e;
                    assertThat(ie.errorCode()).isEqualTo(IntegrationErrorCode.STALE_RECOMMENDATION);
                });
    }

    @Test
    void inactiveEntityThrowsStateConflict() {
        stubSnapshot.snapshot = new CrmEntitySnapshotPort.CrmEntitySnapshot(
                tenantId, "ACCOUNT", UUID.randomUUID(), 5L, "ARCHIVED", false);
        assertThatThrownBy(() -> useCases.confirmRecommendation(
                tenantId, UUID.randomUUID(), requestId,
                "corr-4", "idem-4", 5L, 0L))
                .isInstanceOf(IntegrationException.class)
                .satisfies(e -> {
                    IntegrationException ie = (IntegrationException) e;
                    assertThat(ie.errorCode()).isEqualTo(IntegrationErrorCode.ENTITY_STATE_CONFLICT);
                });
    }

    /** Minimal stub for testing. Returns a snapshot that matches the lookup
     *  keys (entityType, entityId) but uses the preset tenantId/version/state/active
     *  for validation testing. */
    private static class StubEntitySnapshot implements CrmEntitySnapshotPort {
        CrmEntitySnapshot snapshot;

        @Override
        public CrmEntitySnapshot load(UUID tenantId, String entityType, UUID entityId) {
            if (snapshot == null) return null;
            return new CrmEntitySnapshotPort.CrmEntitySnapshot(
                    snapshot.tenantId(), entityType, entityId,
                    snapshot.currentVersion(), snapshot.currentState(), snapshot.active());
        }
    }
}
