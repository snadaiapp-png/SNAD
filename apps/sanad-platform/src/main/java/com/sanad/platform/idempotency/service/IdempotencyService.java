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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 05A.2.1 — Idempotency service using profile-selected persistence
 * adapter.
 *
 * <p>No more catch(Exception) H2 fallback — the correct store is selected
 * by Spring profile at startup. PostgreSQL failures propagate; they are
 * never masked by H2 fallback logic.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final IdempotencyRecordRepository repository;
    private final IdempotencyReservationStore store;
    private final RequestFingerprintService fingerprintService;
    private final TenantContextProvider contextProvider;
    private final AuditRedactionService redactionService;

    public IdempotencyService(IdempotencyRecordRepository repository,
                               IdempotencyReservationStore store,
                               RequestFingerprintService fingerprintService,
                               TenantContextProvider contextProvider,
                               AuditRedactionService redactionService) {
        this.repository = repository;
        this.store = store;
        this.fingerprintService = fingerprintService;
        this.contextProvider = contextProvider;
        this.redactionService = redactionService;
    }

    public enum ReservationType { NEW, REPLAY, CONFLICT, IN_PROGRESS, EXPIRED }

    public record ReservationResult(
            ReservationType type,
            IdempotencyRecord record,
            String message,
            long leaseVersion
    ) {
        public boolean shouldExecute() { return type == ReservationType.NEW; }
        public boolean shouldReplay() { return type == ReservationType.REPLAY; }
        public ReservationResult(ReservationType type, IdempotencyRecord record, String message) {
            this(type, record, message, record != null && record.getLeaseVersion() != null
                    ? record.getLeaseVersion() : 0);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
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
        Instant leaseExpiresAt = now.plus(LEASE_DURATION);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        // Stage 05A.2.1 §5 — Atomic reservation via profile-selected store.
        Optional<UUID> insertedId = store.atomicReserve(
                tenantId, idempotencyKey, operation, route, resourceType,
                fingerprint, expiresAt, requestId, leaseExpiresAt);

        if (insertedId.isPresent()) {
            IdempotencyRecord rec = repository.findById(insertedId.get()).orElseThrow();
            return new ReservationResult(ReservationType.NEW, rec, "New reservation created");
        }

        // The row already exists — re-read.
        Optional<IdempotencyRecord> existing = store.findByTenantOperationRouteKey(
                tenantId, operation, route, idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (rec.getExpiresAt().isBefore(now)) {
                return new ReservationResult(ReservationType.EXPIRED, rec,
                        "Idempotency record expired at " + rec.getExpiresAt());
            }
            if (!rec.getRequestFingerprint().equals(fingerprint)) {
                return new ReservationResult(ReservationType.CONFLICT, rec,
                        "Idempotency key reused with a different payload");
            }
            if (rec.getStatus() == IdempotencyStatus.COMPLETED) {
                return new ReservationResult(ReservationType.REPLAY, rec,
                        "Replaying completed response");
            }
            if (rec.getStatus() == IdempotencyStatus.PROCESSING) {
                if (rec.getLeaseExpiresAt() != null && rec.getLeaseExpiresAt().isBefore(now)) {
                    Optional<IdempotencyRecord> taken = store.atomicTakeoverLease(
                            rec.getId(), requestId, leaseExpiresAt);
                    if (taken.isPresent()) {
                        return new ReservationResult(ReservationType.NEW, taken.get(),
                                "Lease takeover — re-executing after expired processing lease");
                    }
                }
                return new ReservationResult(ReservationType.IN_PROGRESS, rec,
                        "Request is still processing — retry later");
            }
            if (rec.getStatus() == IdempotencyStatus.FAILED_RETRYABLE) {
                Optional<IdempotencyRecord> taken = store.atomicTakeoverLease(
                        rec.getId(), requestId, leaseExpiresAt);
                if (taken.isPresent()) {
                    return new ReservationResult(ReservationType.NEW, taken.get(),
                            "Re-executing after retryable failure");
                }
                return new ReservationResult(ReservationType.IN_PROGRESS, rec,
                        "Concurrent retry detected — retry later");
            }
            if (rec.getStatus() == IdempotencyStatus.FAILED_FINAL) {
                return new ReservationResult(ReservationType.CONFLICT, rec,
                        "Idempotency key previously failed permanently");
            }
        }

        return new ReservationResult(ReservationType.IN_PROGRESS, null,
                "Unexpected state — retry later");
    }

    /**
     * Stage 05A.2.1 §7 — Atomic completion with lease fencing.
     * Throws StaleIdempotencyLeaseException if the lease doesn't match.
     * Uses REQUIRES_NEW for standalone completion (Transaction C path).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID recordId, String leaseOwnerRequestId, long leaseVersion,
                          int responseStatus, String responseHeaders, String responseBody) {
        store.atomicComplete(recordId, leaseOwnerRequestId, leaseVersion,
                responseStatus, sanitizeHeaders(responseHeaders),
                redactionService.redactJson(responseBody));
    }

    /**
     * Stage 05A.2.3 §10 — Completion within an existing transaction (Transaction B).
     * Uses MANDATORY propagation — must be called within an active transaction.
     * This ensures business mutation, audit, and completion all commit together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeInTransaction(UUID recordId, String leaseOwnerRequestId, long leaseVersion,
                          int responseStatus, String responseHeaders, String responseBody) {
        store.atomicComplete(recordId, leaseOwnerRequestId, leaseVersion,
                responseStatus, sanitizeHeaders(responseHeaders),
                redactionService.redactJson(responseBody));
    }

    /**
     * Stage 05A.2.1 §7 — Atomic failure with lease fencing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID recordId, String leaseOwnerRequestId, long leaseVersion,
                      String errorCode, String errorDetail, boolean retryable) {
        store.atomicFail(recordId, leaseOwnerRequestId, leaseVersion,
                errorCode, redactErrorDetail(errorDetail), retryable);
    }

    private UUID requireTenantId() {
        TenantContext ctx = contextProvider.currentContext().orElse(null);
        if (ctx == null || ctx.tenantId() == null) {
            throw new IllegalStateException("Idempotency requires a tenant context");
        }
        return ctx.tenantId();
    }

    private String sanitizeHeaders(String headers) {
        if (headers == null) return null;
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
        // Stage 05A.2.1 §15 — Redact plaintext patterns
        String redacted = detail;
        redacted = redacted.replaceAll("(?i)(password=)[^\\s,;]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)(token=)[^\\s,;]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)(authorization:)[^\\s,;]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)(secret=)[^\\s,;]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)(apikey=)[^\\s,;]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]");
        return redacted;
    }
}
