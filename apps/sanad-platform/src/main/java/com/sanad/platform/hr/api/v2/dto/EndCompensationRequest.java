package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** HRM-G0 / WS5 Task 5 — end a compensation package effective-dated. */
public record EndCompensationRequest(
        @NotNull LocalDate effectiveTo
) {
}
