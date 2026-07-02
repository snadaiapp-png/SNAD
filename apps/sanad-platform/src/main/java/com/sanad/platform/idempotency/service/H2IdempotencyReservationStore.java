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
 * Stage 05A.2.7 — H2-compatible store. No RLS, no ON CONFLICT.
 */
@Component
@Profile({"local", "test-local"})
public class H2IdempotencyReservationStore implements IdempotencyReservationStore {

    private final IdempotencyRecordRepository repository;

    public H2IdempotencyReservationStore(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LeaseGrant> atomicReserve(
            UUID tenantId, String idempotencyKey, String operation,
            String route, String resourceType, String requestFingerprint,
            Instant expiresAt, String leaseOwnerRequestId, Instant leaseExpiresAt) {

        Optional<IdempotencyRecord> existing = repository
                .findByTenantOperationRouteKey(tenantId, operation, route, idempotencyKey);
        if (existing.isPresent()) return Optional.empty();

        IdempotencyRecord rec = new IdempotencyRecord(
                tenantId, idempotencyKey, operation, route, requestFingerprint,
                IdempotencyStatus.PROCESSING, expiresAt);
        rec.setResourceType(resourceType);
        rec.setLockedAt(Instant.now());
        rec.setProcessingStartedAt(Instant.now());
        rec.setOwnerRequestId(leaseOwnerRequestId);
        rec.setLeaseOwnerRequestId(leaseOwnerRequestId);
        rec.setLeaseExpiresAt(leaseExpiresAt);
        rec.setAttemptCount(1);
        rec.setLastAttemptAt(Instant.now());
        rec.setLeaseVersion(1L);
        repository.save(rec);
        return Optional.of(new LeaseGrant(rec.getId(), tenantId, leaseOwnerRequestId,
                1L, "PROCESSING", requestFingerprint, leaseExpiresAt));
    }

    @Override
    public Optional<LeaseGrant> atomicTakeoverLease(
            UUID recordId, UUID tenantId,
            String newOwnerRequestId, Instant newLeaseExpiresAt) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) return Optional.empty();
        if (!rec.getTenantId().equals(tenantId)) return Optional.empty();

        if (rec.getStatus() != IdempotencyStatus.FAILED_RETRYABLE
                && (rec.getLeaseExpiresAt() == null
                    || rec.getLeaseExpiresAt().isAfter(Instant.now()))) {
            return Optional.empty();
        }

        rec.setStatus(IdempotencyStatus.PROCESSING);
        rec.setLeaseOwnerRequestId(newOwnerRequestId);
        rec.setLeaseExpiresAt(newLeaseExpiresAt);
        long newVersion = (rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion()) + 1;
        rec.setLeaseVersion(newVersion);
        rec.setAttemptCount((rec.getAttemptCount() == null ? 0 : rec.getAttemptCount()) + 1);
        rec.setLastAttemptAt(Instant.now());
        repository.save(rec);
        return Optional.of(new LeaseGrant(rec.getId(), rec.getTenantId(), newOwnerRequestId,
                newVersion, "PROCESSING", rec.getRequestFingerprint(), newLeaseExpiresAt));
    }

    @Override
    public void atomicComplete(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            int responseStatus, String responseHeaders, String responseBody) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        long actual = rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion();
        if (!leaseOwnerRequestId.equals(rec.getLeaseOwnerRequestId()) || actual != leaseVersion)
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actual);

        rec.setStatus(IdempotencyStatus.COMPLETED);
        rec.setResponseStatus(responseStatus);
        rec.setResponseHeaders(responseHeaders);
        rec.setResponseBody(responseBody);
        rec.setCompletedAt(Instant.now());
        repository.save(rec);
    }

    @Override
    public void atomicFail(
            UUID recordId, UUID tenantId, String leaseOwnerRequestId, long leaseVersion,
            String errorCode, String errorDetail, boolean retryable) {

        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) throw new StaleIdempotencyLeaseException(recordId, leaseVersion, -1);
        long actual = rec.getLeaseVersion() == null ? 0 : rec.getLeaseVersion();
        if (!leaseOwnerRequestId.equals(rec.getLeaseOwnerRequestId()) || actual != leaseVersion)
            throw new StaleIdempotencyLeaseException(recordId, leaseVersion, actual);

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
