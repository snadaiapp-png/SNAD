package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 5 — controlled-exception request. Justification is
 * mandatory; hard statutory blocks have no override path at all.
 */
public record CreateOverrideRequest(
        @NotNull UUID complianceRuleId,
        @NotNull String resourceType,
        @NotNull UUID resourceId,
        @NotBlank String justification,
        String evidenceReference,
        LocalDate validFrom,
        LocalDate validUntil
) {
}
