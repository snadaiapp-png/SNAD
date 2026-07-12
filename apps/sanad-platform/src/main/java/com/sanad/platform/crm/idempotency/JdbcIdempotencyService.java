package com.sanad.platform.crm.idempotency;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC-backed {@link IdempotencyService}. Stores records in the
 * {@code crm_idempotency_records} table created by Flyway migration
 * {@code V20260713_1__create_crm_idempotency_records.sql}.
 * <p>
 * The table has a UNIQUE constraint on
 * {@code (tenant_id, principal_id, endpoint, idempotency_key)} so the
 * INSERT-or-SELECT race is resolved by the database.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public class JdbcIdempotencyService implements IdempotencyService {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Replay begin(UUID tenantId, UUID principalId, String endpoint, String idempotencyKey, String requestFingerprint) {
        // First, try to look up an existing record for this composite key.
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("principalId", principalId)
                .addValue("endpoint", endpoint)
                .addValue("key", idempotencyKey);
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
                    "       response_status, response_body_json, created_at, expires_at " +
                    "FROM crm_idempotency_records " +
                    "WHERE tenant_id = :tenantId AND principal_id = :principalId " +
                    "  AND endpoint = :endpoint AND idempotency_key = :key", params);
            IdempotencyRecord existing = mapRow(row);
            if (existing.isExpired(Instant.now())) {
                // Expired — delete and proceed to insert a fresh record.
                jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                        new MapSqlParameterSource("id", existing.id()));
            } else if (!existing.requestFingerprintSha256().equals(requestFingerprint)) {
                throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT);
            } else if (existing.responseStatus() == 0) {
                throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT,
                        "An operation with this Idempotency-Key is already in progress.");
            } else {
                return new Replay.ReplayHit(existing);
            }
        } catch (EmptyResultDataAccessException ignored) {
            // No existing record — proceed to insert below.
        }
        // Insert a fresh in-flight record. The UNIQUE constraint protects
        // us against concurrent inserts for the same composite key.
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_RETENTION);
        try {
            jdbc.update(
                    "INSERT INTO crm_idempotency_records " +
                    "(id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
                    " response_status, response_body_json, created_at, expires_at) " +
                    "VALUES (:id, :tenantId, :principalId, :endpoint, :key, :fp, 0, NULL, :now, :expires)",
                    new MapSqlParameterSource()
                            .addValue("id", operationId)
                            .addValue("tenantId", tenantId)
                            .addValue("principalId", principalId)
                            .addValue("endpoint", endpoint)
                            .addValue("key", idempotencyKey)
                            .addValue("fp", requestFingerprint)
                            .addValue("now", Timestamp.from(now))
                            .addValue("expires", Timestamp.from(expiresAt)));
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // Concurrent insert — re-read the row to decide.
            return begin(tenantId, principalId, endpoint, idempotencyKey, requestFingerprint);
        }
        return new Replay.ReplayMiss(operationId);
    }

    @Override
    public void complete(UUID operationId, int responseStatus, String responseBodyJson) {
        jdbc.update(
                "UPDATE crm_idempotency_records " +
                "SET response_status = :status, response_body_json = :body " +
                "WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", operationId)
                        .addValue("status", responseStatus)
                        .addValue("body", responseBodyJson));
    }

    @Override
    public void fail(UUID operationId) {
        jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                new MapSqlParameterSource("id", operationId));
    }

    private static IdempotencyRecord mapRow(Map<String, Object> row) {
        return new IdempotencyRecord(
                (UUID) row.get("id"),
                (UUID) row.get("tenant_id"),
                (UUID) row.get("principal_id"),
                (String) row.get("endpoint"),
                (String) row.get("idempotency_key"),
                (String) row.get("request_fingerprint_sha256"),
                row.get("response_status") == null ? 0 : ((Number) row.get("response_status")).intValue(),
                (String) row.get("response_body_json"),
                row.get("created_at") == null ? null : ((Timestamp) row.get("created_at")).toInstant(),
                row.get("expires_at") == null ? null : ((Timestamp) row.get("expires_at")).toInstant());
    }
}
