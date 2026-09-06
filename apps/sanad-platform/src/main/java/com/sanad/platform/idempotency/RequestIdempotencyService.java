package com.sanad.platform.idempotency;

import java.util.UUID;

/**
 * Shared idempotency contract.
 *
 * <p>Shared abstraction only. Durable storage ownership remains producer-local
 * in future workstreams. This contract does NOT force CRM implementation
 * packages into HR/Platform contract surface.</p>
 */
public interface RequestIdempotencyService {

    /**
     * Begins an idempotent operation. If the key was already used, returns
     * the prior result. Otherwise begins a new operation.
     */
    IdempotencyBeginResult begin(UUID tenantId, UUID principalId, String operation,
                                  String idempotencyKey, String requestFingerprint);

    /**
     * Completes the operation with the given status code and response body.
     */
    void complete(UUID operationId, int statusCode, String responseBody);

    /**
     * Fails the operation (e.g., unhandled exception).
     */
    void fail(UUID operationId);
}
