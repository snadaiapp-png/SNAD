package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** HRM-G0 / WS5 Task 5 — canonical typed request for POST /contracts/{id}/amend. */
public record AmendContractRequest(
        String contractTermType,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        @NotNull LocalDate effectiveDate,
        String documentReference,
        String reasonCode
) {
}
