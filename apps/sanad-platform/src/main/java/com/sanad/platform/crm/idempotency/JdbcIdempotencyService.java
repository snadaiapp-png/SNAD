package com.sanad.platform.crm.idempotency;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <h3>FORCE RLS contract (V20261029_1)</h3>
 * {@code crm_idempotency_records} is a fail-closed FORCE RLS table. Unlike the
 * CRM domain use-cases (which run inside {@code @Transactional} boundaries
 * where {@code TenantRlsConnectionHandler} applies the tenant GUC from the
 * SecurityContext), this service is invoked at controller level BEFORE any
 * transaction exists — a raw connection would be in autocommit mode and every
 * write would be rejected fail-closed. Each operation therefore opens its own
 * transaction and applies the transaction-local {@code app.tenant_id} GUC from
 * the authenticated tenant id passed by the controller (the same trusted
 * identity the rest of the request uses).
 * <p>
 * The INSERT-or-SELECT race is resolved with a bounded retry loop instead of
 * an in-transaction re-read: a unique-violation ABORTS the current PostgreSQL
 * transaction, so the re-read must run in a NEW transaction.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public class JdbcIdempotencyService implements IdempotencyService {

    private static final int MAX_INSERT_ATTEMPTS = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcIdempotencyService(NamedParameterJdbcTemplate jdbc,
                                  PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Replay begin(UUID tenantId, UUID principalId, String endpoint, String idempotencyKey, String requestFingerprint) {
        org.springframework.dao.DuplicateKeyException lastDup = null;
        for (int attempt = 0; attempt < MAX_INSERT_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> {
                    applyTenantScope(tenantId);
                    // Try to look up an existing record for this composite key.
                    Replay replay = lookupAndValidate(
                            tenantId, principalId, endpoint, idempotencyKey, requestFingerprint);
                    if (replay != null) {
                        return replay;
                    }
                    // Insert a fresh in-flight record. The UNIQUE constraint
                    // protects us against concurrent inserts for the same
                    // composite key; a violation aborts this transaction and
                    // the caller loop re-opens a NEW one to re-read.
                    UUID operationId = UUID.randomUUID();
                    Instant now = Instant.now();
                    Instant expiresAt = now.plus(DEFAULT_RETENTION);
                    jdbc.update(
                            "INSERT INTO crm_idempotency_records " +
                            "(id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
                            " response_status, response_body_json, response_headers_json, content_type, created_at, expires_at) " +
                            "VALUES (:id, :tenantId, :principalId, :endpoint, :key, :fp, 0, NULL, NULL, NULL, :now, :expires)",
                            new MapSqlParameterSource()
                                    .addValue("id", operationId)
                                    .addValue("tenantId", tenantId)
                                    .addValue("principalId", principalId)
                                    .addValue("endpoint", endpoint)
                                    .addValue("key", idempotencyKey)
                                    .addValue("fp", requestFingerprint)
                                    .addValue("now", Timestamp.from(now))
                                    .addValue("expires", Timestamp.from(expiresAt)));
                    return new Replay.ReplayMiss(operationId);
                });
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // Concurrent writer won the UNIQUE race — loop and re-read in a
                // fresh transaction (hit, conflict, or insert again).
                lastDup = dup;
            }
        }
        throw lastDup;
    }

    /**
     * Look up an existing record for the composite key under the CURRENT
     * transaction's tenant scope. Returns the replay decision, or {@code null}
     * when no record exists (caller proceeds to insert). Expired records are
     * deleted so a fresh insert can proceed.
     *
     * @throws CrmContractException CRM_IDEMPOTENCY_CONFLICT on fingerprint
     *         mismatch or an in-flight (responseStatus == 0) record.
     */
    private Replay lookupAndValidate(UUID tenantId, UUID principalId, String endpoint,
                                     String idempotencyKey, String requestFingerprint) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("principalId", principalId)
                .addValue("endpoint", endpoint)
                .addValue("key", idempotencyKey);
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT id, tenant_id, principal_id, endpoint, idempotency_key, request_fingerprint_sha256, " +
                    "       response_status, response_body_json, response_headers_json, content_type, created_at, expires_at " +
                    "FROM crm_idempotency_records " +
                    "WHERE tenant_id = :tenantId AND principal_id = :principalId " +
                    "  AND endpoint = :endpoint AND idempotency_key = :key", params);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
        IdempotencyRecord existing = mapRow(row);
        if (existing.isExpired(Instant.now())) {
            // Expired — delete and proceed to insert a fresh record.
            jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                    new MapSqlParameterSource("id", existing.id()));
            return null;
        }
        if (!existing.requestFingerprintSha256().equals(requestFingerprint)) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT);
        }
        if (existing.responseStatus() == 0) {
            throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT,
                    "An operation with this Idempotency-Key is already in progress.");
        }
        return new Replay.ReplayHit(existing);
    }

    @Override
    public void complete(UUID tenantId, UUID operationId, int responseStatus, String responseBodyJson, String responseHeadersJson, String contentType) {
        transactions.executeWithoutResult(status -> {
            applyTenantScope(tenantId);
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
        });
    }

    @Override
    public void fail(UUID tenantId, UUID operationId) {
        transactions.executeWithoutResult(status -> {
            applyTenantScope(tenantId);
            jdbc.update("DELETE FROM crm_idempotency_records WHERE id = :id",
                    new MapSqlParameterSource("id", operationId));
        });
    }

    /**
     * Apply the transaction-local {@code app.tenant_id} GUC for the
     * authenticated tenant. The value is a well-formed UUID supplied by the
     * controller from {@code Authentication.getDetails()} — the same trusted
     * identity used for every other tenant-scoped write in the request.
     */
    private void applyTenantScope(UUID tenantId) {
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource("t", tenantId.toString()),
                String.class);
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
