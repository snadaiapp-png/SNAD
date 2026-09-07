package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical typed mutation request for
 * PATCH /api/v2/hr/people/{personId}/private.
 *
 * <p>Full private-profile replacement guarded by {@code expectedVersion}
 * (never server-defaulted). {@code nationalityCountryCode} is ISO-3166
 * alpha-2; {@code maritalStatus} must be one of the canonical statuses.
 * Final authority for both is the database constraint layer.
 */
public record PatchPersonPrivateRequest(
        LocalDate dateOfBirth,
        @Size(min = 2, max = 2) @Pattern(regexp = "[A-Z]{2}") String nationalityCountryCode,
        @Pattern(regexp = "SINGLE|MARRIED|DIVORCED|WIDOWED|SEPARATED") String maritalStatus,
        @NotNull Long expectedVersion
) {
}
