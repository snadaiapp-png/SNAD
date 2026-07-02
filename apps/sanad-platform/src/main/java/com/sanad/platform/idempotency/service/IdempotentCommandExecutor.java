package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.shared.api.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stage 05A.2.4 — Transactional idempotent command executor.
 *
 * <p>Uses three separate beans for three transactions:</p>
 * <ol>
 *   <li><b>Transaction A</b> (IdempotencyReservationTransactionExecutor, REQUIRES_NEW):
 *       Reserve the idempotency key.</li>
 *   <li><b>Transaction B</b> (IdempotentBusinessTransactionExecutor, REQUIRED):
 *       Business + audit + completion all commit together.</li>
 *   <li><b>Transaction C</b> (IdempotencyFailureTransactionExecutor, REQUIRES_NEW):
 *       Mark as FAILED_RETRYABLE after Transaction B rolls back.</li>
 * </ol>
 */
@Component
public class IdempotentCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentCommandExecutor.class);

    private final IdempotencyReservationTransactionExecutor reservationExecutor;
    private final IdempotentBusinessTransactionExecutor businessTxExecutor;
    private final IdempotencyFailureTransactionExecutor failureExecutor;
    private final IdempotencyReplaySerializer serializer;

    public IdempotentCommandExecutor(IdempotencyReservationTransactionExecutor reservationExecutor,
                                       IdempotentBusinessTransactionExecutor businessTxExecutor,
                                       IdempotencyFailureTransactionExecutor failureExecutor,
                                       IdempotencyReplaySerializer serializer) {
        this.reservationExecutor = reservationExecutor;
        this.businessTxExecutor = businessTxExecutor;
        this.failureExecutor = failureExecutor;
        this.serializer = serializer;
    }

    public <T> IdempotentHttpResult<T> execute(
            OperationMetadata operationMetadata,
            String idempotencyKey,
            String request,
            String method,
            String queryString,
            Supplier<T> businessAction) {

        // Transaction A: Reserve (commits independently via REQUIRES_NEW)
        IdempotencyService.ReservationResult reservation = reservationExecutor.reserve(
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

        // Get verified lease owner BEFORE any transaction
        String leaseOwner = MDC.get(RequestIdFilter.MDC_KEY);
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalStateException(
                    "Cannot execute idempotent command: no verified request identity (MDC request ID is null)");
        }
        long leaseVersion = reservation.leaseVersion();
        IdempotencyRecord rec = reservation.record();

        // Transaction B: Business execution + audit + completion (via separate bean)
        try {
            return businessTxExecutor.executeBusinessTransaction(
                    rec, leaseOwner, leaseVersion,
                    operationMetadata.operation(),
                    operationMetadata.resourceType(),
                    businessAction);
        } catch (Exception e) {
            // Transaction C: Mark as FAILED_RETRYABLE (via separate bean, REQUIRES_NEW)
            try {
                failureExecutor.failReservation(
                        rec.getId(), leaseOwner, leaseVersion,
                        "SANAD-IDEMP-EXEC",
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Exception failEx) {
                log.error("Transaction C failed for record {}: {}", rec.getId(), failEx.getMessage());
                // Don't swallow — throw composite exception
                throw new RuntimeException("Business failure + Transaction C failure: "
                        + e.getMessage() + " / " + failEx.getMessage(), e);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> IdempotentHttpResult<T> buildReplayResult(IdempotencyRecord rec) {
        // Stage 05A.2.4 §8 — Build replay from stored canonical result.
        // The stored response_body is the canonical JSON of the original response.
        // The controller should use responseBody directly for replay responses.
        return new IdempotentHttpResult<>(
                rec.getResponseStatus() != null ? rec.getResponseStatus() : 200,
                Map.of(),
                rec.getResponseBody(),
                null, // resourceId not stored separately — controller uses responseBody
                true, // replayed
                0,    // leaseVersion not relevant for replay
                null  // businessResult null on replay — controller uses responseBody
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
