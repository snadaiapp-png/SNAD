package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical typed mutation request for
 * PATCH /api/v2/hr/people/{personId}.
 *
 * <p>Full name-tuple replacement guarded by {@code expectedVersion};
 * the concurrency version is never defaulted by the server. Display name
 * is server-derived from the name tuple, never client-supplied.
 */
public record PatchPersonRequest(
        @NotBlank @Size(max = 200) String firstName,
        @Size(max = 200) String middleName,
        @NotBlank @Size(max = 200) String lastName,
        @NotNull Long expectedVersion
) {
}
