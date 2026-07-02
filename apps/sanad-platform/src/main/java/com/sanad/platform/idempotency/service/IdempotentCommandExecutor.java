package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stage 05A.2.6 — Transactional idempotent command executor.
 *
 * <p>Uses VerifiedRequestIdentityProvider to resolve identity BEFORE
 * any transaction. Passes the identity explicitly through A/B/C.</p>
 */
@Component
public class IdempotentCommandExecutor {

    private final IdempotencyReservationTransactionExecutor reservationExecutor;
    private final IdempotentBusinessTransactionExecutor businessTxExecutor;
    private final IdempotencyFailureTransactionExecutor failureExecutor;
    private final VerifiedRequestIdentityProvider identityProvider;

    public IdempotentCommandExecutor(IdempotencyReservationTransactionExecutor reservationExecutor,
                                       IdempotentBusinessTransactionExecutor businessTxExecutor,
                                       IdempotencyFailureTransactionExecutor failureExecutor,
                                       VerifiedRequestIdentityProvider identityProvider) {
        this.reservationExecutor = reservationExecutor;
        this.businessTxExecutor = businessTxExecutor;
        this.failureExecutor = failureExecutor;
        this.identityProvider = identityProvider;
    }

    public <T> IdempotentHttpResult<T> execute(
            OperationMetadata operationMetadata,
            String idempotencyKey,
            String request,
            String method,
            String queryString,
            Supplier<T> businessAction) {

        // Stage 05A.2.6 §4 — Resolve verified identity BEFORE Transaction A
        String verifiedRequestId = identityProvider.requireCurrent();

        // Transaction A: Reserve (REQUIRES_NEW, commits independently)
        IdempotencyService.ReservationResult reservation = reservationExecutor.reserve(
                idempotencyKey,
                operationMetadata.operation(),
                operationMetadata.route(),
                operationMetadata.resourceType(),
                method,
                request,
                queryString,
                verifiedRequestId);

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

        LeaseGrant grant = reservation.leaseGrant();
        if (grant == null) {
            throw new IllegalStateException("NEW reservation but leaseGrant is null");
        }

        // Transaction B: Business + audit + completion (REQUIRED, atomic)
        try {
            return businessTxExecutor.executeBusinessTransaction(
                    grant,
                    operationMetadata.operation(),
                    operationMetadata.resourceType(),
                    businessAction);
        } catch (Exception e) {
            // Transaction C: Mark FAILED_RETRYABLE (REQUIRES_NEW)
            try {
                failureExecutor.failReservation(
                        grant.recordId(), grant.tenantId(),
                        grant.leaseOwnerRequestId(), grant.leaseVersion(),
                        "SANAD-IDEMP-EXEC",
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Exception failEx) {
                throw new RuntimeException("Business failure + Transaction C failure: "
                        + e.getMessage() + " / " + failEx.getMessage(), e);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> IdempotentHttpResult<T> buildReplayResult(IdempotencyRecord rec) {
        return new IdempotentHttpResult<>(
                rec.getResponseStatus() != null ? rec.getResponseStatus() : 200,
                Map.of(),
                rec.getResponseBody(),
                null,
                true,
                0,
                null
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
