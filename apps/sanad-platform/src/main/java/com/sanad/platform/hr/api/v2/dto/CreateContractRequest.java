package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 5 — canonical typed request for POST /api/v2/hr/contracts. */
public record CreateContractRequest(
        @NotNull UUID employmentId,
        @NotBlank String contractNumber,
        boolean isPrimary,
        String contractTermType,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        @NotNull LocalDate effectiveDate,
        String documentReference
) {
}
