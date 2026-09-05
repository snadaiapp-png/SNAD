package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** HRM-G0 / WS5 Task 4 — effective-dated Job revision (new version row). */
public record ReviseJobRequest(
        @Size(max = 200) String title,
        @Size(max = 20) String grade,
        @NotNull LocalDate effectiveDate
) {
}
