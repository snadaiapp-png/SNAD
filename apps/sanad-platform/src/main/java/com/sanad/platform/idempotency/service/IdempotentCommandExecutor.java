package com.sanad.platform.idempotency.service;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
import com.sanad.platform.idempotency.domain.IdempotencyRecord;
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
 * Stage 05A.2.1 §9-10 — Transactional idempotent command executor.
 *
 * <p>Executes a business command within a proper transaction boundary:</p>
 * <ol>
 *   <li><b>Transaction A</b> (REQUIRES_NEW): Reserve the idempotency key.
 *       Commits independently so concurrent requests see the PROCESSING state.</li>
 *   <li><b>Transaction B</b> (REQUIRED): Lock the reservation, validate lease
 *       ownership, execute the business mutation, write the SUCCESS audit event,
 *       serialize the approved response, set status = COMPLETED. All commit
 *       together. If any step fails, everything rolls back.</li>
 *   <li><b>Transaction C</b> (REQUIRES_NEW, only on failure): Mark the
 *       reservation as FAILED_RETRYABLE with lease owner/version matching.</li>
 * </ol>
 */
@Component
public class IdempotentCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentCommandExecutor.class);

    private final IdempotencyService idempotencyService;
    private final IdempotencyReplaySerializer serializer;
    private final AuditService auditService;

    public IdempotentCommandExecutor(IdempotencyService idempotencyService,
                                       IdempotencyReplaySerializer serializer,
                                       AuditService auditService) {
        this.idempotencyService = idempotencyService;
        this.serializer = serializer;
        this.auditService = auditService;
    }

    /**
     * Executes an idempotent command with the full transaction boundary.
     *
     * @param operationMetadata the operation name, route, resource type
     * @param idempotencyKey the client-supplied idempotency key
     * @param request the request body (for fingerprinting)
     * @param method the HTTP method
     * @param queryString the canonical query string
     * @param businessAction the supplier that executes the business mutation
     * @param <T> the response DTO type
     * @return the idempotent HTTP result containing status, body, headers
     */
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

        // Transaction B: Business execution + audit + completion (all commit together)
        IdempotencyRecord rec = reservation.record();
        String leaseOwner = MDC.get(RequestIdFilter.MDC_KEY);
        long leaseVersion = reservation.leaseVersion();

        try {
            return executeBusinessTransaction(rec, leaseOwner, leaseVersion,
                    operationMetadata, businessAction);
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

    /**
     * Transaction B — executes the business mutation, audit, and completion
     * in a single transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    protected <T> IdempotentHttpResult<T> executeBusinessTransaction(
            IdempotencyRecord rec,
            String leaseOwner,
            long leaseVersion,
            OperationMetadata operationMetadata,
            Supplier<T> businessAction) {

        // Execute the business mutation
        T result = businessAction.get();

        // Serialize the approved response
        String responseBody = serializer.serializeResponse(result);
        int httpStatus = 201; // Default for create operations
        Map<String, String> headers = Map.of(); // Caller can extend

        // Write SUCCESS audit event (in the same transaction)
        auditService.record(AuditContext.builder(
                        operationMetadata.operation(),
                        operationMetadata.resourceType(),
                        "CREATE")
                .resourceId(extractResourceId(result) != null ? extractResourceId(result).toString() : null)
                .outcome(AuditOutcome.SUCCESS)
                .httpStatus(httpStatus)
                .afterState(responseBody)
                .build());

        // Complete the idempotency record (in the same transaction)
        idempotencyService.complete(rec.getId(), leaseOwner, leaseVersion,
                httpStatus, serializer.serializeHeaders(headers), responseBody);

        return new IdempotentHttpResult<>(
                httpStatus, headers, responseBody,
                extractResourceId(result), false, leaseVersion, result);
    }

    @SuppressWarnings("unchecked")
    private <T> UUID extractResourceId(T result) {
        if (result == null) return null;
        try {
            // Try to call getId() via reflection
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            if (id instanceof UUID uuid) return uuid;
            if (id instanceof String s) return UUID.fromString(s);
        } catch (Exception e) {
            // No getId() method — return null
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> IdempotentHttpResult<T> buildReplayResult(IdempotencyRecord rec) {
        return new IdempotentHttpResult<>(
                rec.getResponseStatus() != null ? rec.getResponseStatus() : 200,
                Map.of(),
                rec.getResponseBody(),
                null, // resourceId not stored separately in this version
                true, // replayed
                0, // leaseVersion not relevant for replay
                null // businessResult not available on replay
        );
    }

    /**
     * Metadata for an idempotent operation.
     */
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
