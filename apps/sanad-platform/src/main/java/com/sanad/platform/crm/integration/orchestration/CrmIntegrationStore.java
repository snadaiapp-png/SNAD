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

/**
 * Tenant-scoped durable request/result store with database-enforced idempotency.
 *
 * <p>Uses {@link CreateResult} with explicit {@link CreationDisposition} so callers
 * can distinguish CREATED_NEW from RETURNED_EXISTING and avoid redispatching.</p>
 */
@Component
public class CrmIntegrationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CrmIntegrationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Result of create() — tells caller whether to dispatch or skip. */
    public record CreateResult(StoredRequest request, CreationDisposition disposition) {}

    public enum CreationDisposition { CREATED_NEW, RETURNED_EXISTING }

    /**
     * Create a new integration request or return existing if idempotency key matches.
     * The caller MUST check disposition before dispatching to external services.
     */
    public CreateResult create(IntegrationEnvelope envelope, String integrationType, JsonNode payload) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO crm_integration_requests " +
                            "(id, tenant_id, actor_id, integration_type, contract_name, contract_version, " +
                            "correlation_id, causation_id, idempotency_key, source_entity_type, source_entity_id, " +
                            "source_entity_version, required_capability, data_classification, payload, status, " +
                            "requested_at, expires_at, created_at, updated_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, ?, ?, ?, 0)",
                    id, envelope.tenantId(), envelope.actorId(), integrationType,
                    envelope.contractName(), envelope.contractVersion(), envelope.correlationId(),
                    envelope.causationId(), envelope.idempotencyKey(), envelope.sourceEntityType(),
                    envelope.sourceEntityId(), envelope.sourceEntityVersion(), envelope.requiredCapability(),
                    envelope.dataClassification(), json(payload), Timestamp.from(envelope.requestedAt()),
                    Timestamp.from(envelope.expiresAt()), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
            return new CreateResult(find(envelope.tenantId(), id).orElseThrow(), CreationDisposition.CREATED_NEW);
        } catch (DuplicateKeyException duplicate) {
            StoredRequest existing = findByIdempotency(envelope.tenantId(), integrationType, envelope.idempotencyKey())
                    .orElseThrow(() -> duplicate);
            return new CreateResult(existing, CreationDisposition.RETURNED_EXISTING);
        }
    }

    /**
     * Conditional update with optimistic locking. Fails if version mismatch or
     * current status not in expectedStatuses.
     */
    public boolean conditionalUpdate(UUID tenantId, UUID requestId, long expectedVersion,
                                      String newStatus, UUID externalReference,
                                      JsonNode result, String errorCode,
                                      String... allowedCurrentStatuses) {
        StringBuilder sql = new StringBuilder(
                "UPDATE crm_integration_requests SET status=?, external_reference=?, " +
                "result_payload=CAST(? AS jsonb), error_code=?, completed_at=CASE WHEN ? IN " +
                "('COMPLETED','REJECTED','CONFIRMED','EXECUTED','CANCELLED','EXPIRED','TIMED_OUT','UNAVAILABLE','POLICY_DENIED','UNSAFE_OUTPUT') " +
                "THEN CURRENT_TIMESTAMP ELSE completed_at END, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND id=? AND version=?");
        var params = new java.util.ArrayList<Object>();
        params.add(newStatus);
        params.add(externalReference);
        params.add(json(result));
        params.add(errorCode);
        params.add(newStatus);
        params.add(tenantId);
        params.add(requestId);
        params.add(expectedVersion);

        if (allowedCurrentStatuses != null && allowedCurrentStatuses.length > 0) {
            sql.append(" AND status IN (");
            for (int i = 0; i < allowedCurrentStatuses.length; i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
                params.add(allowedCurrentStatuses[i]);
            }
            sql.append(")");
        }

        int updated = jdbc.update(sql.toString(), params.toArray());
        return updated == 1;
    }

    /**
     * Legacy complete() — use conditionalUpdate() for atomic state transitions.
     */
    public void complete(UUID tenantId, UUID requestId, String status, UUID externalReference,
                         JsonNode result, String errorCode) {
        int updated = jdbc.update("UPDATE crm_integration_requests SET status=?, external_reference=?, " +
                        "result_payload=CAST(? AS jsonb), error_code=?, completed_at=?, updated_at=?, version=version+1 " +
                        "WHERE tenant_id=? AND id=?",
                status, externalReference, json(result), errorCode, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()), tenantId, requestId);
        if (updated != 1) throw new IllegalArgumentException("Unknown tenant-scoped integration request");
    }

    public Optional<StoredRequest> find(UUID tenantId, UUID id) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND id=?",
                (rs, row) -> new StoredRequest((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        rs.getString("integration_type"), rs.getString("status"),
                        (UUID) rs.getObject("external_reference"), rs.getString("correlation_id"),
                        rs.getString("idempotency_key"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("error_code"),
                        rs.getLong("version")),
                tenantId, id).stream().findFirst();
    }

    public Optional<StoredRequest> findByIdempotency(UUID tenantId, String type, String key) {
        return jdbc.query("SELECT id, tenant_id, integration_type, status, external_reference, " +
                        "correlation_id, idempotency_key, requested_at, expires_at, error_code, version " +
                        "FROM crm_integration_requests WHERE tenant_id=? AND integration_type=? AND idempotency_key=?",
                (rs, row) -> new StoredRequest((UUID) rs.getObject("id"), (UUID) rs.getObject("tenant_id"),
                        rs.getString("integration_type"), rs.getString("status"),
                        (UUID) rs.getObject("external_reference"), rs.getString("correlation_id"),
                        rs.getString("idempotency_key"), rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("error_code"),
                        rs.getLong("version")),
                tenantId, type, key).stream().findFirst();
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid integration payload", error); }
    }

    public record StoredRequest(UUID id, UUID tenantId, String integrationType, String status,
                                UUID externalReference, String correlationId, String idempotencyKey,
                                Instant requestedAt, Instant expiresAt, String errorCode,
                                long version) {}
}
