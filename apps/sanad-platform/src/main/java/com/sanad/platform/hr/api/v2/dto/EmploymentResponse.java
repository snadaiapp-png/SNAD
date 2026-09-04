package com.sanad.platform.hr.api.v2.dto;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 — canonical Employment projection returned by the v2
 * surface. Contains no PII beyond the canonical employment identity fields
 * and never exposes encrypted identifier material, blind indexes or
 * compensation amounts.
 */
public record EmploymentResponse(
        UUID employmentId,
        UUID personId,
        UUID legalEntityId,
        String employeeNumber,
        String workerClassificationCode,
        String currentStatus,
        java.time.LocalDate employmentStartDate,
        java.time.LocalDate terminationDate,
        UUID rehireOfEmployeeId,
        long version
) {
}
