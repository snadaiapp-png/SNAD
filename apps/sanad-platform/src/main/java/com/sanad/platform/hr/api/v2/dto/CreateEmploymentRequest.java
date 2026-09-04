package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 2/3 — canonical typed creation request for the
 * POST /api/v2/hr/employments operation.
 *
 * <p>Typed DTO only — never a {@code Map<String,Object>} request body.
 * Labor jurisdiction is an ISO-3166 alpha-2 code; ambiguity about legal
 * entity or jurisdiction is expressed as {@code HRM_MIGRATION_REQUIRED}
 * by the application service, never guessed here.
 */
public record CreateEmploymentRequest(
        @NotNull UUID personId,
        @NotNull UUID legalEntityId,
        @NotBlank @Size(max = 80) String employeeNumber,
        @NotNull LocalDate employmentStartDate,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String laborJurisdictionCode,
        @NotBlank @Size(max = 60) String workerClassificationCode
) {
}
