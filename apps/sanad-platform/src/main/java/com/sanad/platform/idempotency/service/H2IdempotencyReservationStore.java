package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.domain.IdempotencyStatus;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.1 §5 — H2-compatible reservation store.
 *
 * <p>H2 does not support ON CONFLICT or RETURNING clauses. This store
 * uses find-or-insert with the JPA repository. It is safe for
 * single-threaded local development but MUST NOT be used in production
 * or CI (which uses PostgreSQL with the Postgres store).</p>
 *
 * <p>Active under {@code local} and {@code test-local} profiles.</p>
 */
@Component
@Profile({"local", "test-local"})
public class H2IdempotencyReservationStore implements IdempotencyReservationStore {

    private final IdempotencyRecordRepository repository;

    public H2IdempotencyReservationStore(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> atomicReserve(
            UUID tenantId, String idempotencyKey, String operation,
            String route, String resourceType, String requestFingerprint,
            Instant expiresAt, String leaseOwnerRequestId, Instant leaseExpiresAt) {

        // H2 fallback: check existing, insert if not found.
        Optional<IdempotencyRecord> existing = repository
                .findByTenantOperationRouteKey(tenantId, operation, route, idempotencyKey);
        if (existing.isPresent()) {
            return Optional.empty();
        }

        IdempotencyRecord newRec = new IdempotencyRecord(
                tenantId, idempotencyKey, operation, route, requestFingerprint,
                IdempotencyStatus.PROCESSING, expiresAt);
        newRec.setResourceType(resourceType);
        newRec.setLockedAt(Instant.now());
        newRec.setProcessingStartedAt(Instant.now());
        newRec.setOwnerRequestId(leaseOwnerRequestId);
        newRec.setLeaseOwnerRequestId(leaseOwnerRequestId);
        newRec.setLeaseExpiresAt(leaseExpiresAt);
        newRec.setAttemptCount(1);
        newRec.setLastAttemptAt(Instant.now());
        newRec.setLeaseVersion(1L);
        repository.save(newRec);
        return Optional.of(newRec.getId());
    }

    @Override
    public Optional<IdempotencyRecord> atomicTakeoverLease(
            UUID recordId, String newOwnerRequestId, Instant newLeaseExpiresAt) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) return Optional.empty();

        if (rec.getStatus() != IdempotencyStatus.FAILED_RETRYABLE
                && (rec.getLeaseExpiresAt() == null
                    || rec.getLeaseExpiresAt().isAfter(Instant.now()))) {
            return Optional.empty();
        }

        rec.setStatus(IdempotencyStatus.PROCESSING);
        rec.setLeaseOwnerRequestId(newOwnerRequestId);
        rec.setLeaseExpiresAt(newLeaseExpiresAt);
        rec.setLeaseVersion((rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion()) + 1);
        rec.setAttemptCount((rec.getAttemptCount() == null ? 0 : rec.getAttemptCount()) + 1);
        rec.setLastAttemptAt(Instant.now());
        repository.save(rec);
        return Optional.of(rec);
    }

    @Override
    public void atomicComplete(
            UUID recordId, String leaseOwnerRequestId, long leaseVersion,
            int responseStatus, String responseHeaders, String responseBody) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        }
        long actualVersion = rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion();
        if (!leaseOwnerRequestId.equals(rec.getLeaseOwnerRequestId())
                || actualVersion != leaseVersion) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actualVersion);
        }
        rec.setStatus(IdempotencyStatus.COMPLETED);
        rec.setResponseStatus(responseStatus);
        rec.setResponseHeaders(responseHeaders);
        rec.setResponseBody(responseBody);
        rec.setCompletedAt(Instant.now());
        repository.save(rec);
    }

    @Override
    public void atomicFail(
            UUID recordId, String leaseOwnerRequestId, long leaseVersion,
            String errorCode, String errorDetail, boolean retryable) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        }
        long actualVersion = rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion();
        if (!leaseOwnerRequestId.equals(rec.getLeaseOwnerRequestId())
                || actualVersion != leaseVersion) {
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actualVersion);
        }
        rec.setStatus(retryable ? IdempotencyStatus.FAILED_RETRYABLE : IdempotencyStatus.FAILED_FINAL);
        rec.setErrorCode(errorCode);
        rec.setErrorDetail(errorDetail);
        rec.setCompletedAt(Instant.now());
        repository.save(rec);
    }

    @Override
    public Optional<IdempotencyRecord> findByTenantOperationRouteKey(
            UUID tenantId, String operation, String route, String key) {
        return repository.findByTenantOperationRouteKey(tenantId, operation, route, key);
    }
}
