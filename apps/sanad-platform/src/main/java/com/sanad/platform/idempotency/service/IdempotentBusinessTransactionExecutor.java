package com.sanad.platform.idempotency.service;

import com.sanad.platform.audit.domain.AuditOutcome;
import com.sanad.platform.audit.service.AuditContext;
import com.sanad.platform.audit.service.AuditService;
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
 * <p>Executes business mutation + audit + idempotency completion atomically.
 * The stored response uses an explicit safe-header allowlist; sensitive
 * request/response headers are never copied into the replay record.</p>
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

        T result = businessAction.get();

        String responseBody = serializer.serializeResponse(result);
        int httpStatus = 201;

        // Explicit allowlist. Do not capture servlet headers because they may
        // contain Set-Cookie, Authorization, proxy credentials, or tracing
        // material that must never be persisted for replay.
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json"
        );

        java.util.UUID resourceId = extractResourceId(result);
        auditService.record(AuditContext.builder(
                        operation, resourceType, "CREATE")
                .resourceId(resourceId != null ? resourceId.toString() : null)
                .outcome(AuditOutcome.SUCCESS)
                .httpStatus(httpStatus)
                .afterState(responseBody)
                .build());

        idempotencyService.completeInTransaction(
                grant.recordId(), grant.tenantId(),
                grant.leaseOwnerRequestId(), grant.leaseVersion(),
                httpStatus, serializer.serializeHeaders(headers), responseBody);

        log.debug("Idempotent command completed recordId={} leaseVersion={} status={}",
                grant.recordId(), grant.leaseVersion(), httpStatus);
        return new IdempotentHttpResult<>(
                httpStatus, headers, responseBody,
                resourceId, false, grant.leaseVersion(), result);
    }

    private <T> java.util.UUID extractResourceId(T result) {
        if (result == null) return null;
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            if (id instanceof java.util.UUID uuid) return uuid;
            if (id instanceof String s) return java.util.UUID.fromString(s);
        } catch (Exception e) {
            log.debug("Response type {} does not expose a UUID getId()", result.getClass().getName());
        }
        return null;
    }
}
