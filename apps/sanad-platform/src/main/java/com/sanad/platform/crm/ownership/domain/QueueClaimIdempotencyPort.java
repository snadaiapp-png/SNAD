package com.sanad.platform.crm.ownership.domain;

import java.util.Optional;
import java.util.UUID;

/** Durable idempotency boundary for successful queue claims. */
public interface QueueClaimIdempotencyPort {

    /**
     * Reserve an idempotency key in the current transaction.
     * Returns an existing record when the key has already been used.
     */
    Optional<ClaimRecord> reserve(UUID tenantId,
                                  UUID principalId,
                                  UUID idempotencyKey,
                                  String requestFingerprintSha256);

    void complete(UUID tenantId,
                  UUID principalId,
                  UUID idempotencyKey,
                  String requestFingerprintSha256,
                  UUID assignmentId);

    record ClaimRecord(
            String requestFingerprintSha256,
            int responseStatus,
            UUID assignmentId
    ) {
        public boolean isComplete() {
            return responseStatus >= 200 && responseStatus < 300 && assignmentId != null;
        }
    }
}
