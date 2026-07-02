package com.sanad.platform.idempotency.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 05A.2.4 §3 — Separate bean for Transaction A (reservation).
 *
 * <p>Uses REQUIRES_NEW so the PROCESSING reservation commits independently
 * before Transaction B begins. This ensures concurrent requests see the
 * PROCESSING state immediately.</p>
 *
 * <p>Being a separate bean ensures the REQUIRES_NEW propagation is actually
 * applied (no self-invocation issue).</p>
 */
@Component
public class IdempotencyReservationTransactionExecutor {

    private final IdempotencyService idempotencyService;

    public IdempotencyReservationTransactionExecutor(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    /**
     * Transaction A — reserves the idempotency key.
     * Commits independently before Transaction B.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyService.ReservationResult reserve(
            String idempotencyKey,
            String operation,
            String route,
            String resourceType,
            String method,
            String body,
            String queryString) {
        return idempotencyService.reserveOrReplay(
                idempotencyKey, operation, route, resourceType, method, body, queryString);
    }
}
