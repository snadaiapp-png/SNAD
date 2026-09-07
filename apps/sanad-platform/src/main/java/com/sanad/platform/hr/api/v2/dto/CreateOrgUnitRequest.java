package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical typed request for POST /api/v2/hr/org-units.
 * {@code stableCode} is an optional technical key; when absent a server-side
 * technical key is generated (never a legally significant value).
 */
public record CreateOrgUnitRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 50) String unitType,
        UUID parentOrgUnitId,
        @NotNull LocalDate effectiveFrom
) {
}
