package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * HRM-G0 / WS5 Task 3 — canonical lifecycle command request.
 *
 * <p>{@code effectiveDate} is REQUIRED and is never defaulted to the server
 * clock; {@code expectedVersion} drives optimistic concurrency (a stale value
 * yields HTTP 409 HRM_CONCURRENCY_CONFLICT). {@code reasonCode} is optional
 * provenance for the resulting status period.
 */
public record LifecycleCommandRequest(
        @NotNull LocalDate effectiveDate,
        @NotNull @Min(0) Long expectedVersion,
        @Size(max = 80) String reasonCode
) {
}
