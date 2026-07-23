package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** A single approval step in a transfer request workflow. */
public record TransferStep(
        UUID id,
        UUID tenantId,
        UUID transferRequestId,
        int stepNumber,
        UUID approverUserId,
        TransferStepDecision decision,
        Instant decidedAt,
        String comment,
        Instant createdAt
) {
    public TransferStep {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (transferRequestId == null) throw new IllegalArgumentException("transferRequestId required");
        if (stepNumber < 1) throw new IllegalArgumentException("stepNumber must be >= 1");
        if (approverUserId == null) throw new IllegalArgumentException("approverUserId required");
    }

    public boolean isPending() { return decision == null; }
}
