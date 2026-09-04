package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotBlank;

/** HRM-G0 / WS5 Task 5 — approve/reject/revoke decision (four-eyes enforced server-side). */
public record OverrideDecisionRequest(
        @NotBlank String comment
) {
}
