package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical typed creation request for
 * POST /api/v2/hr/people/{personId}/identifiers.
 *
 * <p>{@code value} is the plaintext identity document value. It is
 * write-only: normalized, blind-indexed and encrypted server-side, never
 * persisted in plaintext, never logged, and never echoed in any response.
 */
public record AddIdentifierRequest(
        @NotBlank @Size(max = 50) String identifierType,
        @Size(min = 2, max = 2) @Pattern(regexp = "[A-Z]{2}") String issuingCountryCode,
        @NotBlank String value
) {
}
