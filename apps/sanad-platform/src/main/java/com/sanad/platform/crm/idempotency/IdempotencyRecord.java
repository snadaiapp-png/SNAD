package com.sanad.platform.crm.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * CRM API Contract — Idempotency Record.
 * <p>
 * Persisted record of a previously-processed idempotent request. The
 * record allows the API to replay the exact same response when a client
 * retries a request with the same {@code Idempotency-Key}.
 * <p>
 * Storage rules (enforced by {@link IdempotencyService}):
 *   - Tenant-scoped: a key used by Tenant A is independent of the same
 *     key used by Tenant B.
 *   - Principal-scoped: a key used by user X is independent of the same
 *     key used by user Y in the same tenant.
 *   - Endpoint-scoped: a key used for {@code POST /accounts} is
 *     independent of the same key used for {@code POST /contacts}.
 *   - Payload-bound: reusing a key with a different payload yields
 *     {@code 409 Conflict} ({@code CRM_IDEMPOTENCY_CONFLICT}).
 *   - Time-bounded: records have a retention window (default 24h) and
 *     are eligible for cleanup afterwards.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public record IdempotencyRecord(
        UUID id,
        UUID tenantId,
        UUID principalId,
        String endpoint,
        String idempotencyKey,
        String requestFingerprintSha256,
        int responseStatus,
        String responseBodyJson,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
