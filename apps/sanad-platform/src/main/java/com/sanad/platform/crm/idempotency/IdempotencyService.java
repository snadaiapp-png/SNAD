package com.sanad.platform.crm.idempotency;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRM API Contract — Idempotency Service.
 * <p>
 * Stores and replays idempotent request results. Two implementations are
 * provided:
 *   - {@link InMemoryIdempotencyService} — used in unit/contract tests
 *     where a real DB is not available.
 *   - {@link JdbcIdempotencyService} — production implementation backed
 *     by the {@code crm_idempotency_records} table (added by Flyway
 *     migration {@code V20260713_1__create_crm_idempotency_records.sql}).
 * <p>
 * Behavior:
 *   - {@link #begin(UUID, UUID, String, String, String)} — open a record.
 *     If a record already exists for the same (tenant, principal, endpoint,
 *     key) tuple, either replay it (same fingerprint) or reject with
 *     {@code CRM_IDEMPOTENCY_CONFLICT} (different fingerprint).
 *   - {@link #complete(UUID, int, String)} — store the response so future
 *     replays return the same body and status.
 *   - {@link #fail(UUID)} — remove the in-flight record so the client can
 *     retry with the same key.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public interface IdempotencyService {

    Duration DEFAULT_RETENTION = Duration.ofHours(24);

    /**
     * Either start a new idempotent operation or replay an existing one.
     *
     * @param tenantId              the requesting tenant
     * @param principalId           the authenticated user id
     * @param endpoint              a stable endpoint identifier (e.g. {@code "POST:/api/v1/crm/accounts"})
     * @param idempotencyKey        the client-supplied Idempotency-Key
     * @param requestFingerprint    a canonical fingerprint of the request payload
     *                              (method + path + body hash) — used to detect
     *                              key+payload mismatch
     * @return either {@link Replay#replay(IdempotencyRecord)} (the cached
     *         response should be replayed) or {@link Replay#begin(UUID)}
     *         (the caller may proceed and must call {@link #complete} or
     *         {@link #fail} afterwards)
     */
    Replay begin(UUID tenantId, UUID principalId, String endpoint, String idempotencyKey, String requestFingerprint);

    void complete(UUID operationId, int responseStatus, String responseBodyJson);

    void fail(UUID operationId);

    /**
     * Result of {@link #begin}. Either a replay (cached response) or a
     * fresh operation id the caller must complete/fail.
     */
    sealed interface Replay permits Replay.ReplayHit, Replay.ReplayMiss {
        record ReplayHit(IdempotencyRecord record) implements Replay {}
        record ReplayMiss(UUID operationId) implements Replay {}
    }

    /**
     * Compute the SHA-256 fingerprint of the request payload. Used to
     * detect key+payload mismatch on retry.
     */
    static String fingerprint(String method, String path, String body) {
        String material = method + "|" + path + "|" + (body == null ? "" : body);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * In-memory implementation for tests. Production uses
     * {@link JdbcIdempotencyService}.
     */
    class InMemoryIdempotencyService implements IdempotencyService {
        private final Map<UUID, IdempotencyRecord> byOperation = new ConcurrentHashMap<>();
        private final Map<String, IdempotencyRecord> byKey = new ConcurrentHashMap<>();

        @Override
        public synchronized Replay begin(UUID tenantId, UUID principalId, String endpoint, String idempotencyKey, String requestFingerprint) {
            String compositeKey = compositeKey(tenantId, principalId, endpoint, idempotencyKey);
            IdempotencyRecord existing = byKey.get(compositeKey);
            if (existing != null) {
                if (existing.isExpired(Instant.now())) {
                    byKey.remove(compositeKey);
                    byOperation.remove(existing.id());
                } else if (!existing.requestFingerprintSha256().equals(requestFingerprint)) {
                    throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT);
                } else if (existing.responseStatus() == 0) {
                    // Operation is still in-flight. The client should retry
                    // later; we treat this as a conflict to avoid duplicate
                    // work.
                    throw new CrmContractException(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT,
                            "An operation with this Idempotency-Key is already in progress.");
                } else {
                    return new Replay.ReplayHit(existing);
                }
            }
            UUID operationId = UUID.randomUUID();
            IdempotencyRecord fresh = new IdempotencyRecord(
                    operationId, tenantId, principalId, endpoint, idempotencyKey,
                    requestFingerprint, 0, null, Instant.now(),
                    Instant.now().plus(DEFAULT_RETENTION));
            byOperation.put(operationId, fresh);
            byKey.put(compositeKey, fresh);
            return new Replay.ReplayMiss(operationId);
        }

        @Override
        public synchronized void complete(UUID operationId, int responseStatus, String responseBodyJson) {
            IdempotencyRecord record = byOperation.get(operationId);
            if (record == null) return;
            IdempotencyRecord updated = new IdempotencyRecord(
                    record.id(), record.tenantId(), record.principalId(), record.endpoint(),
                    record.idempotencyKey(), record.requestFingerprintSha256(),
                    responseStatus, responseBodyJson, record.createdAt(), record.expiresAt());
            byOperation.put(operationId, updated);
            byKey.put(compositeKey(record.tenantId(), record.principalId(), record.endpoint(), record.idempotencyKey()), updated);
        }

        @Override
        public synchronized void fail(UUID operationId) {
            IdempotencyRecord record = byOperation.remove(operationId);
            if (record != null) {
                byKey.remove(compositeKey(record.tenantId(), record.principalId(), record.endpoint(), record.idempotencyKey()));
            }
        }

        private static String compositeKey(UUID tenantId, UUID principalId, String endpoint, String key) {
            return tenantId + "|" + principalId + "|" + endpoint + "|" + key;
        }
    }
}
