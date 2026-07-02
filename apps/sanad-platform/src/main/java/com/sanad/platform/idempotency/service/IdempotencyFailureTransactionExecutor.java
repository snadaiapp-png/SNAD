package com.sanad.platform.idempotency.service;

import com.sanad.platform.idempotency.domain.IdempotencyRecord;
import com.sanad.platform.idempotency.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stage 05A.2.4 §7 — Separate bean for Transaction C (failure recovery).
 *
 * <p>Called after Transaction B rolls back. Marks the idempotency record
 * as FAILED_RETRYABLE with lease owner/version fencing.</p>
 *
 * <p>Being a separate bean with REQUIRES_NEW ensures this transaction
 * commits independently even after Transaction B's rollback.</p>
 */
@Component
public class IdempotencyFailureTransactionExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFailureTransactionExecutor.class);

    private final IdempotencyService idempotencyService;
    private final IdempotencyRecordRepository repository;

    public IdempotencyFailureTransactionExecutor(IdempotencyService idempotencyService,
                                                    IdempotencyRecordRepository repository) {
        this.idempotencyService = idempotencyService;
        this.repository = repository;
    }

    /**
     * Transaction C — marks the reservation as FAILED_RETRYABLE.
     *
     * <p>Uses REQUIRES_NEW so it commits independently of the rolled-back
     * Transaction B. The lease owner and version must match — if a takeover
     * has occurred, this will throw StaleIdempotencyLeaseException.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failReservation(UUID recordId, String leaseOwnerRequestId, long leaseVersion,
                                  String errorCode, String errorDetail) {
        try {
            idempotencyService.fail(recordId, leaseOwnerRequestId, leaseVersion,
                    errorCode, errorDetail, true);
        } catch (StaleIdempotencyLeaseException e) {
            // Another worker has already taken over — log and re-throw.
            log.warn("Transaction C: stale lease for record {} — another worker took over: {}",
                    recordId, e.getMessage());
            throw e;
        } catch (Exception e) {
            // Transaction C itself failed — do NOT swallow.
            log.error("Transaction C: failed to mark record {} as FAILED_RETRYABLE: {}",
                    recordId, e.getMessage(), e);
            throw new RuntimeException("Transaction C failure: " + e.getMessage(), e);
        }
    }
}
