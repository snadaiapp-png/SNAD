package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical typed request for
 * POST /api/v2/hr/assignments/{assignmentId}/transfer.
 *
 * <p>Transfer closes/supersedes the current effective assignment period and
 * creates the new period atomically; it never overwrites historical
 * placement. {@code positionId} and {@code reportsToAssignmentId} are
 * optional carried-over fields; {@code orgUnitId} is the placement change.
 */
public record TransferRequest(
        @NotNull UUID orgUnitId,
        UUID positionId,
        UUID reportsToAssignmentId,
        @NotNull LocalDate effectiveDate,
        @NotNull Long expectedVersion
) {
}
