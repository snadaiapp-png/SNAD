package com.sanad.platform.crm.ownership.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import com.sanad.platform.crm.ownership.domain.QueueClaimIdempotencyPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL-backed idempotency record for queue claim commands. */
@Component
public class JdbcQueueClaimIdempotencyAdapter implements QueueClaimIdempotencyPort {

    private static final String ENDPOINT = "CRM_QUEUE_CLAIM";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcQueueClaimIdempotencyAdapter(NamedParameterJdbcTemplate jdbc,
                                            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ClaimRecord> reserve(UUID tenantId,
                                         UUID principalId,
                                         UUID idempotencyKey,
                                         String requestFingerprintSha256) {
        requireArguments(tenantId, principalId, idempotencyKey, requestFingerprintSha256);
        Instant now = Instant.now();
        int inserted = jdbc.update("""
                INSERT INTO crm_idempotency_records
                  (id, tenant_id, principal_id, endpoint, idempotency_key,
                   request_fingerprint_sha256, response_status, response_body_json,
                   response_headers_json, content_type, created_at, expires_at)
                VALUES
                  (:id, :tenantId, :principalId, :endpoint, :key,
                   :fingerprint, 0, NULL, NULL, 'application/json', :createdAt, :expiresAt)
                ON CONFLICT (tenant_id, principal_id, endpoint, idempotency_key)
                DO NOTHING
                """, parameters(tenantId, principalId, idempotencyKey, requestFingerprintSha256)
                .addValue("id", UUID.randomUUID())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(now.plus(24, ChronoUnit.HOURS))));
        if (inserted == 1) {
            return Optional.empty();
        }
        return Optional.of(loadForUpdate(tenantId, principalId, idempotencyKey));
    }

    @Override
    public void complete(UUID tenantId,
                         UUID principalId,
                         UUID idempotencyKey,
                         String requestFingerprintSha256,
                         UUID assignmentId) {
        requireArguments(tenantId, principalId, idempotencyKey, requestFingerprintSha256);
        if (assignmentId == null) {
            throw new OwnershipDomainException("assignmentId required for idempotency completion");
        }
        String body = "{\"assignmentId\":\"" + assignmentId + "\"}";
        int rows = jdbc.update("""
                UPDATE crm_idempotency_records
                   SET response_status=200,
                       response_body_json=:body,
                       response_headers_json='{}',
                       content_type='application/json'
                 WHERE tenant_id=:tenantId
                   AND principal_id=:principalId
                   AND endpoint=:endpoint
                   AND idempotency_key=:key
                   AND request_fingerprint_sha256=:fingerprint
                   AND response_status=0
                """, parameters(tenantId, principalId, idempotencyKey, requestFingerprintSha256)
                .addValue("body", body));
        if (rows != 1) {
            ClaimRecord existing = loadForUpdate(tenantId, principalId, idempotencyKey);
            if (!requestFingerprintSha256.equals(existing.requestFingerprintSha256())
                    || !existing.isComplete()
                    || !assignmentId.equals(existing.assignmentId())) {
                throw new OwnershipDomainException("Queue claim idempotency completion conflict");
            }
        }
    }

    private ClaimRecord loadForUpdate(UUID tenantId,
                                      UUID principalId,
                                      UUID idempotencyKey) {
        try {
            return jdbc.queryForObject("""
                    SELECT request_fingerprint_sha256,
                           response_status,
                           response_body_json
                      FROM crm_idempotency_records
                     WHERE tenant_id=:tenantId
                       AND principal_id=:principalId
                       AND endpoint=:endpoint
                       AND idempotency_key=:key
                     FOR UPDATE
                    """, parameters(tenantId, principalId, idempotencyKey, "unused"),
                    (rs, rowNum) -> new ClaimRecord(
                            rs.getString("request_fingerprint_sha256"),
                            rs.getInt("response_status"),
                            assignmentId(rs.getString("response_body_json"))));
        } catch (EmptyResultDataAccessException missing) {
            throw new OwnershipDomainException("Queue claim idempotency record disappeared");
        }
    }

    private UUID assignmentId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            JsonNode assignmentId = node.get("assignmentId");
            return assignmentId == null || assignmentId.isNull()
                    ? null : UUID.fromString(assignmentId.asText());
        } catch (Exception invalid) {
            throw new OwnershipDomainException("Invalid queue claim idempotency response", invalid);
        }
    }

    private MapSqlParameterSource parameters(UUID tenantId,
                                             UUID principalId,
                                             UUID idempotencyKey,
                                             String fingerprint) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("principalId", principalId)
                .addValue("endpoint", ENDPOINT)
                .addValue("key", idempotencyKey.toString())
                .addValue("fingerprint", fingerprint);
    }

    private void requireArguments(UUID tenantId,
                                  UUID principalId,
                                  UUID idempotencyKey,
                                  String fingerprint) {
        if (tenantId == null || principalId == null || idempotencyKey == null) {
            throw new OwnershipDomainException("Tenant, principal and idempotency key are required");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new OwnershipDomainException("Valid SHA-256 request fingerprint required");
        }
    }
}
