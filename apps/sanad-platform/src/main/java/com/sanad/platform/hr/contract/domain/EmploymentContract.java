package com.sanad.platform.hr.contract.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Employment contract identity aggregate root (WS6 Task 2).
 * A materially new legal instrument creates a NEW contract; an amendment
 * creates a new VERSION of the same contract.
 */
public record EmploymentContract(
        UUID id,
        UUID tenantId,
        UUID employmentId,
        String contractNumber,
        boolean isPrimary,
        UUID predecessorContractId,
        Instant createdAt) {

    public EmploymentContract {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(contractNumber, "contractNumber");
    }
}
