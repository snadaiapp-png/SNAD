package com.sanad.platform.test.support;

import com.sanad.platform.idempotency.service.IdempotencyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stage 05A.2.8 §4.2 — Test-only harness for calling
 * {@link IdempotencyService#completeInTransaction} which has
 * {@code @Transactional(MANDATORY)} propagation.
 *
 * <p>This harness wraps the call in a {@code REQUIRES_NEW} transaction
 * so tests can invoke completion without being inside Transaction B.</p>
 */
@Component
public class IdempotencyCompletionTestTransactionExecutor {

    private final IdempotencyService idempotencyService;

    public IdempotencyCompletionTestTransactionExecutor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID recordId, UUID tenantId,
                          String leaseOwnerRequestId, long leaseVersion,
                          int responseStatus, String responseHeaders, String responseBody) {
        idempotencyService.completeInTransaction(recordId, tenantId,
                leaseOwnerRequestId, leaseVersion,
                responseStatus, responseHeaders, responseBody);
    }
}
