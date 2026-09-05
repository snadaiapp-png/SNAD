package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical typed creation request for
 * POST /api/v2/hr/people.
 *
 * <p>Typed DTO only — never a {@code Map<String,Object>} request body.
 * Names are directory data; private PII (date of birth, nationality,
 * marital status, identity documents) is carried exclusively by the
 * capability-gated {@code /private} and {@code /identifiers} operations.
 */
public record CreatePersonRequest(
        @NotBlank @Size(max = 200) String firstName,
        @Size(max = 200) String middleName,
        @NotBlank @Size(max = 200) String lastName
) {
}
