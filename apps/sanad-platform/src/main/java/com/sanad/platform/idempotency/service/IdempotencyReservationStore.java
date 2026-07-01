package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.1 §5 — Persistence adapter for idempotency reservation.
 *
 * <p>Separates PostgreSQL-specific atomic operations (ON CONFLICT,
 * RETURNING) from the H2-compatible fallback. The correct
 * implementation is selected by Spring profile — no runtime
 * exception-based detection.</p>
 */
public interface IdempotencyReservationStore {

    /**
     * Atomically reserves an idempotency key. If the key already exists,
     * returns empty — caller must re-read the existing record.
     *
     * @return the new record ID if the INSERT succeeded, or empty if
     *         the key already exists
     */
    Optional<UUID> atomicReserve(
            UUID tenantId,
            String idempotencyKey,
            String operation,
            String route,
            String resourceType,
            String requestFingerprint,
            Instant expiresAt,
            String leaseOwnerRequestId,
            Instant leaseExpiresAt);

    /**
     * Atomically takes over a stale or failed lease. Increments
     * lease_version and returns the new record state.
     *
     * @return the updated record if takeover succeeded, or empty if
     *         the record is still actively processing
     */
    Optional<IdempotencyRecord> atomicTakeoverLease(
            UUID recordId,
            String newOwnerRequestId,
            Instant newLeaseExpiresAt);

    /**
     * Atomically completes the record, matching lease owner and version.
     * Throws StaleIdempotencyLeaseException if the lease doesn't match.
     */
    void atomicComplete(
            UUID recordId,
            String leaseOwnerRequestId,
            long leaseVersion,
            int responseStatus,
            String responseHeaders,
            String responseBody);

    /**
     * Atomically marks the record as failed, matching lease owner and version.
     * Throws StaleIdempotencyLeaseException if the lease doesn't match.
     */
    void atomicFail(
            UUID recordId,
            String leaseOwnerRequestId,
            long leaseVersion,
            String errorCode,
            String errorDetail,
            boolean retryable);

    /**
     * Reads the existing record by tenant+operation+route+key.
     */
    Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            UUID tenantId,
            String operation,
            String route,
            String idempotencyKey);
}
