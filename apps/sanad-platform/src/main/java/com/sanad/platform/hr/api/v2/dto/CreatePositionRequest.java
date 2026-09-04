package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 4 — canonical typed request for POST /api/v2/hr/positions. */
public record CreatePositionRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50) String code,
        UUID jobId,
        UUID orgUnitId,
        @NotNull LocalDate effectiveFrom
) {
}
