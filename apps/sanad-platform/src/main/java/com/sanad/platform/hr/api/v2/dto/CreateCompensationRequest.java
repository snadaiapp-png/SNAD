package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HRM-G0 / WS5 Task 5 — canonical typed request for POST /compensation-packages. */
public record CreateCompensationRequest(
        @NotNull UUID employmentId,
        @NotBlank String currencyCode,
        String payFrequency,
        @NotNull LocalDate effectiveFrom,
        List<ComponentInput> components
) {

    public record ComponentInput(String componentCode, String componentType, boolean recurring,
                                 java.math.BigDecimal amount, java.math.BigDecimal percentage) {
    }
}
