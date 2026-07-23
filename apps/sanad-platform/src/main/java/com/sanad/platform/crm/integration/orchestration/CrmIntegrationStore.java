package com.sanad.platform.crm.integration.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped durable request/result store with database-enforced idempotency. */
@Component
public class CrmIntegrationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CrmIntegrationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public StoredRequest create(IntegrationEnvelope envelope, String integrationType, JsonNode payload) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO crm_integration_requests " +
                            "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, source_entity_version, required_capability, data_classification, payload, status, requested_at, expires_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, ?, ?, ?)",
                    id, envelope.tenantId(), envelope.actorId(), integrationType,
                    envelope.contractName(), envelope.contractVersion(), envelope.correlationId(),
                    envelope.causationId(), envelope.idempotencyKey(), envelope.sourceEntityType(),
                    envelope.sourceEntityId(), envelope.sourceEntityVersion(), envelope.requiredCapability(),
                    envelope.dataClassification(), json(payload), Timestamp.from(envelope.requestedAt()),
                    Timestamp.from(envelope.expiresAt()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
            return find(envelope.tenantId(), id).orElseThrow();
        } catch (DuplicateKeyException duplicate) {
            return findByIdempotency(envelope.tenantId(), integrationType, envelope.idempotencyKey())
                    .orElseThrow(() -> duplicate);
        }
    }

    public void complete(UUID tenantId, UUID requestId, String status, UUID externalReference,
                         JsonNode result, String errorCode) {
        int updated = jdbc.update("UPDATE crm_integration_requests SET status=?, external_reference=?, result_payload=CAST(? AS jsonb), error_code=?, completed_at=?, updated_at=? WHERE tenant_id=? AND id=?",
                status, externalReference, json(result), errorCode, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()), tenantId, requestId);
        if (updated != 1) throw new IllegalArgumentException("Unknown tenant-scoped integration request");
    }

    public Optional<StoredRequest> find(UUID tenantId, UUID id) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, correlation_id, idempotency_key, requested_at, expires_at, error_code FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> new StoredRequest((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        rs.getString("integration_type"), rs.getString("status"),
                        (UUID) rs.getObject("external_reference"), rs.getString("correlation_id"),
                        rs.getString("idempotency_key"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("error_code")),
                tenantId, id).stream().findFirst();
    }

    private Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, correlation_id, idempotency_key, requested_at, expires_at, error_code FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> new StoredRequest((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        rs.getString("integration_type"), rs.getString("status"),
                        (UUID) rs.getObject("external_reference"), rs.getString("correlation_id"),
                        rs.getString("idempotency_key"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("error_code")),
                tenantId, type, key).stream().findFirst();
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid integration payload", error); }
    }

    public record StoredRequest(UUID id, UUID tenantId, String integrationType, String status,
                                UUID externalReference, String correlationId, String idempotencyKey,
                                Instant requestedAt, Instant expiresAt, String errorCode) {}
}
