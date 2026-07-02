package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stage 05A.2.6 §7 — Separate bean for Transaction C (failure recovery).
 * REQUIRES_NEW — commits independently after Transaction B rolls back.
 */
@Component
public class IdempotencyFailureTransactionExecutor {

    private final IdempotencyService idempotencyService;

    public IdempotencyFailureTransactionExecutor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failReservation(UUID recordId, UUID tenantId,
                                  String leaseOwnerRequestId, long leaseVersion,
                                  String errorCode, String errorDetail) {
        try {
            idempotencyService.fail(recordId, tenantId, leaseOwnerRequestId, leaseVersion,
                    errorCode, errorDetail, true);
        } catch (StaleIdempotencyLeaseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Transaction C failure: " + e.getMessage(), e);
        }
    }
}
