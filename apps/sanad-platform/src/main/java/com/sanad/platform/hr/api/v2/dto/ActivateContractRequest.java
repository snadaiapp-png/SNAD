package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** HRM-G0 / WS5 Task 5 — activate a specific contract version effective-dated. */
public record ActivateContractRequest(
        @NotNull Integer versionNumber,
        @NotNull LocalDate effectiveDate
) {
}
