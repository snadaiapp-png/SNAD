package com.sanad.platform.idempotency.service;

import com.sanad.platform.shared.api.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stage 05A.2.3 §9-10 — Transactional idempotent command executor.
 *
 * <p>Uses a separate {@link IdempotentBusinessTransactionExecutor} bean for
 * Transaction B to avoid self-invocation issues with @Transactional.</p>
 *
 * <ol>
 *   <li><b>Transaction A</b> (REQUIRES_NEW): Reserve the idempotency key.</li>
 *   <li><b>Transaction B</b> (REQUIRED, via separate bean): Business + audit +
 *       completion all commit together.</li>
 *   <li><b>Transaction C</b> (REQUIRES_NEW, only on failure): Mark as
 *       FAILED_RETRYABLE.</li>
 * </ol>
 */
@Component
public class IdempotentCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentCommandExecutor.class);

    private final IdempotencyService idempotencyService;
    private final IdempotencyReplaySerializer serializer;
    private final IdempotentBusinessTransactionExecutor businessTxExecutor;

    public IdempotentCommandExecutor(IdempotencyService idempotencyService,
                                       IdempotencyReplaySerializer serializer,
                                       IdempotentBusinessTransactionExecutor businessTxExecutor) {
        this.idempotencyService = idempotencyService;
        this.serializer = serializer;
        this.businessTxExecutor = businessTxExecutor;
    }

    public <T> IdempotentHttpResult<T> execute(
            OperationMetadata operationMetadata,
            String idempotencyKey,
            String request,
            String method,
            String queryString,
            Supplier<T> businessAction) {

        // Transaction A: Reserve (commits independently)
        IdempotencyService.ReservationResult reservation = idempotencyService.reserveOrReplay(
                idempotencyKey,
                operationMetadata.operation(),
                operationMetadata.route(),
                operationMetadata.resourceType(),
                method,
                request,
                queryString);

        if (reservation.shouldReplay()) {
            return buildReplayResult(reservation.record());
        }
        if (reservation.type() == IdempotencyService.ReservationType.CONFLICT) {
            throw new IdempotencyPayloadConflictException(reservation.message());
        }
        if (reservation.type() == IdempotencyService.ReservationType.IN_PROGRESS) {
            throw new IdempotencyInProgressException(reservation.message());
        }
        if (reservation.type() == IdempotencyService.ReservationType.EXPIRED) {
            throw new IdempotencyExpiredException(reservation.message());
        }

        // Transaction B: Business execution + audit + completion (via separate bean)
        var rec = reservation.record();
        String leaseOwner = MDC.get(RequestIdFilter.MDC_KEY);
        if (leaseOwner == null || leaseOwner.isBlank()) {
            leaseOwner = "unknown-" + UUID.randomUUID();
        }
        long leaseVersion = reservation.leaseVersion();

        try {
            return businessTxExecutor.executeBusinessTransaction(
                    rec, leaseOwner, leaseVersion,
                    operationMetadata.operation(),
                    operationMetadata.resourceType(),
                    businessAction);
        } catch (Exception e) {
            // Transaction C: Mark as FAILED_RETRYABLE
            try {
                idempotencyService.fail(rec.getId(), leaseOwner, leaseVersion,
                        "SANAD-IDEMP-EXEC", e.getClass().getSimpleName() + ": " + e.getMessage(),
                        true);
            } catch (Exception failEx) {
                log.error("Idempotency fail() threw for record {}: {}", rec.getId(), failEx.getMessage());
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> IdempotentHttpResult<T> buildReplayResult(
            com.sanad.platform.idempotency.domain.IdempotencyRecord rec) {
        // Stage 05A.2.3 §12 — Deserialize stored body for replay.
        // The stored body is the canonical JSON of the original response.
        return new IdempotentHttpResult<>(
                rec.getResponseStatus() != null ? rec.getResponseStatus() : 200,
                Map.of(),
                rec.getResponseBody(),
                null,
                true, // replayed
                0, // leaseVersion not relevant for replay
                null // businessResult not available on replay — controller
                     // should use responseBody directly for replay responses
        );
    }

    public record OperationMetadata(
            String operation,
            String route,
            String resourceType
    ) {}

    public static class IdempotencyPayloadConflictException extends RuntimeException {
        public IdempotencyPayloadConflictException(String message) { super(message); }
    }

    public static class IdempotencyInProgressException extends RuntimeException {
        public IdempotencyInProgressException(String message) { super(message); }
    }

    public static class IdempotencyExpiredException extends RuntimeException {
        public IdempotencyExpiredException(String message) { super(message); }
    }
}
