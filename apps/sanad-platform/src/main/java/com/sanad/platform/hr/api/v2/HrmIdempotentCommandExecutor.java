package com.sanad.platform.hr.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.idempotency.IdempotencyBeginResult;
import com.sanad.platform.idempotency.RequestIdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HRM-G0 / WS5 Task 3 — idempotent command plumbing for critical v2 POSTs.
 *
 * <p>Owns replay/conflict resolution over the durable HR idempotency store
 * (WS4 Task 8). Business services never parse HTTP headers: the controller
 * extracts the {@code Idempotency-Key} and computes the request fingerprint,
 * then delegates here.
 *
 * <p>Semantics (all deterministic, never exception-swallowing):
 * <ul>
 *   <li>first seen → run the command, persist the completed response</li>
 *   <li>same key + same fingerprint + completed → replay the STORED response
 *       (byte-identical values, no re-execution, no duplicated side effects)</li>
 *   <li>same key + different fingerprint → {@code HRM_IDEMPOTENCY_CONFLICT}</li>
 *   <li>same key still in flight → {@code HRM_IDEMPOTENCY_CONFLICT}
 *       (deterministic retry-later)</li>
 *   <li>command failure → operation marked failed and the exception
 *       propagates unchanged</li>
 * </ul>
 */
@Service
public class HrmIdempotentCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HrmIdempotentCommandExecutor.class);

    private final RequestIdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public HrmIdempotentCommandExecutor(RequestIdempotencyService idempotency, ObjectMapper objectMapper) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Execute the command under the (tenant, principal, operation, key)
     * idempotency boundary. On replay, the stored response is deserialized to
     * {@code responseType} and returned WITHOUT invoking the command.
     */
    public <T> T execute(UUID tenantId, UUID principalId, String operation, String idempotencyKey,
                         String requestFingerprint, Class<T> responseType, Supplier<T> command) {
        IdempotencyBeginResult begin = idempotency.begin(
                tenantId, principalId, operation, idempotencyKey, requestFingerprint);

        if (begin.alreadyExists()) {
            if (begin.priorStatus() == null) {
                throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: operation is still in flight "
                        + "for key " + idempotencyKey);
            }
            try {
                return objectMapper.readValue(begin.priorResponse(), responseType);
            } catch (Exception unreadable) {
                throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: stored replay payload is unreadable "
                        + "for key " + idempotencyKey);
            }
        }

        try {
            T result = command.get();
            try {
                idempotency.complete(begin.operationId(), 200, objectMapper.writeValueAsString(result));
            } catch (Exception serializationFailure) {
                idempotency.fail(begin.operationId());
                throw new IllegalStateException("HRM_IDEMPOTENCY_COMPLETE_FAILED: unable to persist replay payload "
                        + "for key " + idempotencyKey, serializationFailure);
            }
            return result;
        } catch (RuntimeException commandFailure) {
            try {
                idempotency.fail(begin.operationId());
            } catch (RuntimeException failFailure) {
                log.warn("HRM idempotency fail() could not mark operation {} for key {}: {}",
                        begin.operationId(), idempotencyKey, failFailure.getMessage());
            }
            throw commandFailure;
        }
    }

    /** Stable SHA-256 fingerprint of the operation and raw request body. */
    public static String fingerprint(String operation, String rawBody) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((operation + "|" + Objects.requireNonNullElse(rawBody, "")).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
