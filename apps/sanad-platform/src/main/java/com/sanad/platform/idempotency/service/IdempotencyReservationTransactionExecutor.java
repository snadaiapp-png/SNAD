package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 05A.2.6 §3 — Separate bean for Transaction A (reservation).
 * REQUIRES_NEW — commits independently before Transaction B begins.
 */
@Component
public class IdempotencyReservationTransactionExecutor {

    private final IdempotencyService idempotencyService;

    public IdempotencyReservationTransactionExecutor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyService.ReservationResult reserve(
            String idempotencyKey,
            String operation,
            String route,
            String resourceType,
            String method,
            String body,
            String queryString,
            String verifiedRequestId) {
        return idempotencyService.reserveOrReplay(
                idempotencyKey, operation, route, resourceType,
                method, body, queryString, verifiedRequestId);
    }
}
