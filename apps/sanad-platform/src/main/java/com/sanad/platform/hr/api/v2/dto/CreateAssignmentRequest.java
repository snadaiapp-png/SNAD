package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 4 — canonical typed request for POST /api/v2/hr/assignments.
 *
 * <p>{@code effectiveFrom} is REQUIRED (never server-defaulted). Position
 * occupancy, allocation, overlap and reporting validation are delegated to
 * the WS2 atomic repository chain — this DTO carries no business logic.
 */
public record CreateAssignmentRequest(
        @NotNull UUID employmentId,
        @NotNull UUID organizationId,
        UUID orgUnitId,
        UUID positionId,
        UUID reportsToAssignmentId,
        @NotNull AssignmentType assignmentType,
        @NotNull OccupancyMode occupancyMode,
        BigDecimal allocationPercent,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
