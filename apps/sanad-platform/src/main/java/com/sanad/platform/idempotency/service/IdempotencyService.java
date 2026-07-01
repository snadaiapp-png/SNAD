package com.sanad.platform.idempotency.service;

import com.sanad.platform.audit.service.AuditRedactionService;
import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.domain.IdempotencyStatus;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import com.sanad.platform.security.tenant.TenantContext;
import com.sanad.platform.security.tenant.TenantContextProvider;
import com.sanad.platform.shared.api.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05 §13-19 — Persistent idempotency enforcement.
 *
 * <p>Provides three operations:</p>
 * <ol>
 *   <li>{@link #reserveOrReplay} — called BEFORE the business operation.
 *       Returns a {@link ReservationResult} indicating whether the
 *       caller should execute the business operation (NEW), replay
 *       a stored response (REPLAY), reject due to payload mismatch
 *       (CONFLICT), or wait because another request is processing
 *       (IN_PROGRESS).</li>
 *   <li>{@link #complete} — called AFTER the business operation
 *       succeeds. Stores the response for future replays.</li>
 *   <li>{@link #fail} — called AFTER the business operation fails.
 *       Records the failure and marks the record as retryable or
 *       final.</li>
 * </ol>
 *
 * <h2>Concurrency safety</h2>
 * <p>The unique constraint on {@code (tenant_id, operation, route,
 * idempotency_key)} is the primary concurrency guard. When two
 * concurrent requests try to reserve the same key, one succeeds
 * (INSERT) and the other gets a {@link DataIntegrityViolationException}
 * which is caught and translated to an IN_PROGRESS result.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final RequestFingerprintService fingerprintService;
    private final TenantContextProvider contextProvider;
    private final AuditRedactionService redactionService;

    public IdempotencyService(IdempotencyRecordRepository repository,
                               RequestFingerprintService fingerprintService,
                               TenantContextProvider contextProvider,
                               AuditRedactionService redactionService) {
        this.repository = repository;
        this.fingerprintService = fingerprintService;
        this.contextProvider = contextProvider;
        this.redactionService = redactionService;
    }

    public enum ReservationType { NEW, REPLAY, CONFLICT, IN_PROGRESS, EXPIRED }

    public record ReservationResult(
            ReservationType type,
            IdempotencyRecord record,
            String message
    ) {
        public boolean shouldExecute() { return type == ReservationType.NEW; }
        public boolean shouldReplay() { return type == ReservationType.REPLAY; }
    }

    /**
     * Reserves an idempotency key for the given operation, or returns
     * a replay/conflict/in-progress result.
     *
     * <p>Runs in {@code REQUIRES_NEW} so that the reservation commits
     * independently of the business operation. This ensures the
     * reservation is visible to concurrent requests immediately.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResult reserveOrReplay(
            String idempotencyKey,
            String operation,
            String route,
            String resourceType,
            String method,
            String body,
            String queryString) {
        UUID tenantId = requireTenantId();
        String fingerprint = fingerprintService.compute(method, route, body, queryString, tenantId, operation);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_TTL);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        // Check for an existing record.
        Optional<IdempotencyRecord> existing = repository
                .findByTenantOperationRouteKey(tenantId, operation, route, idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            // Expired?
            if (rec.getExpiresAt().isBefore(now)) {
                return new ReservationResult(ReservationType.EXPIRED, rec,
                        "Idempotency record expired at " + rec.getExpiresAt());
            }
            // Payload mismatch?
            if (!rec.getRequestFingerprint().equals(fingerprint)) {
                return new ReservationResult(ReservationType.CONFLICT, rec,
                        "Idempotency key reused with a different payload");
            }
            // Already completed → replay.
            if (rec.getStatus() == IdempotencyStatus.COMPLETED) {
                return new ReservationResult(ReservationType.REPLAY, rec,
                        "Replaying completed response");
            }
            // Still processing → tell the client to wait.
            if (rec.getStatus() == IdempotencyStatus.PROCESSING) {
                return new ReservationResult(ReservationType.IN_PROGRESS, rec,
                        "Request is still processing — retry later");
            }
            // Failed retryable → allow re-execution.
            if (rec.getStatus() == IdempotencyStatus.FAILED_RETRYABLE) {
                rec.setStatus(IdempotencyStatus.PROCESSING);
                rec.setProcessingStartedAt(now);
                rec.setOwnerRequestId(requestId);
                repository.save(rec);
                return new ReservationResult(ReservationType.NEW, rec,
                        "Re-executing after retryable failure");
            }
            // Failed final → reject.
            if (rec.getStatus() == IdempotencyStatus.FAILED_FINAL) {
                return new ReservationResult(ReservationType.CONFLICT, rec,
                        "Idempotency key previously failed permanently");
            }
        }

        // No existing record → INSERT. Concurrent inserts collide on the
        // unique constraint; the loser gets IN_PROGRESS.
        IdempotencyRecord newRec = new IdempotencyRecord(
                tenantId, idempotencyKey, operation, route, fingerprint,
                IdempotencyStatus.PROCESSING, expiresAt);
        newRec.setResourceType(resourceType);
        newRec.setLockedAt(now);
        newRec.setProcessingStartedAt(now);
        newRec.setOwnerRequestId(requestId);
        try {
            repository.save(newRec);
            repository.flush();
            return new ReservationResult(ReservationType.NEW, newRec,
                    "New reservation created");
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotency concurrent insert detected: tenant={} key={} operation={}",
                    tenantId, idempotencyKey, operation);
            // Re-read the winning record.
            IdempotencyRecord winner = repository
                    .findByTenantOperationRouteKey(tenantId, operation, route, idempotencyKey)
                    .orElse(null);
            return new ReservationResult(ReservationType.IN_PROGRESS, winner,
                    "Concurrent request detected — retry later");
        }
    }

    /**
     * Marks the reservation as completed and stores the response for replay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID recordId, int responseStatus, String responseHeaders,
                          String responseBody) {
        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) {
            log.warn("Idempotency complete: record not found (id={})", recordId);
            return;
        }
        rec.setStatus(IdempotencyStatus.COMPLETED);
        rec.setResponseStatus(responseStatus);
        rec.setResponseHeaders(sanitizeHeaders(responseHeaders));
        // Stage 05A.1 §24 — Redact sensitive fields from response body before persistence.
        rec.setResponseBody(redactionService.redactJson(responseBody));
        rec.setCompletedAt(Instant.now());
        repository.save(rec);
    }

    /**
     * Marks the reservation as failed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID recordId, String errorCode, String errorDetail, boolean retryable) {
        IdempotencyRecord rec = repository.findById(recordId).orElse(null);
        if (rec == null) {
            log.warn("Idempotency fail: record not found (id={})", recordId);
            return;
        }
        rec.setStatus(retryable ? IdempotencyStatus.FAILED_RETRYABLE : IdempotencyStatus.FAILED_FINAL);
        rec.setErrorCode(errorCode);
        // Stage 05A.1 §24 — Redact sensitive data from error detail.
        rec.setErrorDetail(redactErrorDetail(errorDetail));
        rec.setCompletedAt(Instant.now());
        repository.save(rec);
    }

    private UUID requireTenantId() {
        TenantContext ctx = contextProvider.currentContext().orElse(null);
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalStateException("Idempotency requires a tenant context");
        }
        return ctx.tenantId();
    }

    /**
     * Strips sensitive headers (Set-Cookie, Authorization) from the stored
     * response headers to prevent leaking credentials on replay.
     */
    private String sanitizeHeaders(String headers) {
        if (headers == null) return null;
        // Simple line-based filter. Headers are stored as "Key: Value\n...".
        StringBuilder sb = new StringBuilder();
        for (String line : headers.split("\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("set-cookie:") || lower.startsWith("authorization:")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private String redactErrorDetail(String detail) {
        if (detail == null) return null;
        if (detail.trim().startsWith("{")) {
            return redactionService.redactJson(detail);
        }
        return detail;
    }
}
