package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — effective-dated Org Unit revision. Creates a new
 * version row; the open version closes the day before {@code effectiveDate}.
 * Period-aware cycle validation runs before any mutation.
 */
public record ReviseOrgUnitRequest(
        UUID parentOrgUnitId,
        @Size(max = 200) String name,
        @Size(max = 50) String code,
        @Size(max = 50) String unitType,
        @NotNull LocalDate effectiveDate
) {
}
