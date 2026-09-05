package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** HRM-G0 / WS5 Task 5 — revise a compensation package (new effective version). */
public record ReviseCompensationRequest(
        String currencyCode,
        String payFrequency,
        @NotNull LocalDate effectiveFrom,
        List<CreateCompensationRequest.ComponentInput> components,
        String reasonCode
) {
}
