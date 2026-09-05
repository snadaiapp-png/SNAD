package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** HRM-G0 / WS5 Task 5 — terminate the active contract version effective-dated. */
public record TerminateContractRequest(
        @NotNull LocalDate effectiveDate,
        String reasonCode
) {
}
