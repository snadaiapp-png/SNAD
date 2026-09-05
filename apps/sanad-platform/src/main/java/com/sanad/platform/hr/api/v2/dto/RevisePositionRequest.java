package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 4 — effective-dated Position revision (new version row). */
public record RevisePositionRequest(
        @Size(max = 200) String title,
        UUID jobId,
        UUID orgUnitId,
        @NotNull LocalDate effectiveDate
) {
}
