package com.sanad.platform.idempotency.service;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stage 05A.2.3 §9 — Separate bean for Transaction B (business execution).
 *
 * <p>This bean is called by {@link IdempotentCommandExecutor} to execute the
 * business mutation, write the SUCCESS audit event, and mark the idempotency
 * record as COMPLETED — all within a single transaction.</p>
 *
 * <p>Being a separate bean ensures that Spring's transaction proxy intercepts
 * the call (no self-invocation issue).</p>
 */
@Component
public class IdempotentBusinessTransactionExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentBusinessTransactionExecutor.class);

    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final IdempotencyReplaySerializer serializer;

    public IdempotentBusinessTransactionExecutor(AuditService auditService,
                                                    IdempotencyService idempotencyService,
                                                    IdempotencyReplaySerializer serializer) {
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.serializer = serializer;
    }

    /**
     * Transaction B — executes business mutation + audit + completion atomically.
     *
     * <p>Uses {@code Propagation.REQUIRED} to join the caller's transaction
     * or start a new one. All three operations (business, audit, completion)
     * commit together or roll back together.</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public <T> IdempotentHttpResult<T> executeBusinessTransaction(
            IdempotencyRecord rec,
            String leaseOwner,
            long leaseVersion,
            String operation,
            String resourceType,
            Supplier<T> businessAction) {

        // Execute the business mutation
        T result = businessAction.get();

        // Serialize the approved response
        String responseBody = serializer.serializeResponse(result);
        int httpStatus = 201; // Default for create operations
        Map<String, String> headers = Map.of();

        // Write SUCCESS audit event (in the same transaction)
        auditService.record(AuditContext.builder(
                        operation, resourceType, "CREATE")
                .resourceId(extractResourceId(result) != null ? extractResourceId(result).toString() : null)
                .outcome(AuditOutcome.SUCCESS)
                .httpStatus(httpStatus)
                .afterState(responseBody)
                .build());

        // Complete the idempotency record (in the same transaction)
        // Stage 05A.2.3 §10 — Complete uses MANDATORY propagation (must be
        // called within an existing transaction). The store's atomicComplete
        // runs the UPDATE directly within this transaction.
        idempotencyService.completeInTransaction(
                rec.getId(), leaseOwner, leaseVersion,
                httpStatus, serializer.serializeHeaders(headers), responseBody);

        return new IdempotentHttpResult<>(
                httpStatus, headers, responseBody,
                extractResourceId(result), false, leaseVersion, result);
    }

    @SuppressWarnings("unchecked")
    private <T> UUID extractResourceId(T result) {
        if (result == null) return null;
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            if (id instanceof UUID uuid) return uuid;
            if (id instanceof String s) return UUID.fromString(s);
        } catch (Exception e) {
            // No getId() method — return null
        }
        return null;
    }
}
