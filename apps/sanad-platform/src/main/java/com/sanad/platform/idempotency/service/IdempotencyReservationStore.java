package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.6 — Persistence adapter for idempotency reservation.
 *
 * <p>Returns {@link LeaseGrant} directly from SQL RETURNING — no JPA re-read.</p>
 */
public interface IdempotencyReservationStore {

    Optional<LeaseGrant> atomicReserve(
            UUID tenantId,
            String idempotencyKey,
            String operation,
            String route,
            String resourceType,
            String requestFingerprint,
            Instant expiresAt,
            String leaseOwnerRequestId,
            Instant leaseExpiresAt);

    Optional<LeaseGrant> atomicTakeoverLease(
            UUID recordId,
            String newOwnerRequestId,
            Instant newLeaseExpiresAt);

    void atomicComplete(
            UUID recordId,
            UUID tenantId,
            String leaseOwnerRequestId,
            long leaseVersion,
            int responseStatus,
            String responseHeaders,
            String responseBody);

    void atomicFail(
            UUID recordId,
            UUID tenantId,
            String leaseOwnerRequestId,
            long leaseVersion,
            String errorCode,
            String errorDetail,
            boolean retryable);

    Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            UUID tenantId,
            String operation,
            String route,
            String idempotencyKey);
}
