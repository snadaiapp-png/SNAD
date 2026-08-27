package com.sanad.platform.crm.idempotency;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC-backed {@link IdempotencyService}. Stores records in the
 * {@code crm_idempotency_records} table created by Flyway migration
 * {@code V20260713_1__create_crm_idempotency_records.sql}.
 * <p>
 * Every public operation owns a Spring transaction. This is required by the
 * tenant RLS datasource: {@code app.tenant_id} is transaction-local and is
 * applied before SQL only on non-autocommit connections.
 * <p>
 * The table has a UNIQUE constraint on
 * {@code (tenant_id, principal_id, endpoint, idempotency_key)}. Concurrent
 * reservation uses PostgreSQL {@code ON CONFLICT DO NOTHING} so a duplicate
 * does not abort the transaction before the winning row is re-read.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public class JdbcIdempotencyService implements IdempotencyService {

    private static final String SELECT_BY_KEY =
            "SELECT id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
            "       response_status, response_body_json, response_headers_json, content_type, created_at, expires_at " +
            "FROM crm_idempotency_records " +
            "WHERE tenant_id = :tenantId AND principal_id = :principalId " +
            "  AND endpoint = :endpoint AND idempotency_key = :key";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Replay begin(UUID tenantId,
                        UUID principalId,
                        String endpoint,
                        String idempotencyKey,
                        String requestFingerprint) {
        MapSqlParameterSource keyParams = keyParams(tenantId, principalId, endpoint, idempotencyKey);

        IdempotencyRecord existing = findExisting(keyParams);
        if (existing != null) {
            if (existing.isExpired(Instant.now())) {
                jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                        new MapSqlParameterSource("id", existing.id()));
            } else {
                return resolveExisting(existing, requestFingerprint);
            }
        }

        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_RETENTION);
        int inserted = jdbc.update(
                "INSERT INTO crm_idempotency_records " +
                "(id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
                " response_status, response_body_json, response_headers_json, content_type, created_at, expires_at) " +
                "VALUES (:id, :tenantId, :principalId, :endpoint, :key, :fp, 0, NULL, NULL, NULL, :now, :expires) " +
                "ON CONFLICT (tenant_id, principal_id, endpoint, idempotency_key) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("id", operationId)
                        .addValue("tenantId", tenantId)
                        .addValue("principalId", principalId)
                        .addValue("endpoint", endpoint)
                        .addValue("key", idempotencyKey)
                        .addValue("fp", requestFingerprint)
                        .addValue("now", Timestamp.from(now))
                        .addValue("expires", Timestamp.from(expiresAt)));

        if (inserted == 1) {
            return new Replay.ReplayMiss(operationId);
        }

        // Another transaction won the unique-key race. ON CONFLICT avoids a
        // PostgreSQL error state, so this transaction can safely re-read the
        // committed winner and apply the normal replay/conflict contract.
        IdempotencyRecord winner = findExisting(keyParams);
        if (winner == null) {
            // The winner disappeared between conflict detection and re-read
            // (for example, its request failed and removed the in-flight row).
            // Treat this transient race as a governed conflict instead of
            // leaking an infrastructure exception or recursively issuing SQL.
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT,
                    "An operation with this Idempotency-Key changed concurrently. Retry the request.");
        }
        return resolveExisting(winner, requestFingerprint);
    }

    @Override
    @Transactional
    public void complete(UUID operationId,
                         int responseStatus,
                         String responseBodyJson,
                         String responseHeadersJson,
                         String contentType) {
        jdbc.update(
                "UPDATE crm_idempotency_records " +
                "SET response_status = :status, response_body_json = :body, response_headers_json = :headers, content_type = :ct " +
                "WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", operationId)
                        .addValue("status", responseStatus)
                        .addValue("body", responseBodyJson)
                        .addValue("headers", responseHeadersJson)
                        .addValue("ct", contentType));
    }

    @Override
    @Transactional
    public void fail(UUID operationId) {
        jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                new MapSqlParameterSource("id", operationId));
    }

    private IdempotencyRecord findExisting(MapSqlParameterSource params) {
        try {
            return mapRow(jdbc.queryForMap(SELECT_BY_KEY, params));
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private static Replay resolveExisting(IdempotencyRecord existing, String requestFingerprint) {
        if (!existing.requestFingerprintSha256().equals(requestFingerprint)) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT);
        }
        if (existing.responseStatus() == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT,
                    "An operation with this Idempotency-Key is already in progress.");
        }
        return new Replay.ReplayHit(existing);
    }

    private static MapSqlParameterSource keyParams(UUID tenantId,
                                                    UUID principalId,
                                                    String endpoint,
                                                    String idempotencyKey) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("principalId", principalId)
                .addValue("endpoint", endpoint)
                .addValue("key", idempotencyKey);
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
                (String) row.get("response_headers_json"),
                (String) row.get("content_type"),
                row.get("created_at") == null ? null : ((Timestamp) row.get("created_at")).toInstant(),
                row.get("expires_at") == null ? null : ((Timestamp) row.get("expires_at")).toInstant());
    }
}
