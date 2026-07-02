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
import java.util.function.Supplier;

/**
 * Stage 05A.2.6 §6 — Separate bean for Transaction B.
 *
 * <p>Executes business mutation + audit + completion atomically in one
 * transaction. Uses MANDATORY propagation for completion (must join
 * the existing transaction).</p>
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

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> IdempotentHttpResult<T> executeBusinessTransaction(
            LeaseGrant grant,
            String operation,
            String resourceType,
            Supplier<T> businessAction) {

        // Execute the business mutation
        T result = businessAction.get();

        // Serialize the approved response
        String responseBody = serializer.serializeResponse(result);
        int httpStatus = 201;
        Map<String, String> headers = Map.of();

        // Write SUCCESS audit event (same transaction)
        java.util.UUID resourceId = extractResourceId(result);
        auditService.record(AuditContext.builder(
                        operation, resourceType, "CREATE")
                .resourceId(resourceId != null ? resourceId.toString() : null)
                .outcome(AuditOutcome.SUCCESS)
                .httpStatus(httpStatus)
                .afterState(responseBody)
                .build());

        // Complete the idempotency record (same transaction, MANDATORY)
        idempotencyService.completeInTransaction(
                grant.recordId(), grant.tenantId(),
                grant.leaseOwnerRequestId(), grant.leaseVersion(),
                httpStatus, serializer.serializeHeaders(headers), responseBody);

        return new IdempotentHttpResult<>(
                httpStatus, headers, responseBody,
                resourceId, false, grant.leaseVersion(), result);
    }

    @SuppressWarnings("unchecked")
    private <T> java.util.UUID extractResourceId(T result) {
        if (result == null) return null;
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            if (id instanceof java.util.UUID uuid) return uuid;
            if (id instanceof String s) return java.util.UUID.fromString(s);
        } catch (Exception e) {
            // No getId() method
        }
        return null;
    }
}
