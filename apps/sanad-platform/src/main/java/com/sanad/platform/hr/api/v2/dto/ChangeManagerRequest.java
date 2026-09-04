package com.sanad.platform.hr.api.v2.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical typed request for
 * POST /api/v2/hr/assignments/{assignmentId}/change-manager.
 *
 * <p>The new manager link takes effect from {@code effectiveDate}; the
 * current open period is superseded atomically and historical placement is
 * preserved. {@code expectedVersion} drives optimistic concurrency.
 */
public record ChangeManagerRequest(
        @NotNull UUID reportsToAssignmentId,
        @NotNull LocalDate effectiveDate,
        @NotNull Long expectedVersion
) {
}
